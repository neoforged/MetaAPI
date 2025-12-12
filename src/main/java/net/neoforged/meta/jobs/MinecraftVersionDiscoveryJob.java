package net.neoforged.meta.jobs;

import net.neoforged.meta.config.MetaApiProperties;
import net.neoforged.meta.db.MinecraftVersion;
import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.db.MinecraftVersionManifest;
import net.neoforged.meta.db.ReferencedLibrary;
import net.neoforged.meta.manifests.launcher.LauncherManifest;
import net.neoforged.meta.maven.NeoForgeVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MinecraftVersionDiscoveryJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MinecraftVersionDiscoveryJob.class);

    private final RestClient restClient;
    private final MinecraftVersionDao minecraftVersionDao;
    private final BrokenVersionService brokenVersionService;
    private final TransactionTemplate transactionTemplate;

    public MinecraftVersionDiscoveryJob(MinecraftVersionDao minecraftVersionDao,
                                        MetaApiProperties apiProperties,
                                        BrokenVersionService brokenVersionService,
                                        TransactionTemplate transactionTemplate) {
        this.minecraftVersionDao = minecraftVersionDao;
        this.restClient = RestClient.builder()
                .baseUrl(apiProperties.getMinecraftLauncherMetaUrl())
                .build();
        this.brokenVersionService = brokenVersionService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run() {
        logger.info("Starting Minecraft metadata polling job");

        var launcherManifest = restClient.get().retrieve().body(LauncherManifest.class);

        var existingVersions = minecraftVersionDao.getAllVersions();
        logger.info("Discovered {} versions. {} are already known.", launcherManifest.versions().size(), existingVersions.size());

        var brokenVersions = brokenVersionService.getBrokenMinecraftVersions();

        var versionsAdded = new AtomicInteger();
        var versionsChanged = new AtomicInteger();

        for (var discoveredVersion : launcherManifest.versions()) {
            logger.trace("Working on version {}", discoveredVersion.id());

            if (brokenVersions.shouldSkipVersion(discoveredVersion.id())) {
                logger.debug("Skipping version {} since it's broken", discoveredVersion.id());
                continue;
            }

            // Import each version in a separate DB transaction to avoid locking the DB for too long.
            try {
                transactionTemplate.executeWithoutResult(ignored -> {
                    var existingVersion = minecraftVersionDao.getByVersion(discoveredVersion.id());
                    if (existingVersion != null) {
                        // Check if manifest changed
                        var existingManifest = existingVersion.getManifest();
                        boolean manifestChanged = existingManifest == null ||
                                !existingManifest.getSha1().equals(discoveredVersion.sha1());

                        if (manifestChanged || existingVersion.isReimport()) {
                            updateVersion(discoveredVersion, existingVersion);
                            existingVersion.setReimport(false);
                            existingVersion.setLastModified(Instant.now());
                            versionsChanged.incrementAndGet();
                        }
                        brokenVersions.reportSuccess(discoveredVersion.id());
                    } else {
                        existingVersion = new MinecraftVersion();
                        existingVersion.setVersion(discoveredVersion.id());
                        existingVersion.setDiscovered(Instant.now());
                        existingVersion.setLastModified(existingVersion.getDiscovered());
                        existingVersion.setImported(true);
                        updateVersion(discoveredVersion, existingVersion);
                        minecraftVersionDao.save(existingVersion);
                        versionsAdded.incrementAndGet();
                    }
                });
                brokenVersions.reportSuccess(discoveredVersion.id());
            } catch (Exception e) {
                brokenVersions.reportError(discoveredVersion.id(), e);
            }
        }

        logger.info("Completed Minecraft metadata polling job. Versions added: {}, changed: {}", versionsAdded.get(), versionsChanged.get());
    }

    private void updateVersion(LauncherManifest.Version discoveredVersion, MinecraftVersion version) {
        version.setType(discoveredVersion.type());
        version.setReleased(discoveredVersion.releaseTime().toInstant());

        // Fetch the manifest content from the URL
        String manifestContent = RestClient.create()
                .get()
                .uri(discoveredVersion.url())
                .retrieve()
                .body(String.class);

        if (manifestContent == null) {
            throw new IllegalArgumentException("Empty manifest received from " + discoveredVersion.url());
        }

        // Verify SHA1 checksum
        String actualSha1 = calculateSha1(manifestContent);
        if (!actualSha1.equalsIgnoreCase(discoveredVersion.sha1())) {
            String errorMsg = String.format(
                    "SHA1 checksum mismatch for version %s manifest. Expected: %s, Got: %s",
                    discoveredVersion.id(),
                    discoveredVersion.sha1(),
                    actualSha1
            );
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Create or update the manifest
        var manifest = version.getManifest();
        boolean isNew = manifest == null;
        if (isNew) {
            manifest = new MinecraftVersionManifest();
            version.setManifest(manifest);
        }

        // Only update lastModified if content actually changed
        boolean contentChanged = isNew || !manifestContent.equals(manifest.getContent());

        manifest.setImported(true);
        manifest.setSha1(discoveredVersion.sha1());
        manifest.setSourceUrl(discoveredVersion.url().toString());
        manifest.setContent(manifestContent);

        if (contentChanged || version.isReimport()) {
            manifest.setLastModified(Instant.now());
            // Parse manifest to extract Java version
            var parsedManifest = net.neoforged.meta.manifests.version.MinecraftVersionManifest.from(manifest.getContent());
            if (parsedManifest.javaVersion() == null) {
                // For some very old versions, we allow this
                if (parsedManifest.releaseTime().isBefore(OffsetDateTime.parse("2014-01-01T00:00:00Z").toInstant())) {
                    version.setJavaVersion(8);
                } else {
                    throw new IllegalStateException("Version manifest is missing java version.");
                }
            } else {
                version.setJavaVersion(parsedManifest.javaVersion().majorVersion());
            }

            version.getLibraries().clear();
            for (var library : parsedManifest.libraries()) {
                for (var referencedLibrary : ReferencedLibrary.of(library)) {
                    referencedLibrary.setClientClasspath(true);
                    version.getLibraries().add(referencedLibrary);
                }
            }
        }
    }

    private String calculateSha1(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(content.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    @Override
    public String toString() {
        return "Minecraft Version Discovery";
    }
}
