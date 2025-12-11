package net.neoforged.meta.maven;

import net.neoforged.meta.db.NeoForgeVersion;
import net.neoforged.meta.db.SoftwareComponentVersion;
import net.neoforged.meta.db.SoftwareComponentVersionDao;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class NeoForgeVersionService {
    private static final String GROUP_ID = "net.neoforged";
    private static final String NEOFORGE_ARTIFACT_ID = "neoforge";
    private static final String FORGE_ARTIFACT_ID = "forge";
    private final SoftwareComponentVersionDao dao;

    public NeoForgeVersionService(SoftwareComponentVersionDao dao) {
        this.dao = dao;
    }

    public boolean isNeoForgeGA(String groupId, String artifactId) {
        return groupId.equals(GROUP_ID) && (artifactId.equals(NEOFORGE_ARTIFACT_ID) || artifactId.equals(FORGE_ARTIFACT_ID));
    }

    public List<NeoForgeVersion> getVersions() {
        var result = dao.findAllNeoForgeVersions();
        result.sort(Comparator.comparing(SoftwareComponentVersion::getReleased).reversed());
        return result;
    }

    @Nullable
    public NeoForgeVersion getVersion(String version) {
        var entity = dao.findByGAV(GROUP_ID, NEOFORGE_ARTIFACT_ID, version);
        if (entity instanceof NeoForgeVersion neoForgeVersion) {
            return neoForgeVersion;
        }

        entity = dao.findByGAV(GROUP_ID, FORGE_ARTIFACT_ID, version);
        if (entity instanceof NeoForgeVersion neoForgeVersion) {
            return neoForgeVersion;
        }

        return null;
    }
}
