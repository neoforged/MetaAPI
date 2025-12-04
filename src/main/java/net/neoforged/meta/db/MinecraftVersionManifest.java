package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
public class MinecraftVersionManifest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "minecraft_version_id", nullable = false, unique = true)
    private MinecraftVersion minecraftVersion;

    /**
     * Whether this manifest was imported automatically (i.e. by the job to sync with the launcher manifest).
     */
    @Column(nullable = false)
    private boolean imported;

    @Column(nullable = false)
    private String sha1;

    @Column(nullable = false)
    private Instant lastModified;

    /**
     * URL pointing to the source of the launcher manifest (e.g., Mojang's service).
     * If present, indicates the manifest was obtained from an external source.
     */
    @Column(length = 512)
    private @Nullable String sourceUrl;

    /**
     * The manifest content stored directly in the database.
     * Automatically compressed using deflate.
     */
    @Column(nullable = false, columnDefinition = "BLOB")
    @jakarta.persistence.Convert(converter = DeflateConverter.class)
    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(MinecraftVersion minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public @Nullable String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(@Nullable String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
