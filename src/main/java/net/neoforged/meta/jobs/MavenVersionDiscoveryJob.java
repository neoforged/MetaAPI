package net.neoforged.meta.jobs;

import net.neoforged.meta.config.MetaApiProperties;
import net.neoforged.meta.config.SoftwareComponentArtifactProperties;
import net.neoforged.meta.config.SoftwareComponentProperties;
import net.neoforged.meta.config.SoftwareComponentPublicationPropertiesRule;
import net.neoforged.meta.db.SoftwareComponentVersionDao;
import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.db.NeoForgeVersion;
import net.neoforged.meta.db.SoftwareComponentArtifact;
import net.neoforged.meta.db.SoftwareComponentReleaseNotes;
import net.neoforged.meta.db.SoftwareComponentVersion;
import net.neoforged.meta.event.EventService;
import net.neoforged.meta.extract.ChangelogExtractor;
import net.neoforged.meta.extract.NeoForgeVersionExtractor;
import net.neoforged.meta.maven.MavenRepositoriesFacade;
import net.neoforged.meta.maven.NeoForgeVersionService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Discover new versions of a Maven component using the NeoForged Maven API.
 * <p>
 * This job fetches version information from the /api/maven/versions endpoint
 * and stores discovered versions in the database.
 */
@Component
public class MavenVersionDiscoveryJob implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MavenVersionDiscoveryJob.class);

    private final SoftwareComponentVersionDao versionDao;
    private final List<SoftwareComponentProperties> components;
    private final MavenRepositoriesFacade mavenRepositories;
    private final TransactionTemplate transactionTemplate;
    private final MinecraftVersionDao minecraftVersionDao;
    private final EventService eventService;
    private final BrokenVersionService brokenVersionService;
    private final NeoForgeVersionService neoForgeVersionService;

    public MavenVersionDiscoveryJob(
            SoftwareComponentVersionDao versionDao,
            MetaApiProperties apiProperties,
            MavenRepositoriesFacade mavenRepositories,
            TransactionTemplate transactionTemplate,
            MinecraftVersionDao minecraftVersionDao,
            EventService eventService,
            BrokenVersionService brokenVersionService, NeoForgeVersionService neoForgeVersionService) {
        this.versionDao = versionDao;
        this.components = apiProperties.getComponents();
        this.mavenRepositories = mavenRepositories;
        this.transactionTemplate = transactionTemplate;
        this.minecraftVersionDao = minecraftVersionDao;
        this.eventService = eventService;
        this.brokenVersionService = brokenVersionService;
        this.neoForgeVersionService = neoForgeVersionService;
    }

    @Override
    public void run() {
        for (var component : components) {
            discoverComponent(component);
        }
    }

    public void discoverComponent(SoftwareComponentProperties component) {
        var groupId = component.getGroupId();
        var artifactId = component.getArtifactId();
        var repository = component.getMavenRepositoryId();

        if (neoForgeVersionService.isNeoForgeGA(groupId, artifactId) && minecraftVersionDao.count() == 0) {
            logger.error("Skipping discovery of {}:{} since no Minecraft versions are known yet.", groupId, artifactId);
            return;
        }

        logger.info("Discovering Maven versions for {}:{} in repository {}", groupId, artifactId, repository);

        // Get existing versions to avoid duplicates and broken versions to avoid rescanning them
        var existingVersions = Set.copyOf(versionDao.findAllVersionsByGA(groupId, artifactId));
        logger.info("Component has {} known versions.", existingVersions.size());
        var brokenVersions = brokenVersionService.getBrokenVersions(groupId, artifactId);

        var discoveredVersions = mavenRepositories.listComponentVersions(component.getMavenRepositoryId(), component.getGroupId(), component.getArtifactId());

        logger.info("Found {} versions for component {}:{}", discoveredVersions.size(), groupId, artifactId);

        int newVersions = 0;
        for (var version : discoveredVersions) {
            if (existingVersions.contains(version)) {
                continue;
            }

            if (brokenVersions.shouldSkipVersion(version)) {
                logger.debug("Skipping version {} because it is broken.", version);
                continue;
            }

            try {
                transactionTemplate.executeWithoutResult(ignored -> {
                    discoverVersion(component, version);
                    brokenVersions.reportSuccess(version);
                });
                eventService.newComponentVersion(component.getGroupId(), component.getArtifactId(), version);
                newVersions++;
            } catch (Exception e) {
                brokenVersions.reportError(version, e);
            }
        }

        logger.info("Completed Maven version discovery for {}:{}. Found {} new versions out of {} total",
                groupId, artifactId, newVersions, discoveredVersions.size());
    }


    private void discoverVersion(SoftwareComponentProperties component, String version) {
        SoftwareComponentVersion versionEntity;

        // Post-Process Component Specific Information
        if (neoForgeVersionService.isNeoForgeGA(component.getGroupId(), component.getArtifactId())) {
            var neoForgeVersion = new NeoForgeVersion();
            discoverBaseVersion(component, version, versionEntity = neoForgeVersion);

            discoverNeoForgeVersion(version, neoForgeVersion);
        } else {
            discoverBaseVersion(component, version, versionEntity = new SoftwareComponentVersion());
        }

        versionEntity.setDiscovered(Instant.now());
        versionEntity.setLastModified(versionEntity.getDiscovered());
        versionDao.saveAndFlush(versionEntity);

        logger.info("Discovered new version: {}:{}:{} ({} artifacts)", component.getMavenRepositoryId(), component.getArtifactId(), version, versionEntity.getArtifacts().size());
    }

    private void discoverNeoForgeVersion(String version, NeoForgeVersion neoForgeVersion) {
        // We need to parse information found in the installer jar, for which we need to download the jar file
        var installerArtifact = neoForgeVersion.getArtifact("installer", "jar");
        if (installerArtifact == null) {
            throw new IllegalStateException("Expected installer, but is missing.");
        }

        byte[] installerContent = mavenRepositories.getArtifact(neoForgeVersion.getRepository(), neoForgeVersion.getGroupId(), neoForgeVersion.getArtifactId(), neoForgeVersion.getVersion(), installerArtifact.getClassifier(), installerArtifact.getExtension());
        var versionMetadata = NeoForgeVersionExtractor.extract(installerContent);
        neoForgeVersion.setReleased(versionMetadata.releaseTime()); // The time in the profile may be more accurate

        var minecraftVersion = minecraftVersionDao.getByVersion(versionMetadata.minecraftVersion());
        if (minecraftVersion == null) {
            throw new IllegalStateException("NeoForge version " + version + " references unknown Minecraft version " + versionMetadata.minecraftVersion()); // TODO -> Record as parsing failure
        }

        neoForgeVersion.setMinecraftVersion(minecraftVersion);
        neoForgeVersion.setInstallerProfile(versionMetadata.installerProfile());
        neoForgeVersion.setLauncherProfile(versionMetadata.launcherProfile());
        neoForgeVersion.setLauncherProfileId(versionMetadata.launcherProfileId());
        neoForgeVersion.setLibraries(versionMetadata.libraries());
        neoForgeVersion.setClientStartup(versionMetadata.clientStartup());
        neoForgeVersion.setServerStartup(versionMetadata.serverStartup());
    }

    private void discoverBaseVersion(SoftwareComponentProperties component,
                                     String version,
                                     SoftwareComponentVersion versionEntity) {
        versionEntity.getWarnings().clear(); // Clear all previous warnings when discovery starts

        versionEntity.setGroupId(component.getGroupId());
        versionEntity.setArtifactId(component.getArtifactId());
        versionEntity.setVersion(version);
        versionEntity.setSnapshot(version.endsWith("-SNAPSHOT"));
        versionEntity.setRepository(component.getMavenRepositoryId());

        // Use last-modified of the .pom file which every maven publication should have as the last-modified timestamp of the release and thus the release time
        var pomHeaders = mavenRepositories.headArtifact(component.getMavenRepositoryId(), component.getGroupId(), component.getArtifactId(), version, null, "pom");
        long lastModified = pomHeaders.getLastModified();
        if (lastModified != -1) {
            versionEntity.setReleased(Instant.ofEpochMilli(lastModified));
        } else {
            versionEntity.setReleased(versionEntity.getDiscovered());
        }

        var publicationRule = findMatchingPublicationRule(component, version);
        if (publicationRule != null) {
            for (var artifact : publicationRule.artifacts()) {
                var artifactEntity = discoverArtifact(component, versionEntity, artifact);
                if (artifactEntity != null) {
                    versionEntity.getArtifacts().add(artifactEntity);
                }
            }
        }

        // Post-Process optional information
        var changelog = versionEntity.getArtifact("changelog", "txt");
        if (changelog != null) {
            parseChangelog(component, versionEntity, changelog);
        }
    }

    private void parseChangelog(SoftwareComponentProperties properties,
                                SoftwareComponentVersion versionEntity,
                                SoftwareComponentArtifact changelogArtifact) {
        var changelogBody = mavenRepositories.getArtifact(versionEntity.getRepository(), versionEntity.getGroupId(), versionEntity.getArtifactId(), versionEntity.getVersion(), changelogArtifact.getClassifier(), changelogArtifact.getExtension());

        var changelogEntry = ChangelogExtractor.extract(changelogBody);
        if (changelogEntry != null) {
            var changelogEntity = new SoftwareComponentReleaseNotes();
            changelogEntity.setComponentVersion(versionEntity);
            changelogEntity.setOriginalText(changelogEntry);
            changelogEntity.setText(ChangelogExtractor.extractText(properties, changelogEntry));
            changelogEntity.setMarkdown(ChangelogExtractor.extractMarkdown(properties, changelogEntry));
            versionEntity.setReleaseNotes(changelogEntity);
        } else {
            versionEntity.addWarning("Couldn't find changelog entry.");
        }
    }

    @Nullable
    private SoftwareComponentArtifact discoverArtifact(SoftwareComponentProperties component,
                                                       SoftwareComponentVersion versionEntity,
                                                       SoftwareComponentArtifactProperties artifact) {
        var artifactHeaders = mavenRepositories.headOptionalArtifact(component.getMavenRepositoryId(), component.getGroupId(), component.getArtifactId(), versionEntity.getVersion(), artifact.classifier(), artifact.extension());

        if (artifactHeaders == null) {
            if (artifact.optional()) {
                return null;
            }
            throw new IllegalStateException("Required artifact " + artifact + " is missing.");
        }

        var artifactContentLength = artifactHeaders.getContentLength();
        var artifactLastModified = artifactHeaders.getLastModified();
        var etag = artifactHeaders.getETag();

        // TODO: We need to deal with last modification being unknown
        if (artifactLastModified < 0) {
            throw new IllegalStateException("No last modification time available for artifact"); // TODO associate with artifact
        }
        if (artifactContentLength < 0) {
            throw new IllegalStateException("No content length available for artifact"); // TODO associate error with artifact
        }

        var artifactEntity = new SoftwareComponentArtifact();
        artifactEntity.setComponentVersion(versionEntity);
        artifactEntity.setClassifier(artifact.classifier());
        artifactEntity.setExtension(artifact.extension());
        artifactEntity.setRelativePath(mavenRepositories.getArtifactPath(component.getMavenRepositoryId(), component.getGroupId(), component.getArtifactId(), versionEntity.getVersion(), artifact.classifier(), artifact.extension()));
        artifactEntity.setSize(artifactContentLength);
        artifactEntity.setLastModified(Instant.ofEpochMilli(artifactLastModified));
        artifactEntity.setEtag(etag);

        // Try to discover the checksums
        for (var checksumType : SoftwareComponentArtifact.ChecksumType.values()) {
            String checksumExtension = Objects.requireNonNullElse(artifact.extension(), "") + checksumType.checksumExtension();
            var checksum = new String(mavenRepositories.getArtifact(
                    component.getMavenRepositoryId(),
                    component.getGroupId(),
                    component.getArtifactId(),
                    versionEntity.getVersion(),
                    artifact.classifier(),
                    checksumExtension
            )).trim().toLowerCase(Locale.ROOT);

            var checksumBytes = HexFormat.of().parseHex(checksum);
            if (checksumBytes.length != checksumType.byteLength()) {
                throw new IllegalStateException("Checksum value '" + checksum + "' does not match expected byte length " + checksumType.byteLength());
            }

            artifactEntity.setChecksum(checksumType, HexFormat.of().formatHex(checksumBytes));
        }

        return artifactEntity;
    }

    private static @Nullable SoftwareComponentPublicationPropertiesRule findMatchingPublicationRule(SoftwareComponentProperties component, String version) {
        // Find the appropriate rule to apply
        for (var rule : component.getPublicationRules()) {
            if (rule.matchesVersion(version)) {
                return rule;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Maven Version Discovery";
    }
}