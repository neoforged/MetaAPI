package net.neoforged.meta.db;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrokenSoftwareComponentVersionDao extends JpaRepository<BrokenSoftwareComponentVersion, Long> {

    @Query("select bv.id, bv.versionString, bv.created, bv.lastAttempt, bv.retry from BrokenSoftwareComponentVersion bv where bv.groupId = :groupId and bv.artifactId = :artifactId")
    List<BrokenVersionSummary> findSummariesByGA(String groupId, String artifactId);

    @Query("select bv from BrokenSoftwareComponentVersion bv where bv.groupId = :groupId and bv.artifactId = :artifactId and bv.versionString = :version")
    @Nullable
    BrokenSoftwareComponentVersion findByGAV(String groupId, String artifactId, String version);

    @Modifying
    @Query("update BrokenSoftwareComponentVersion set retry = false, attempts = attempts + 1 where id = :id")
    void incrementRetryCount(long id);

    @Modifying
    @Query("update BrokenSoftwareComponentVersion set retry = true")
    int retryAll();

    /**
     * Try to acquire a retry lease for a specific version.
     * Uses SELECT FOR UPDATE so only one replica can claim the row at a time.
     * Only acquires if the version is marked for retry and not already leased (or lease expired).
     * The caller must write the lease owner/expiry on the returned entity within
     * the same transaction to complete acquisition.
     *
     * @param groupId    The group ID
     * @param artifactId The artifact ID
     * @param version    The version string
     * @param now        Current timestamp for lease expiry comparison
     * @return The locked entity if lease acquired, empty otherwise
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bv FROM BrokenSoftwareComponentVersion bv " +
            "WHERE bv.groupId = :groupId AND bv.artifactId = :artifactId AND bv.versionString = :version " +
            "AND bv.retry = true " +
            "AND (bv.retryingBy IS NULL OR bv.retryingUntil < :now)")
    Optional<BrokenSoftwareComponentVersion> tryAcquireRetryLease(
            String groupId, String artifactId, String version, Instant now);

    /**
     * Release a retry lease if held by the specified instance.
     */
    @Modifying
    @Query("UPDATE BrokenSoftwareComponentVersion bv " +
            "SET bv.retryingBy = NULL, bv.retryingUntil = NULL " +
            "WHERE bv.id = :id AND bv.retryingBy = :instanceId")
    int releaseRetryLease(long id, String instanceId);

    /**
     * Clean up expired retry leases from crashed replicas.
     */
    @Modifying
    @Query("UPDATE BrokenSoftwareComponentVersion bv " +
            "SET bv.retryingBy = NULL, bv.retryingUntil = NULL " +
            "WHERE bv.retryingBy IS NOT NULL AND bv.retryingUntil < :now")
    int cleanupExpiredLeases(Instant now);

    record BrokenVersionSummary(long id, String versionString, Instant created, Instant lastAttempt, boolean retry) {
    }

}
