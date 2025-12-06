package net.neoforged.meta.db;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MavenComponentVersionDao extends JpaRepository<SoftwareComponentVersion, Long> {

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

    /**
     * Get all versions for a specific Maven artifact.
     *
     * @param groupId    The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return List of all versions for this artifact
     */
    @Query("from SoftwareComponentVersion where groupId = :groupId and artifactId = :artifactId order by discovered desc")
    List<SoftwareComponentVersion> findAllByGA(String groupId, String artifactId);

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
}
