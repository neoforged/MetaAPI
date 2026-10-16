package net.neoforged.meta.jobs;

import org.springframework.stereotype.Service;

/**
 * Service for managing broken software component versions during discovery.
 * Provides methods to:
 * - Check if a version should be skipped due to previous failures
 * - Report success/failure when processing versions
 * - Handle retry logic and distributed locking in HA deployments
 */
@Service
public interface BrokenVersionService {

    /**
     * Get the broken versions tracker for Minecraft client.
     */
    BrokenVersions getBrokenMinecraftVersions();

    /**
     * Get the broken versions tracker for a specific component.
     */
    BrokenVersions getBrokenVersions(String groupId, String artifactId);

    /**
     * Tracks broken versions for a specific component (group:artifact).
     * Provides methods to check if versions should be skipped and report outcomes.
     */
    interface BrokenVersions {

        /**
         * Check if a version should be skipped during discovery.
         * - Returns true if the version is marked as broken and not retryable, or leased by another instance (HA mode).
         * - Returns false if the version is not broken, or retryable and lease acquired (HA mode).
         */
        boolean shouldSkipVersion(String version);

        /**
         * Report that processing a version failed. Records the error and handles retry logic.
         */
        void reportError(String version, Exception e);

        /**
         * Report that processing a version succeeded. Removes the broken entry and releases any lease.
         */
        void reportSuccess(String version);

        /**
         * Release the lease for a specific version (HA mode only, no-op in single-instance mode).
         * Useful if you acquired a lease but decide not to process after all.
         */
        void releaseLease(String version);
    }

}
