package net.neoforged.meta.db;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a version of a software component (e.g., net.neoforged:neoforge:21.0.1).
 */
@Entity
@Table(
        indexes = {
                @Index(name = "idx_maven_group_artifact", columnList = "groupId, artifactId"),
                @Index(name = "idx_maven_gav", columnList = "groupId, artifactId, version", unique = true)
        }
)
public class SoftwareComponentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    /**
     * Version string (e.g., "21.0.1", "21.0.2-beta")
     */
    @Column(nullable = false)
    private String version;

    /**
     * Whether this is a snapshot version, which means it can change over time without notice.
     */
    @Column(nullable = false)
    private boolean snapshot;

    /**
     * Id of the {@link net.neoforged.meta.config.MavenRepositoryProperties} this version was discovered in.
     */
    @Column(nullable = false)
    private String repository;

    /**
     * When this version was released. This can be hard to determine for Maven artifacts.
     * We HEAD the POM to detect when it was created.
     */
    @Column(nullable = false)
    private Instant released;

    /**
     * When this version was first discovered
     */
    @Column(nullable = false)
    private Instant discovered = Instant.now();

    @OneToOne(mappedBy = "componentVersion", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    @Nullable
    private SoftwareComponentChangelog changelog;

    @OneToMany(mappedBy = "componentVersion", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private List<SoftwareComponentArtifact> artifacts = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    public void setSnapshot(boolean snapshot) {
        this.snapshot = snapshot;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public Instant getReleased() {
        return released;
    }

    public void setReleased(Instant released) {
        this.released = released;
    }

    public Instant getDiscovered() {
        return discovered;
    }

    public void setDiscovered(Instant discovered) {
        this.discovered = discovered;
    }

    public List<SoftwareComponentArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<SoftwareComponentArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public @Nullable SoftwareComponentChangelog getChangelog() {
        return changelog;
    }

    public void setChangelog(@Nullable SoftwareComponentChangelog changelog) {
        this.changelog = changelog;
    }

    @Nullable
    public SoftwareComponentArtifact getArtifact(@Nullable String classifier, @Nullable String extension) {
        for (var artifact : getArtifacts()) {
            if (Objects.equals(classifier, artifact.getClassifier()) && Objects.equals(extension, artifact.getExtension())) {
                return artifact;
            }
        }
        return null;
    }
}
