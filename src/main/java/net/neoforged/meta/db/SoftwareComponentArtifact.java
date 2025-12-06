package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(
        indexes = {
                @Index(columnList = "component_version,classifier,extension", unique = true)
        }
)
public class SoftwareComponentArtifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private SoftwareComponentVersion componentVersion;

    @Column(nullable = false)
    private String relativePath;

    @Nullable
    private String classifier;

    @Nullable
    private String extension;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private Instant lastModified;

    private String etag;

    @Column(nullable = false)
    private String md5Checksum;

    @Column(nullable = false)
    private String sha1Checksum;

    @Column(nullable = false)
    private String sha256Checksum;

    @Column(nullable = false)
    private String sha512Checksum;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SoftwareComponentVersion getComponentVersion() {
        return componentVersion;
    }

    public void setComponentVersion(SoftwareComponentVersion componentVersion) {
        this.componentVersion = componentVersion;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public @Nullable String getClassifier() {
        return classifier;
    }

    public void setClassifier(@Nullable String classifier) {
        this.classifier = classifier;
    }

    public @Nullable String getExtension() {
        return extension;
    }

    public void setExtension(@Nullable String extension) {
        this.extension = extension;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public @Nullable String getMd5Checksum() {
        return md5Checksum;
    }

    public void setMd5Checksum(@Nullable String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public @Nullable String getSha1Checksum() {
        return sha1Checksum;
    }

    public void setSha1Checksum(@Nullable String sha1Checksum) {
        this.sha1Checksum = sha1Checksum;
    }

    public @Nullable String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(@Nullable String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public @Nullable String getSha512Checksum() {
        return sha512Checksum;
    }

    public void setSha512Checksum(@Nullable String sha512Checksum) {
        this.sha512Checksum = sha512Checksum;
    }

    public String getChecksum(ChecksumType type) {
        return switch (type) {
            case MD5 -> getMd5Checksum();
            case SHA1 -> getSha1Checksum();
            case SHA256 -> getSha256Checksum();
            case SHA512 -> getSha512Checksum();
        };
    }

    public void setChecksum(ChecksumType type, String value) {
        switch (type) {
            case MD5 -> setMd5Checksum(value);
            case SHA1 -> setSha1Checksum(value);
            case SHA256 -> setSha256Checksum(value);
            case SHA512 -> setSha512Checksum(value);
        }
    }

    public enum ChecksumType {
        MD5(".md5", 128),
        SHA1(".sha1", 160),
        SHA256(".sha256", 256),
        SHA512(".sha512", 512);

        private final String checksumExtension;
        private final int bitLength;

        ChecksumType(String checksumExtension, int bitLength) {
            this.checksumExtension = checksumExtension;
            this.bitLength = bitLength;
        }

        public String checksumExtension() {
            return checksumExtension;
        }

        public int byteLength() {
            return bitLength / 8;
        }
    }
}
