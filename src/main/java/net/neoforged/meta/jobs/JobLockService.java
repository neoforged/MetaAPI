package net.neoforged.meta.jobs;

import net.neoforged.meta.db.JobLock;
import net.neoforged.meta.db.JobLockDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for acquiring, renewing, and releasing distributed job locks.
 * Ensures only one replica executes a given job at a time.
 * Only active in postgres (HA) mode.
 */
@Service
@Profile("postgres")
public class JobLockService {

    private static final Logger logger = LoggerFactory.getLogger(JobLockService.class);

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(15);

    private final JobLockDao dao;
    private final String instanceId;

    public JobLockService(JobLockDao dao, String instanceId) {
        this.dao = dao;
        this.instanceId = instanceId;
    }

    /**
     * Try to acquire the distributed lock for the specified scheduled slot.
     * <p>
     * Acquisition is a single atomic conditional UPDATE: it succeeds only if no other
     * replica currently holds the lock (or its lock has expired) AND this slot has not
     * already been executed by any replica. This guarantees exactly-once-per-slot
     * execution across replicas, even for jobs that finish in milliseconds.
     *
     * @param jobId The job identifier
     * @param slot  The scheduled fire time (from the cron expression) this run belongs to
     * @return true if lock was acquired, false otherwise
     */
    @Transactional
    public boolean tryAcquire(String jobId, Instant slot) {
        return doTryAcquire(jobId, DEFAULT_LOCK_TTL, slot);
    }

    /**
     * Try to acquire the distributed lock with a custom TTL.
     *
     * @param jobId The job identifier
     * @param ttl   Time-to-live for the lock
     * @param slot  The scheduled fire time (from the cron expression) this run belongs to
     * @return true if lock was acquired, false otherwise
     */
    @Transactional
    public boolean tryAcquire(String jobId, Duration ttl, Instant slot) {
        return doTryAcquire(jobId, ttl, slot);
    }

    private boolean doTryAcquire(String jobId, Duration ttl, Instant slot) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        // Ensure the lock row exists (idempotent; safe under concurrent creation)
        dao.ensureRow(jobId);

        int acquired = dao.acquireIfFree(jobId, instanceId, now, expiry, slot);
        if (acquired > 0) {
            logger.info("Acquired lock for job '{}' (expires at {})", jobId, expiry);
            return true;
        }

        // For logging: explain why we couldn't acquire
        JobLock current = dao.findById(jobId).orElse(null);
        if (current != null) {
            if (instanceId.equals(current.getLockedBy())) {
                logger.debug("Job '{}' lock already held by this instance", jobId);
            } else if (current.getLockedBy() != null && !isExpired(current, now)) {
                logger.debug("Job '{}' is already running by instance '{}' (expires at {})",
                        jobId, current.getLockedBy(), current.getExpiresAt());
            } else if (current.getLastRunAt() != null && !current.getLastRunAt().isBefore(slot)) {
                logger.debug("Job '{}' slot {} was already executed (last run at {})",
                        jobId, slot, current.getLastRunAt());
            } else {
                logger.debug("Could not acquire lock for job '{}'", jobId);
            }
        }
        return false;
    }

    private static boolean isExpired(JobLock lock, Instant now) {
        return lock.getExpiresAt() != null && lock.getExpiresAt().isBefore(now);
    }

    /**
     * Release a lock for the specified job.
     * Idempotent: safe to call even if lock wasn't held.
     *
     * @param jobId The job identifier
     */
    @Transactional
    public void release(String jobId) {
        int released = dao.release(jobId, instanceId);
        if (released > 0) {
            logger.info("Released lock for job '{}'", jobId);
        } else {
            logger.debug("Lock for job '{}' was not held by this instance", jobId);
        }
    }

    /**
     * Renew a lock's expiration time. Used by long-running jobs to prevent
     * lock expiry while they're still working.
     *
     * @param jobId The job identifier
     * @param ttl   New TTL from now
     * @return true if renewed, false if lock was lost (another replica took it)
     */
    @Transactional
    public boolean renew(String jobId, Duration ttl) {
        int renewed = dao.renew(jobId, instanceId, Instant.now().plus(ttl));
        if (renewed == 0) {
            logger.warn("Failed to renew lock for job '{}' - lock may have been lost", jobId);
            return false;
        }
        return true;
    }

    /**
     * Clean up expired locks from crashed replicas.
     * Should be called periodically.
     */
    @Transactional
    public void cleanupExpired() {
        int cleaned = dao.cleanupExpired(Instant.now());
        if (cleaned > 0) {
            logger.info("Cleaned up {} expired job locks", cleaned);
        }
    }
}
