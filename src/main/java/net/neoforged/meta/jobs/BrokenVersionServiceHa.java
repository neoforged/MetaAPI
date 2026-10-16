package net.neoforged.meta.jobs;

import net.neoforged.meta.db.BrokenSoftwareComponentVersion;
import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import net.neoforged.meta.db.DiscoveryLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HA-enabled implementation of BrokenVersionService for PostgreSQL deployments.
 * Uses distributed leases to prevent multiple replicas from processing the same broken version simultaneously.
 */
@Service
@Profile("postgres")
public class BrokenVersionServiceHa implements BrokenVersionService {

    private static final Logger logger = LoggerFactory.getLogger(BrokenVersionServiceHa.class);
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final long CLEANUP_INTERVAL_MS = 5 * 60_000; // every 5 minutes
    private static final String MINECRAFT_GROUP_ID = "net.minecraft";
    private static final String MINECRAFT_ARTIFACT_ID = "client";

    private final BrokenSoftwareComponentVersionDao dao;
    private final TransactionTemplate transactionTemplate;
    private final String instanceId;

    public BrokenVersionServiceHa(BrokenSoftwareComponentVersionDao dao,
                                 TransactionTemplate transactionTemplate,
                                 String instanceId) {
        this.dao = dao;
        this.transactionTemplate = transactionTemplate;
        this.instanceId = instanceId;
    }

    @Override
    public BrokenVersions getBrokenMinecraftVersions() {
        return getBrokenVersions(MINECRAFT_GROUP_ID, MINECRAFT_ARTIFACT_ID);
    }

    @Override
    public BrokenVersions getBrokenVersions(String groupId, String artifactId) {
        var brokenVersions = dao.findSummariesByGA(groupId, artifactId)
                .stream()
                .collect(Collectors.toMap(
                        BrokenSoftwareComponentVersionDao.BrokenVersionSummary::versionString,
                        summary -> summary
                ));
        logger.info("Component {}:{} has {} broken versions.", groupId, artifactId, brokenVersions.size());
        return new BrokenVersionTracker(groupId, artifactId, brokenVersions);
    }

