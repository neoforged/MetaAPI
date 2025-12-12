package net.neoforged.meta.api;

import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.db.NeoForgeVersion;
import net.neoforged.meta.db.NeoForgeVersionDao;
import net.neoforged.meta.generated.api.MinecraftVersionsApi;
import net.neoforged.meta.generated.model.MinecraftVersionDetails;
import net.neoforged.meta.generated.model.MinecraftVersionSummary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class MinecraftVersionsController implements MinecraftVersionsApi {

    private final MinecraftVersionDao minecraftVersionDao;
    private final NeoForgeVersionDao neoForgeVersionDao;

    public MinecraftVersionsController(MinecraftVersionDao minecraftVersionDao, NeoForgeVersionDao neoForgeVersionDao) {
        this.minecraftVersionDao = minecraftVersionDao;
        this.neoForgeVersionDao = neoForgeVersionDao;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<MinecraftVersionSummary>> getMinecraftVersions() {
        // Build a map from minecraft version to latest NF version available for that
        var latestNeoForgeVersions = neoForgeVersionDao.findLatestForAllMinecraftVersions()
                .stream()
                .collect(Collectors.toMap(
                        NeoForgeVersion::getMinecraftVersion,
                        v -> v,
                        (a, _) -> a
                ));

        var result = new ArrayList<MinecraftVersionSummary>();
        for (var version : minecraftVersionDao.findAll()) {
            var neoForgeVersion = latestNeoForgeVersions.get(version);

            var summary = new MinecraftVersionSummary(
                    version.getVersion(),
                    version.getType(),
                    version.getReleased().atOffset(ZoneOffset.UTC),
                    version.getLastModified().atOffset(ZoneOffset.UTC),
                    neoForgeVersion != null ? neoForgeVersion.getVersion() : null
            );
            result.add(summary);
        }

        return ResponseEntity.ok(result);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<MinecraftVersionDetails> getMinecraftVersionDetails(String versionId) {
        var version = minecraftVersionDao.getByVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        var details = new MinecraftVersionDetails(
                version.getVersion(),
                version.getType(),
                version.getReleased().atOffset(ZoneOffset.UTC),
                version.getLastModified().atOffset(ZoneOffset.UTC),
                version.getJavaVersion()
        );
        return ResponseEntity.ok(details);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getMinecraftVersionManifest(String versionId) {
        var version = minecraftVersionDao.getByVersion(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        var manifest = version.getManifest();
        if (manifest == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .eTag("\"" + manifest.getSha1() + "\"")
                .lastModified(manifest.getLastModified().toEpochMilli())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(manifest.getContent());
    }
}
