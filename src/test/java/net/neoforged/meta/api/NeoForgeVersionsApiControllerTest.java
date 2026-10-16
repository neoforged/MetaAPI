package net.neoforged.meta.api;

import net.neoforged.meta.db.MinecraftVersion;
import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.db.NeoForgeVersion;
import net.neoforged.meta.db.SoftwareComponentVersionDao;
import net.neoforged.meta.generated.model.NeoForgeVersionDetails;
import net.neoforged.meta.generated.model.NeoForgeVersionSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the NeoForge version list endpoint, including the
 * optional {@code minecraftVersion} query filter.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "meta-api.security.api-keys=test-filter-key"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers = NeoForgeVersionsApiControllerTest.Initializer.class)
@ActiveProfiles("test")
class NeoForgeVersionsApiControllerTest {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-filter-key";

    @LocalServerPort
    private int port;

    @TempDir
    static Path tempDir;

    @Autowired
    MinecraftVersionDao minecraftVersionDao;

    @Autowired
    SoftwareComponentVersionDao softwareComponentVersionDao;

    @BeforeEach
    void setUp() {
        // 29.1.0 -> MC 9.1.0, 29.2.0 -> MC 9.2.0 (newest release first)
        seedNeoForge(seedMinecraft("9.1.0", Instant.parse("2026-01-02T00:00:00Z")), "29.1.0", Instant.parse("2026-02-01T00:00:00Z"));
        seedNeoForge(seedMinecraft("9.2.0", Instant.parse("2026-03-05T00:00:00Z")), "29.2.0", Instant.parse("2026-04-01T00:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        softwareComponentVersionDao.deleteAll();
        minecraftVersionDao.deleteAll();
    }

    private MinecraftVersion seedMinecraft(String version, Instant released) {
        var mc = new MinecraftVersion();
        mc.setVersion(version);
        mc.setType("release");
        mc.setReleased(released);
        mc.setDiscovered(Instant.now());
        mc.setLastModified(Instant.now());
        mc.setImported(true);
        mc.setJavaVersion(21);
        mc.setReimport(false);
        return minecraftVersionDao.save(mc);
    }

    private NeoForgeVersion seedNeoForge(MinecraftVersion mc, String version, Instant released) {
        var nf = new NeoForgeVersion();
        nf.setGroupId(NeoForgeVersion.GROUP_ID);
        nf.setArtifactId("neoforge");
        nf.setVersion(version);
        nf.setSnapshot(false);
        nf.setRepository("releases");
        nf.setReleased(released);
        nf.setDiscovered(Instant.now());
        nf.setLastModified(Instant.now());
        nf.setMinecraftVersion(mc);
        nf.setLauncherProfileId(NeoForgeVersion.GROUP_ID + ":" + "neoforge" + ":" + version);
        nf.setLauncherProfile("{}");
        nf.setInstallerProfile("{}");
        return softwareComponentVersionDao.save(nf);
    }

    private List<NeoForgeVersionSummary> getVersions(String query) {
        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(API_KEY_HEADER, API_KEY_VALUE)
                .build();
        return restClient.get()
                .uri("/v1/neoforge-versions/" + (query == null ? "" : query))
                .retrieve()
                .body(new ParameterizedTypeReference<List<NeoForgeVersionSummary>>() { });
    }

    @Test
    void listWithoutFilterReturnsAllVersions() {
        var versions = getVersions(null);
        assertEquals(List.of("29.2.0", "29.1.0"), versions.stream().map(NeoForgeVersionSummary::getVersion).toList());
        assertTrue(versions.stream().allMatch(v -> v.getMinecraftVersion() != null));
    }

    @Test
    void listFilteredByMinecraftVersionReturnsOnlyMatchingVersions() {
        var versions = getVersions("?minecraftVersion=9.1.0");
        assertEquals(List.of("29.1.0"), versions.stream().map(NeoForgeVersionSummary::getVersion).toList());
        assertEquals("9.1.0", versions.get(0).getMinecraftVersion());
    }

    @Test
    void listFilteredByUnknownMinecraftVersionReturnsEmptyList() {
        var versions = getVersions("?minecraftVersion=0.0.0");
        assertTrue(versions.isEmpty());
    }

    @Test
    void detailsEndpointServesSeededVersion() {
        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(API_KEY_HEADER, API_KEY_VALUE)
                .build();
        NeoForgeVersionDetails details = restClient.get()
                .uri("/v1/neoforge-versions/version/29.1.0/")
                .retrieve()
                .body(NeoForgeVersionDetails.class);
        assertEquals("net.neoforged", details.getGroupId());
        assertEquals("neoforge", details.getArtifactId());
        assertEquals("29.1.0", details.getVersion());
        assertEquals("9.1.0", details.getMinecraftVersion());
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
