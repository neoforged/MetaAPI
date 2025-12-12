package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import net.neoforged.meta.manifests.version.MinecraftDownload;
import net.neoforged.meta.manifests.version.MinecraftLibrary;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Libraries referenced by a Minecraft or NeoForge version.
 * <p>
 * They can be referenced in various ways:
 * <ul>
 *     <li>clientClasspath: The library is on the startup classpath of the client.</li>
 * </ul>
 */
@Embeddable
public class ReferencedLibrary {

    @Column(nullable = false)
    private String groupId;
    @Column(nullable = false)
    private String artifactId;
    @Column(nullable = false)
    private String version;
    @Nullable
    private String classifier;
    @Nullable
    private String extension;

    /**
     * Used on the client-side classpath.
     */
    private boolean clientClasspath;

    /**
     * Used on the client-side module-path.
     */
    private boolean clientModulePath;

    /**
     * Used by the installer for the client.
     */
    private boolean clientInstaller;

    /**
     * Used by the installer for the server.
     */
    private boolean serverInstaller;

    private String sha1Checksum;

    private Long size;

    private String url;

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

    public boolean isClientClasspath() {
        return clientClasspath;
    }

    public void setClientClasspath(boolean clientClasspath) {
        this.clientClasspath = clientClasspath;
    }

    public boolean isClientModulePath() {
        return clientModulePath;
    }

    public void setClientModulePath(boolean clientModulePath) {
        this.clientModulePath = clientModulePath;
    }

    public boolean isClientInstaller() {
        return clientInstaller;
    }

    public void setClientInstaller(boolean clientInstaller) {
        this.clientInstaller = clientInstaller;
    }

    public boolean isServerInstaller() {
        return serverInstaller;
    }

    public void setServerInstaller(boolean serverInstaller) {
        this.serverInstaller = serverInstaller;
    }

    public String getSha1Checksum() {
        return sha1Checksum;
    }

    public void setSha1Checksum(String sha1Checksum) {
        this.sha1Checksum = sha1Checksum;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMavenRepositoryPath() {
        var path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        if (classifier != null) {
            path += "-" + classifier;
        }
        if (extension != null) {
            path += "." + extension;
        }
        return path;
    }

    public static ReferencedLibrary of(String artifactId) {
        String extension = "jar";
        if (artifactId.contains("@")) {
            extension = artifactId.substring(artifactId.lastIndexOf('@') + 1);
            artifactId = artifactId.substring(0, artifactId.lastIndexOf('@'));
        }
        var parts = artifactId.split(":");
        var referencedLibrary = new ReferencedLibrary();
        referencedLibrary.setGroupId(parts[0]);
        referencedLibrary.setArtifactId(parts[1]);
        referencedLibrary.setVersion(parts[2]);
        if (parts.length > 3) {
            referencedLibrary.setClassifier(parts[3]);
        }
        referencedLibrary.setExtension(extension);
        return referencedLibrary;
    }

    public static List<ReferencedLibrary> of(MinecraftLibrary library) {
        var result = new ArrayList<ReferencedLibrary>();
        for (var classifierPair : iterateClassifiers(library)) {
            var referencedLib = of(library.artifactId());
            if (classifierPair.classifier != null) {
                referencedLib.setClassifier(classifierPair.classifier);
            }
            referencedLib.setSha1Checksum(classifierPair.download.checksum());
            referencedLib.setSize((long) classifierPair.download.size());
            referencedLib.setUrl(classifierPair.download.uri().toString());
            if (!referencedLib.getMavenRepositoryPath().equals(classifierPair.download.path())) {
                throw new IllegalStateException("Library in manifest specifies non-standard download path: " + classifierPair.download.path()
                        + ". Expected: " + referencedLib.getMavenRepositoryPath());
            }
            result.add(referencedLib);
        }
        return result;
    }

    private static List<ClassifierDownload> iterateClassifiers(MinecraftLibrary library) {
        var result = new ArrayList<ClassifierDownload>();

        var downloads = library.downloads();
        if (downloads.artifact() != null) {
            result.add(new ClassifierDownload(null, downloads.artifact()));
        }
        for (var entry : downloads.classifiers().entrySet()) {
            result.add(new ClassifierDownload(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    record ClassifierDownload(@Nullable String classifier, MinecraftDownload download) {
    }

    public String getMavenComponentIdString() {
        var result = groupId + ":" + artifactId + ":" + version;
        if (classifier != null) {
            result += ":" + classifier;
        }
        return result + "@" + extension;
    }

    @Override
    public String toString() {
        return getMavenComponentIdString();
    }
}
