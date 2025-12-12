package net.neoforged.meta.db;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SoftwareComponentVersionDao extends JpaRepository<SoftwareComponentVersion, Long> {

    /**
     * Finds the latest NeoForge version for each Minecraft version using a subquery.
     * This efficiently retrieves all latest versions in a single database query.
     * @return List of the latest NeoForge version for each Minecraft version
     */
    @Query("""
        SELECT nf FROM NeoForgeVersion nf
        WHERE (nf.minecraftVersion, nf.released) IN (
            SELECT nf2.minecraftVersion, MAX(nf2.released)
            FROM NeoForgeVersion nf2
            GROUP BY nf2.minecraftVersion
        )
        """)
    List<NeoForgeVersion> findLatestNeoForgeByMinecraftVersion();

    /**
     * Find a specific version of a Maven artifact.
     *
     * @param groupId    The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version    The version string
     * @return The matching version entity, or null if not found
     */
    @Query("from SoftwareComponentVersion where groupId = :groupId and artifactId = :artifactId and version = :version")
    @Nullable
    SoftwareComponentVersion findByGAV(String groupId, String artifactId, String version);

    @Query("from NeoForgeVersion order by released desc")
    List<NeoForgeVersion> findAllNeoForgeVersions();

    /**
     * Get all versions for a specific Maven artifact.
     *
     * @param groupId    The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return List of all versions for this artifact
     */
    @Query("select v.version, v.released, v.discovered, v.repository, size(v.warnings), size(v.artifacts), v.releaseNotes from SoftwareComponentVersion v where v.groupId = :groupId and v.artifactId = :artifactId order by v.released desc")
    List<Summary> findSummaryByGA(String groupId, String artifactId);

    record Summary(String version,
                   Instant released,
                   Instant discovered,
                   String repository,
                   int warningCount,
                   int artifactCount,
                   @Nullable SoftwareComponentReleaseNotes releaseNotes) {
    }

    /**
     * Get all version strings for a specific Maven artifact.
     *
     * @param groupId    The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return List of version strings
     */
    @Query("select version from SoftwareComponentVersion where groupId = :groupId and artifactId = :artifactId order by discovered desc")
    List<String> findAllVersionsByGA(String groupId, String artifactId);

    /**
     * Check if a specific version exists.
     *
     * @param groupId    The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version    The version string
     * @return true if the version exists
     */
    @Query("select count(*) > 0 from SoftwareComponentVersion where groupId = :groupId and artifactId = :artifactId and version = :version")
    boolean existsByGAV(String groupId, String artifactId, String version);

    @Query("select v.version, v.released from SoftwareComponentVersion v where v.groupId = :groupId and v.artifactId = :artifactId")
    List<VersionSummary> getVersionSummary(String groupId, String artifactId);

    public record VersionSummary(String version, Instant released) {
    }
}
