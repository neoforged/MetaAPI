package net.neoforged.meta.jobs;

import net.neoforged.meta.db.BrokenSoftwareComponentVersion;
import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import net.neoforged.meta.db.DiscoveryLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BrokenVersionService {
    private static final String MINECRAFT_GROUP_ID = "net.minecraft";
    private static final String MINECRAFT_ARTIFACT_ID = "minecraft";
    private static final Logger logger = LoggerFactory.getLogger(BrokenVersionService.class);

    private final BrokenSoftwareComponentVersionDao dao;
    private final TransactionTemplate transactionTemplate;

    public BrokenVersionService(BrokenSoftwareComponentVersionDao dao, TransactionTemplate transactionTemplate) {
        this.dao = dao;
        this.transactionTemplate = transactionTemplate;
    }

    public final class BrokenVersions {
        private final String groupId;
        private final String artifactId;
        private final Map<String, BrokenSoftwareComponentVersionDao.BrokenVersionSummary> summaries;

        public BrokenVersions(String groupId, String artifactId, Map<String, BrokenSoftwareComponentVersionDao.BrokenVersionSummary> summaries) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.summaries = summaries;
        }

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

        public void reportError(String version, Exception e) {
            reportDiscoveryError(groupId, artifactId, version, summaries.get(version), e);
        }

        public void reportSuccess(String version) {
            var summary = summaries.get(version);
            if (summary != null) {
                dao.deleteById(summary.id());
            }
        }
    }

    public BrokenVersions getBrokenMinecraftVersions() {
        return getBrokenVersions(MINECRAFT_GROUP_ID, MINECRAFT_ARTIFACT_ID);
    }

    public BrokenVersions getBrokenVersions(String groupId, String artifactId) {
        var brokenVersions = dao.getBrokenVersions(groupId, artifactId)
                .stream()
                .collect(Collectors.toMap(
                        BrokenSoftwareComponentVersionDao.BrokenVersionSummary::version,
                        summary -> summary
                ));
        logger.info("Component {}:{} has {} broken versions.", groupId, artifactId, brokenVersions.size());
        return new BrokenVersions(groupId, artifactId, brokenVersions);
    }

    private void reportDiscoveryError(String groupId,
                                      String artifactId,
                                      String version,
                                      BrokenSoftwareComponentVersionDao.BrokenVersionSummary brokenVersionSummary,
                                      Exception e) {
        // TODO: Store the error on the version or as an event
        logger.error("Discovery of {}:{}:{} failed: {}", groupId, artifactId, version, e.toString());
        transactionTemplate.executeWithoutResult(ignored -> {
            // Update, if it already exists
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
                brokenVersion.setVersion(version);
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
