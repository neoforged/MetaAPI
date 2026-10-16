package net.neoforged.meta.jobs;

import net.neoforged.meta.MetaApiApplication;
import net.neoforged.meta.db.BrokenSoftwareComponentVersion;
import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import net.neoforged.meta.db.DiscoveryLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single-instance (non-HA) implementation of BrokenVersionService.
 * Used when running with SQLite or any non-postgres profile.
 */
@Service
@Profile("!" + MetaApiApplication.CLI_PROFILE + " && !postgres")
public class BrokenVersionServiceDefault implements BrokenVersionService {

    private static final Logger logger = LoggerFactory.getLogger(BrokenVersionServiceDefault.class);
    private static final String MINECRAFT_GROUP_ID = "net.minecraft";
    private static final String MINECRAFT_ARTIFACT_ID = "client";

    private final BrokenSoftwareComponentVersionDao dao;
    private final TransactionTemplate transactionTemplate;

    public BrokenVersionServiceDefault(BrokenSoftwareComponentVersionDao dao,
                                      TransactionTemplate transactionTemplate) {
        this.dao = dao;
        this.transactionTemplate = transactionTemplate;
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
            if (brokenVersionSummary != null) {
                if (!brokenVersionSummary.retry()) {
                    return true;
                }

                // Clear the retry flag first since the following transactions can fail
                transactionTemplate.executeWithoutResult(ignored -> dao.incrementRetryCount(brokenVersionSummary.id()));
            }
            return false;
        }

        @Override
        public void reportError(String version, Exception e) {
            var summary = summaries.get(version);
            if (summary != null) {
                reportDiscoveryError(groupId, artifactId, version, summary, e);
            } else {
                // New failure - create a broken entry
                reportDiscoveryError(groupId, artifactId, version, null, e);
            }
        }

        @Override
        public void reportSuccess(String version) {
            var summary = summaries.get(version);
            if (summary != null) {
                dao.deleteById(summary.id());
            }
        }

        @Override
        public void releaseLease(String version) {
            // No-op in single-instance mode - no leases to manage
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
