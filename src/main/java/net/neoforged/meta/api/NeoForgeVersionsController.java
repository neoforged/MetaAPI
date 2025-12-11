package net.neoforged.meta.api;

import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.generated.api.NeoforgeVersionsApi;
import net.neoforged.meta.maven.NeoForgeVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NeoForgeVersionsController implements NeoforgeVersionsApi {

    private final MinecraftVersionDao minecraftVersionDao;
    private final NeoForgeVersionService neoForgeVersionService;

    public NeoForgeVersionsController(MinecraftVersionDao minecraftVersionDao, NeoForgeVersionService neoForgeVersionService) {
        this.minecraftVersionDao = minecraftVersionDao;
        this.neoForgeVersionService = neoForgeVersionService;
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
}
