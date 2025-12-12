package net.neoforged.meta.db;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@NamedEntityGraph(name = "MinecraftVersion.full",
        attributeNodes = {
                @NamedAttributeNode("libraries"),
                @NamedAttributeNode("manifest"),
        }
)
public class MinecraftVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String version;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Instant released;

    /**
     * When this version was first discovered.
     */
    @Column(nullable = false)
    private Instant discovered;

    /**
     * When this version was last modified.
     */
    @Column(nullable = false)
    private Instant lastModified;

    /**
     * Whether this version was added automatically (i.e. by the job to sync with the launcher manifest).
     */
    @Column(nullable = false)
    private boolean imported;

    /**
     * The Java version declared by this Minecraft version in its launcher manifest.
     */
    @Column(nullable = false)
    private int javaVersion;

    @OneToOne(mappedBy = "minecraftVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private MinecraftVersionManifest manifest;

    @OneToMany(mappedBy = "minecraftVersion")
    private List<NeoForgeVersion> neoForgeVersions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "minecraft_version_libraries")
    private List<ReferencedLibrary> libraries = new ArrayList<>();

    /**
     * A flag that can be set to true to force this version to be reimported next time the Minecraft
     * versions are scanned.
     */
    @Column(nullable = false)
    private boolean reimport;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
    }

    public MinecraftVersionManifest getManifest() {
        return manifest;
    }

    public void setManifest(MinecraftVersionManifest manifest) {
        this.manifest = manifest;
        if (manifest != null) {
            manifest.setMinecraftVersion(this);
        }
    }

    public int getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(int javaVersion) {
        this.javaVersion = javaVersion;
    }

    public List<NeoForgeVersion> getNeoForgeVersions() {
        return neoForgeVersions;
    }

    public void setNeoForgeVersions(List<NeoForgeVersion> neoForgeVersions) {
        this.neoForgeVersions = neoForgeVersions;
    }

    public List<ReferencedLibrary> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<ReferencedLibrary> libraries) {
        this.libraries = libraries;
    }

    public boolean isReimport() {
        return reimport;
    }

    public void setReimport(boolean reimport) {
        this.reimport = reimport;
    }
}
