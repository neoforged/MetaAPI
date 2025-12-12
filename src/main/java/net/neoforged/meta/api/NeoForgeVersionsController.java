package net.neoforged.meta.api;

import net.neoforged.meta.config.MetaApiProperties;
import net.neoforged.meta.db.SoftwareComponentChangelog;
import net.neoforged.meta.generated.api.NeoforgeVersionsApi;
import net.neoforged.meta.generated.model.NeoForgeVersionDetails;
import net.neoforged.meta.generated.model.NeoForgeVersionSummary;
import net.neoforged.meta.generated.model.ReleaseNotes;
import net.neoforged.meta.generated.model.SoftwareComponentArtifact;
import net.neoforged.meta.maven.NeoForgeVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
public class NeoForgeVersionsController implements NeoforgeVersionsApi {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeVersionsController.class);

    private final NeoForgeVersionService neoForgeVersionService;
    private final MetaApiProperties apiProperties;

    public NeoForgeVersionsController(NeoForgeVersionService neoForgeVersionService,
                                      MetaApiProperties apiProperties) {
        this.neoForgeVersionService = neoForgeVersionService;
        this.apiProperties = apiProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getNeoForgeVersionLauncherManifest(String versionId) {
        var version = neoForgeVersionService.getVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(version.getLauncherProfile());
    }

    @Override
    public ResponseEntity<Object> getNeoForgeVersionInstallerProfile(String versionId) {
        var version = neoForgeVersionService.getVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(version.getInstallerProfile());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<NeoForgeVersionSummary>> getNeoForgeVersions() {
        var versions = neoForgeVersionService.getVersions();
        return ResponseEntity.ok(versions.stream().map(version -> {
            return new NeoForgeVersionSummary(version.getVersion(),
                    version.getReleased().atOffset(ZoneOffset.UTC),
                    version.getLastModified().atOffset(ZoneOffset.UTC),
                    version.getMinecraftVersion().getVersion());
        }).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<NeoForgeVersionDetails> getNeoForgeVersionDetails(String versionId) {
        var version = neoForgeVersionService.getVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        var repository = apiProperties.getMavenRepositories().stream().filter(repo -> repo.getId().equals(version.getRepository())).findFirst().orElse(null);
        if (repository == null) {
            LOG.error("Version {} reference unknown repository {}", versionId, version.getRepository());
            return ResponseEntity.internalServerError().build();
        }

        var artifacts = version.getArtifacts().stream().map(artifact -> new SoftwareComponentArtifact(
                artifact.getClassifier(),
                artifact.getExtension(),
                artifact.getSize(),
                artifact.getLastModified().atOffset(ZoneOffset.UTC),
                repository.getDownloadUrl(artifact),
                artifact.getMd5Checksum(),
                artifact.getSha1Checksum(),
                artifact.getSha256Checksum(),
                artifact.getSha512Checksum()
        )).toList();

        ReleaseNotes releaseNotes = null;
        if (version.getChangelog() != null) {
            releaseNotes = convert(version.getChangelog());
        }

        var details = new NeoForgeVersionDetails(
                version.getGroupId(),
                version.getArtifactId(),
                version.getVersion(),
                version.getReleased().atOffset(ZoneOffset.UTC),
                version.getLastModified().atOffset(ZoneOffset.UTC),
                artifacts,
                releaseNotes,
                version.getMinecraftVersion().getVersion()
        );

        return ResponseEntity.ok(details);
    }

    private ReleaseNotes convert(SoftwareComponentChangelog changelog) {
        var markdownText = changelog.getChangelog();
        markdownText = markdownText.replaceAll("#([1-9]\\d*)", "[#$1](https://github.com/neoforged/NeoForge/issues/$1)");
        return new ReleaseNotes(changelog.getChangelog(), markdownText);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<String> getNeoForgeServerArgsUnix(String versionId) {
        var version = neoForgeVersionService.getVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("");
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<String> getNeoForgeServerArgsWindows(String versionId) {
        var version = neoForgeVersionService.getVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("");
    }
}
