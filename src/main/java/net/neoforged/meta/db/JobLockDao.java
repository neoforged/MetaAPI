package net.neoforged.meta.db;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * DAO for distributed job locking using PostgreSQL's SELECT FOR UPDATE SKIP LOCKED.
 * Only active in postgres (HA) mode.
 */
@Repository
@Profile("postgres")
public interface JobLockDao extends JpaRepository<JobLock, String> {

    /**
     * Ensure a lock row exists for the given job. Idempotent: does nothing if the row
     * already exists (even when created concurrently by another replica).
     */
    @Modifying
    @Query(value = "INSERT INTO job_lock (job_id) VALUES (:jobId) ON CONFLICT (job_id) DO NOTHING", nativeQuery = true)
    void ensureRow(@Param("jobId") String jobId);

    /**
     * Atomically acquire the lock and mark this scheduled slot as executed.
     * Succeeds only if the row is free (unlocked or expired) AND no replica has already
     * executed this slot ({@code last_run_at < :slot}). A single conditional UPDATE makes
     * acquisition race-free: Postgres serializes concurrent updates on the row and each
     * re-evaluates the WHERE clause against committed state.
     *
     * @param jobId      The job identifier
     * @param instanceId ID of the acquiring replica
     * @param now        Current timestamp (lock acquisition time)
     * @param expiry     Lock expiration
     * @param slot       Scheduled fire time this execution belongs to
     * @return 1 if acquired, 0 otherwise
     */
    @Modifying
    @Query("UPDATE JobLock jl SET jl.lockedBy = :instanceId, " +
            "jl.lockedAt = :now, jl.expiresAt = :expiry, jl.lastRunAt = :slot " +
            "WHERE jl.jobId = :jobId " +
            "AND (jl.lockedBy IS NULL OR jl.expiresAt < :now) " +
            "AND (jl.lastRunAt IS NULL OR jl.lastRunAt < :slot)")
    int acquireIfFree(@Param("jobId") String jobId,
                     @Param("instanceId") String instanceId,
                     @Param("now") Instant now,
                     @Param("expiry") Instant expiry,
                     @Param("slot") Instant slot);

    /**
     * Release a lock if it was held by the specified instance.
     *
     * @param jobId       The job identifier
     * @param instanceId  The instance that wants to release
     * @return true if the lock was released, false if it wasn't held by this instance
     */
    @Modifying
    @Query("UPDATE JobLock jl SET jl.lockedBy = NULL, jl.lockedAt = NULL, jl.expiresAt = NULL WHERE jl.jobId = :jobId AND jl.lockedBy = :instanceId")
    int release(String jobId, String instanceId);

    /**
     * Renew a lock's expiration time (for long-running jobs).
     *
     * @param jobId       The job identifier
     * @param instanceId  The instance renewing the lock
     * @param newExpiry   New expiration time
     * @return true if renewed, false if lock was not held by this instance
     */
    @Modifying
    @Query("UPDATE JobLock jl SET jl.expiresAt = :newExpiry WHERE jl.jobId = :jobId AND jl.lockedBy = :instanceId")
    int renew(String jobId, String instanceId, Instant newExpiry);

    /**
     * Clean up locks that have expired (e.g., due to replica crash).
     *
     * @param now Current timestamp
     * @return Number of locks cleaned up
     */
    @Modifying
    @Query("UPDATE JobLock jl SET jl.lockedBy = NULL, jl.lockedAt = NULL, jl.expiresAt = NULL WHERE jl.lockedBy IS NOT NULL AND jl.expiresAt < :now")
    int cleanupExpired(Instant now);

    /**
     * Ensure lock rows exist for all specified job IDs (idempotent).
     */
    @Query("SELECT jl.jobId FROM JobLock jl")
    List<String> findAllJobIds();
}
