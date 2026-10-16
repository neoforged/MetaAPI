package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Distributed lock for scheduled jobs to ensure only one replica executes a job at a time.
 * <p>
 * Uses PostgreSQL's {@code SELECT FOR UPDATE SKIP LOCKED} to allow multiple replicas
 * to compete for job execution without blocking each other.
 */
@Entity
public class JobLock {

    /**
     * Unique identifier for the job (e.g., "maven-version-discovery").
     */
    @Id
    private String jobId;

    /**
     * When the lock was acquired.
     */
    @Nullable
    private Instant lockedAt;

    /**
     * Instance ID of the replica that acquired the lock (for debugging).
     */
    @Nullable
    private String lockedBy;

    /**
     * When the lock expires. Used to recover from crashed replicas.
     */
    @Nullable
    private Instant expiresAt;

    /**
     * The scheduled slot (cron fire time) that was last executed for this job.
     * Prevents a second replica from re-running the same slot after a fast job released its lock.
     */
    @Nullable
    private Instant lastRunAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    @Nullable
    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(@Nullable Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    @Nullable
    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(@Nullable String lockedBy) {
        this.lockedBy = lockedBy;
    }

    @Nullable
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(@Nullable Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Nullable
    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(@Nullable Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
}
