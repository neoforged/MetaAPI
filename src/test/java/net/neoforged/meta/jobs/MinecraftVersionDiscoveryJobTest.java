package net.neoforged.meta.jobs;

import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import net.neoforged.meta.db.MinecraftVersionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MinecraftMetadataPollingJob.
 * <p>
 * This test uses:
 * - A real embedded HTTP server (JDK's HttpServer) to serve fake launcher manifests
 * - A real Spring context with minimal configuration (no web server, no scheduled jobs)
 * - A temporary SQLite database for isolation
 * - The job bean retrieved from Spring context with DAO verification
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers = MinecraftVersionDiscoveryJobTest.Initializer.class)
@ActiveProfiles("test")
class MinecraftVersionDiscoveryJobTest {
    @TempDir
    static Path tempDir;

    @AutoClose
    static FakeLauncherManifestServer launcherServer;

    @Autowired
    MinecraftVersionDao minecraftVersionDao;

    @Autowired
    BrokenSoftwareComponentVersionDao brokenVersionDao;

    @Autowired
    MinecraftVersionDiscoveryJob pollingJob;

    @BeforeAll
    static void setUpAll() {
        // Create and start fake launcher manifest server
        launcherServer = new FakeLauncherManifestServer();
    }

    @BeforeEach
    void setUp() {
        // Clear the fake server before each test
        launcherServer.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up database after each test. Broken-version entries must go as well,
        // otherwise a version reported broken by one test (e.g. the checksum-mismatch
        // test) is skipped by later tests that reuse the same version id.
        minecraftVersionDao.deleteAll();
        brokenVersionDao.deleteAll();
    }

    @Test
    void testDiscoverMinecraftVersions() {
        // Set up fake launcher manifest with multiple versions
        launcherServer.addVersion("1.21", "release")
                .withReleaseTime("2024-06-13T12:00:00+00:00")
                .withManifest(21);

        launcherServer.addVersion("1.20.1", "release")
                .withReleaseTime("2023-06-12T10:00:00+00:00")
                .withManifest(17);

        launcherServer.addVersion("24w50a", "snapshot")
                .withReleaseTime("2024-12-11T15:30:00+00:00")
                .withManifest(21);

        // Run the polling job
        pollingJob.run();

        // Verify results using DAOs
        var versions = minecraftVersionDao.findAll();
        assertEquals(3, versions.size());

        var v1_21 = minecraftVersionDao.getByVersion("1.21");
        assertNotNull(v1_21);
        assertEquals("release", v1_21.getType());
        assertEquals(21, v1_21.getJavaVersion());
        assertNotNull(v1_21.getManifest());
        assertNotNull(v1_21.getManifest().getSha1());

        var v1_20_1 = minecraftVersionDao.getByVersion("1.20.1");
        assertNotNull(v1_20_1);
        assertEquals("release", v1_20_1.getType());
        assertEquals(17, v1_20_1.getJavaVersion());

        var v24w50a = minecraftVersionDao.getByVersion("24w50a");
        assertNotNull(v24w50a);
        assertEquals("snapshot", v24w50a.getType());
        assertEquals(21, v24w50a.getJavaVersion());
    }

    @Test
    void testUpdatesExistingVersions() {
        // Set up initial versions
        launcherServer.addVersion("1.21", "release")
                .withReleaseTime("2024-06-13T12:00:00+00:00")
                .withManifest(21);

        // Run job first time
        pollingJob.run();

        // Verify initial version was added
        var v1 = minecraftVersionDao.getByVersion("1.21");
        assertNotNull(v1);
        assertNotNull(v1.getManifest());
        String initialSha1 = v1.getManifest().getSha1();
        assertNotNull(initialSha1);

        // Update the manifest with new content
        launcherServer.clear();
        launcherServer.addVersion("1.21", "release")
                .withReleaseTime("2024-06-13T12:00:00+00:00")
                .withManifest(22);

        // Run job again
        pollingJob.run();

        // Verify manifest was updated
        var v2 = minecraftVersionDao.getByVersion("1.21");
        assertNotNull(v2);
        assertNotNull(v2.getManifest());
        String updatedSha1 = v2.getManifest().getSha1();
        assertNotEquals(initialSha1, updatedSha1, "SHA1 should change when manifest content changes");
        assertEquals(22, v2.getJavaVersion());
    }

    @Test
    void testHandlesNewVersionsAdded() {
        // Start with one version
        launcherServer.addVersion("1.20.1", "release")
                .withReleaseTime("2023-06-12T10:00:00+00:00")
                .withManifest(17);

        pollingJob.run();

        // Verify one version exists
        assertEquals(1, minecraftVersionDao.findAll().size());

        // Add a new version to the launcher manifest
        launcherServer.addVersion("1.21", "release")
                .withReleaseTime("2024-06-13T12:00:00+00:00")
                .withManifest(21);

        pollingJob.run();

        // Verify both versions exist
        assertEquals(2, minecraftVersionDao.findAll().size());
        assertNotNull(minecraftVersionDao.getByVersion("1.20.1"));
        assertNotNull(minecraftVersionDao.getByVersion("1.21"));
    }

    @Test
    void testSha1ChecksumMismatch() {
        // Set up a version with an incorrect SHA-1 that won't match the actual content
        launcherServer.addVersion("1.21", "release")
                .withSha1("0000000000000000000000000000000000000000") // Intentionally wrong SHA1
                .withReleaseTime("2024-06-13T12:00:00+00:00")
                .withManifest(21);

        // The polling job should fail due to checksum mismatch
        // Since the job catches exceptions and logs them, we need to verify it doesn't crash
        // but also doesn't save the version with the wrong checksum
        pollingJob.run();

        // Verify the version was NOT saved due to checksum mismatch
        var version = minecraftVersionDao.getByVersion("1.21");
        assertNull(version, "Version should not be saved when checksum mismatches");
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // Set both the temp directory and the launcher manifest URL
            TestPropertyValues.of(
                    "meta-api.data-directory=" + tempDir.toAbsolutePath(),
                    "meta-api.minecraft-launcher-meta-url=" + launcherServer.getManifestUrl()
            ).applyTo(context);
        }
    }
}