    /**
     * Periodically clean up expired retry leases from crashed replicas.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpiredLeases() {
        Integer cleaned = transactionTemplate.execute(status -> dao.cleanupExpiredLeases(Instant.now()));
        if (cleaned != null && cleaned > 0) {
            logger.info("Cleaned up {} expired retry leases", cleaned);
        }
    }

    private class BrokenVersionTracker implements BrokenVersions {
        private final String groupId;
        private final String artifactId;
        private final Map<String, BrokenSoftwareComponentVersionDao.BrokenVersionSummary> summaries;

        public BrokenVersionTracker(String groupId, String artifactId,
                                   Map<String, BrokenSoftwareComponentVersionDao.BrokenVersionSummary> summaries) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.summaries = summaries;
        }

        @Override
        public boolean shouldSkipVersion(String version) {
            var brokenVersionSummary = summaries.get(version);
            if (brokenVersionSummary == null) {
                return false; // Not in broken list, process normally
            }

            if (!brokenVersionSummary.retry()) {
                logger.debug("Skipping version {}:{}:{} - marked as broken, not retryable",
                        groupId, artifactId, version);
                return true;
            }

            // Try to acquire a retry lease for this version
            boolean leased = tryAcquireLease(brokenVersionSummary.id(), brokenVersionSummary.versionString());

            if (!leased) {
                logger.debug("Skipping version {}:{}:{} - lease held by another instance",
                        groupId, artifactId, version);
                return true;
            }

            // Lease acquired - clear the retry flag so we own this version now.
            // If subsequent processing fails, reportError will handle it (and the lease remains).
            transactionTemplate.executeWithoutResult(ignored -> dao.incrementRetryCount(brokenVersionSummary.id()));

            logger.info("Acquired retry lease for version {}:{}:{} (instance: {}, expires in {})",
                    groupId, artifactId, version, instanceId, LEASE_TTL);

            return false; // Don't skip - we own this version now
        }

        @Override
        public void reportError(String version, Exception e) {
            var summary = summaries.get(version);
            if (summary != null && isLeasedByMe(summary.id())) {
                // Refresh the lease since we're still working on this version (error during processing)
                refreshLease(summary.id(), summary.versionString());
            }

            reportDiscoveryError(groupId, artifactId, version, summary, e);
        }

        @Override
        public void reportSuccess(String version) {
            var summary = summaries.get(version);
            if (summary != null) {
                transactionTemplate.executeWithoutResult(ignored -> {
                    // Release any lease first (idempotent)
                    dao.releaseRetryLease(summary.id(), instanceId);

                    // Delete the broken version entry - it's fixed
                    dao.deleteById(summary.id());
                });

                logger.info("Version {}:{}:{} recovered successfully, removed from broken list",
                        groupId, artifactId, version);
            }
        }

        @Override
        public void releaseLease(String version) {
            var summary = summaries.get(version);
            if (summary != null && isLeasedByMe(summary.id())) {
                transactionTemplate.executeWithoutResult(ignored -> dao.releaseRetryLease(summary.id(), instanceId));
                logger.debug("Released retry lease for version {}:{}:{} (instance: {})",
                        groupId, artifactId, version, instanceId);
            }
        }

        private boolean isLeasedByMe(long id) {
            return transactionTemplate.execute(status -> {
                var bv = dao.getReferenceById(id);
                return instanceId.equals(bv.getRetryingBy());
            });
        }

        private void refreshLease(long id, String version) {
            transactionTemplate.executeWithoutResult(ignored -> {
                var bv = dao.getReferenceById(id);
                if (instanceId.equals(bv.getRetryingBy())) {
                    bv.setRetryingUntil(Instant.now().plus(LEASE_TTL));
                    dao.saveAndFlush(bv);
                    logger.debug("Refreshed retry lease for version {}:{}:{} (instance: {}, expires at {})",
                            groupId, artifactId, version, instanceId, bv.getRetryingUntil());
                }
            });
        }

        private boolean tryAcquireLease(long summaryId, String version) {
            return transactionTemplate.execute(status -> {
                var bv = dao.getReferenceById(summaryId);
                String gaVersionKey = bv.getGroupId() + ":" + bv.getArtifactId() + ":" + version;

                Instant now = Instant.now();
                var leased = dao.tryAcquireRetryLease(
                        bv.getGroupId(), bv.getArtifactId(), version, now);

                if (leased.isPresent()) {
                    // Mark the row as leased by this instance. The row is locked for
                    // the duration of this transaction, so no other replica can claim it.
                    var locked = leased.get();
                    locked.setRetryingBy(instanceId);
                    locked.setRetryingUntil(now.plus(LEASE_TTL));
                    dao.saveAndFlush(locked);
                    logger.debug("Acquired retry lease for {} (instance: {}, expires at {})", gaVersionKey, instanceId, locked.getRetryingUntil());
                    return true;
                } else {
                    // Check if another instance holds it (for logging)
                    var current = dao.getReferenceById(summaryId);
                    if (current.getRetryingBy() != null && !current.getRetryingBy().equals(instanceId)) {
                        logger.debug("Retry lease for {} held by instance '{}' (expires at {})",
                                gaVersionKey, current.getRetryingBy(), current.getRetryingUntil());
                    }
                    return false;
                }
            });
        }

        private void reportDiscoveryError(String groupId, String artifactId, String version,
                                         BrokenSoftwareComponentVersionDao.BrokenVersionSummary brokenVersionSummary,
                                         Exception e) {
            logger.error("Discovery of {}:{}:{} failed: {}", groupId, artifactId, version, e.toString());
            transactionTemplate.executeWithoutResult(ignored -> {
                BrokenSoftwareComponentVersion brokenVersion;
                if (brokenVersionSummary != null) {
                    brokenVersion = dao.getReferenceById(brokenVersionSummary.id());
                    brokenVersion.setLastAttempt(Instant.now());
                    brokenVersion.getErrors().clear();
                    brokenVersion.setAttempts(brokenVersion.getAttempts() + 1);
                } else {
                    brokenVersion = new BrokenSoftwareComponentVersion();
                    brokenVersion.setGroupId(groupId);
                    brokenVersion.setArtifactId(artifactId);
                    brokenVersion.setVersionString(version);
                    brokenVersion.setCreated(Instant.now());
                    brokenVersion.setLastAttempt(brokenVersion.getCreated());
                    brokenVersion.setAttempts(1);
                }

                var error = new DiscoveryLogMessage();
                var sw = new StringWriter();
                var w = new PrintWriter(sw);
                e.printStackTrace(w);
                w.close();
                error.setDetails(sw.toString());
                brokenVersion.getErrors().add(error);

                dao.saveAndFlush(brokenVersion);
            });
        }
    }
}
