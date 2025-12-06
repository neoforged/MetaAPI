package net.neoforged.meta.jobs;

import net.neoforged.meta.db.SoftwareComponentVersion;
import net.neoforged.meta.db.MavenComponentVersionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for MavenVersionDiscoveryJob.
 * <p>
 * This test uses:
 * - A real embedded HTTP server (JDK's HttpServer) to serve fake Maven API responses
 * - A real Spring context with minimal configuration (no web server, no scheduled jobs)
 * - A temporary SQLite database for isolation
 * - Manual job execution with DAO verification
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers = MavenVersionDiscoveryJobTest.Initializer.class)
@ActiveProfiles("test")
class MavenVersionDiscoveryJobTest {
    @TempDir
    static Path tempDir;

    @AutoClose
    static FakeMavenRepository mavenRepo;

    @BeforeAll
    static void setupMavenRepo() {
        mavenRepo = new FakeMavenRepository();
    }

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MavenComponentVersionDao versionDao;

    @Autowired
    private MavenVersionDiscoveryJob job;

    @AfterEach
    void tearDown() {
        // Clean up database and server after each test
        versionDao.deleteAll();
        mavenRepo.clear();
    }

    @Test
    void testDiscoverVersionsFromMavenApi() {
        // Set up fake Maven repository with multiple versions
        mavenRepo.addArtifact("releases", "net.neoforged", "neoforge")
                .withVersion("21.3.0-beta")
                .withVersion("21.3.1-beta")
                .withVersion("21.3.2")
                .withVersion("21.3.3")
                .withVersion("21.3.4")
                .withVersion("21.3.5-beta")
                .withSnapshot(false);

        job.run();

        // Verify results using DAOs
        var versions = versionDao.findAll();
        assertEquals(6, versions.size());

        var versionStrings = versionDao.findAllVersionsByGA("net.neoforged", "neoforge");
        assertTrue(versionStrings.contains("21.3.5-beta"));
        assertTrue(versionStrings.contains("21.3.0-beta"));
        assertTrue(versionStrings.contains("21.3.4"));

        // Verify all are marked as not snapshot
        assertTrue(versions.stream().noneMatch(SoftwareComponentVersion::isSnapshot));
    }

    @Test
    void testHandlesMissingArtifact() {
        // Configure repository to return 404
        mavenRepo.addMissingArtifact("releases", "net.neoforged", "neoforge");

        // Should not throw exception
        assertDoesNotThrow(job::run);

        // Verify no versions were added
        assertEquals(0, versionDao.findAll().size());
    }

    @Test
    void testHandlesInvalidJson() {
        // Configure repository to return invalid JSON
        mavenRepo.addInvalidArtifact("releases", "net.neoforged", "neoforge");

        // Should handle gracefully
        assertDoesNotThrow(job::run);

        // Verify no versions were added
        assertEquals(0, versionDao.findAll().size());
    }

    @Test
    void testAddsNewVersions() {
        // Set up repository with initial versions
        mavenRepo.addArtifact("releases", "net.neoforged", "neoforge")
                .withVersion("21.3.0")
                .withVersion("21.3.1")
                .withSnapshot(false);

        // Run job first time
        job.run();

        // Verify initial versions were added
        assertEquals(2, versionDao.findAll().size());

        // Update repository with new version
        mavenRepo.clear();
        mavenRepo.addArtifact("releases", "net.neoforged", "neoforge")
                .withVersion("21.3.0")
                .withVersion("21.3.1")
                .withVersion("21.3.2")  // New version
                .withSnapshot(false);

        // Run job again with updated repository
        job.run();

        // Verify new version was added (existing versions should not be duplicated)
        var versions = versionDao.findAll();
        assertEquals(3, versions.size());

        // Verify all versions are present
        var versionStrings = versionDao.findAllVersionsByGA("net.neoforged", "neoforge");
        assertTrue(versionStrings.contains("21.3.0"));
        assertTrue(versionStrings.contains("21.3.1"));
        assertTrue(versionStrings.contains("21.3.2"));
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "meta-api.data-directory=" + tempDir.toAbsolutePath()
            ).applyTo(context);
        }
    }
}
