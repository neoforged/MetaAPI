package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * This entity records failures to discover particular versions of a software component and indicates
 * the reason for failure. A failed version entry prevents the version itself from being discovered
 * or updated again, and needs to be cleared.
 */
@Entity
@Table(
        indexes = {
                @Index(name = "idx_broken_version_maven_group_artifact", columnList = "groupId, artifactId"),
                @Index(name = "idx_broken_version_maven_gav", columnList = "groupId, artifactId, versionString", unique = true)
        }
)
public class BrokenSoftwareComponentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking version for concurrent update safety in HA deployments.
     */
    @Version
    private Long version;

    /**
     * Group ID (e.g., "net.neoforged") identifying the component.
     */
    @Column(nullable = false)
    private String groupId;

    /**
     * Artifact ID (e.g., "neoforge") identifying the component.
     */
    @Column(nullable = false)
    private String artifactId;

    @Column(nullable = false)
    private String versionString;

    @Column(nullable = false)
    private Instant created;

    @Column(nullable = false)
    private Instant lastAttempt;

    private boolean retry;

    private int attempts;

    /**
     * Instance ID that currently has this version locked for retry.
     * Used to prevent multiple replicas from retrying the same version.
     */
    @Nullable
    private String retryingBy;

    /**
     * When the retry lock expires. If null or in the past, another replica can take over.
     */
    @Nullable
    private Instant retryingUntil;

    @ElementCollection
    private List<DiscoveryLogMessage> errors = new ArrayList<>();

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersionString() {
        return versionString;
    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    public List<DiscoveryLogMessage> getErrors() {
        return errors;
    }

    public void setErrors(List<DiscoveryLogMessage> failureCode) {
        this.errors = failureCode;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(Instant lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

    public boolean isRetry() {
        return retry;
    }

    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    @Nullable
    public String getRetryingBy() {
        return retryingBy;
    }

    public void setRetryingBy(@Nullable String retryingBy) {
        this.retryingBy = retryingBy;
    }

    @Nullable
    public Instant getRetryingUntil() {
        return retryingUntil;
    }

    public void setRetryingUntil(@Nullable Instant retryingUntil) {
        this.retryingUntil = retryingUntil;
    }
}
