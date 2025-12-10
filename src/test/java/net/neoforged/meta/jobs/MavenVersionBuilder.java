package net.neoforged.meta.jobs;

import net.neoforged.meta.manifests.installer.InstallerProfile;
import net.neoforged.meta.manifests.version.AssetIndexReference;
import net.neoforged.meta.manifests.version.JavaVersionReference;
import net.neoforged.meta.manifests.version.MinecraftVersionManifest;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class MavenVersionBuilder {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final Map<String, FakeMavenRepository.FileContent> files;

    public MavenVersionBuilder(String groupId, String artifactId, String version, Map<String, FakeMavenRepository.FileContent> files) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.files = files;
    }

    public MavenVersionBuilder neoForgePublication() {
        mavenPom();

        artifact("universal", "jar", new byte[0], Instant.now());
        artifact("sources", "jar", new byte[0], Instant.now());
        artifact("userdev", "jar", new byte[0], Instant.now());
        artifact(null, "module", new byte[0], Instant.now());

        var installerJarContent = new ByteArrayOutputStream();
        try (var installerJar = new JarOutputStream(installerJarContent)) {
            var installerProfile = new InstallerProfile();
            installerProfile.setVersion(version);
            installerProfile.setMinecraft("1.0.0");
            installerProfile.setJson("version.json");
            installerJar.putNextEntry(new JarEntry("install_profile.json"));
            installerJar.write(installerProfile.toByteArray());
            installerJar.closeEntry();

            var versionManifest = new MinecraftVersionManifest(
                    "neoforge-" + version,
                    Map.of(),
                    List.of(),
                    null,
                    null,
                    new JavaVersionReference(21),
                    null,
                    null,
                    Instant.now()
            );

            installerJar.putNextEntry(new JarEntry(installerProfile.getJson()));
            installerJar.write(versionManifest.toByteArray());
            installerJar.closeEntry();

            installerJar.putNextEntry(new JarEntry("data/unix_args.txt"));
            installerJar.closeEntry();
            installerJar.putNextEntry(new JarEntry("data/win_args.txt"));
            installerJar.closeEntry();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        artifact("installer", "jar", installerJarContent.toByteArray(), Instant.now());

        return this;
    }

    public MavenVersionBuilder file(String filename, byte[] content, Instant lastModified) {
        files.put(filename, new FakeMavenRepository.FileContent(content, lastModified));
        return this;
    }

    public MavenVersionBuilder artifact(@Nullable String classifier, String extension, byte[] content, Instant lastModified) {
        String filename = artifactId + "-" + version + (classifier != null ? ("-" + classifier) : "") + "." + extension;
        return file(filename, content, lastModified)
                .file(filename + ".md5", hashContent(content, "MD5"), lastModified)
                .file(filename + ".sha1", hashContent(content, "SHA1"), lastModified)
                .file(filename + ".sha256", hashContent(content, "SHA256"), lastModified)
                .file(filename + ".sha512", hashContent(content, "SHA512"), lastModified);
    }

    public MavenVersionBuilder mavenPom() {
        return artifact(null, "pom", "<project>".getBytes(), Instant.now());
    }

    private byte[] hashContent(byte[] content, String algorithm) {
        try {
            var digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(content)).getBytes();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
