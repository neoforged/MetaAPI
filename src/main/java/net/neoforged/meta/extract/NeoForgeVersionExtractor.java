package net.neoforged.meta.extract;

import net.neoforged.meta.db.ReferencedLibrary;
import net.neoforged.meta.manifests.installer.InstallerProfile;
import net.neoforged.meta.manifests.version.MinecraftVersionManifest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

public final class NeoForgeVersionExtractor {
    private static final Logger logger = LoggerFactory.getLogger(NeoForgeVersionExtractor.class);

    private NeoForgeVersionExtractor() {
    }

    public record Metadata(
            Instant releaseTime,
            String minecraftVersion,
            String launcherProfileId,
            String launcherProfile,
            String installerProfile,
            List<ReferencedLibrary> libraries,
            String serverArgsUnix,
            String serverArgsWindows
    ) {
    }

    public static Metadata extract(byte[] installerJarContent) {
        Path installer = null;
        try {
            installer = Files.createTempFile("installer", "zip");
            Files.write(installer, installerJarContent);

            String installerProfileText, versionManifestText;
            InstallerProfile installerProfile;
            MinecraftVersionManifest versionManifest;
            String serverUnixArgs, serverWindowsArgs;
            try (var zf = new ZipFile(installer.toFile())) {
                installerProfileText = readEntryAsString(zf, "install_profile.json");
                installerProfile = InstallerProfile.from(installerProfileText);
                if (installerProfile.getJson() == null) {
                    throw new IllegalStateException("Installer profile is missing key for version manifest.");
                }
                versionManifestText = readEntryAsString(zf, installerProfile.getJson());
                versionManifest = MinecraftVersionManifest.from(versionManifestText);

                // Get server startup command line arguments
                serverUnixArgs = readEntryAsString(zf, "data/unix_args.txt");
                serverWindowsArgs = readEntryAsString(zf, "data/win_args.txt");
            }

            // Collect all libraries that are used by processors, separated by side
            var clientProcessorGav = new HashSet<>();
            var serverProcessorGav = new HashSet<>();
            for (var processor : installerProfile.getProcessors()) {
                if (processor.isSide("client")) {
                    clientProcessorGav.add(processor.getJar());
                    clientProcessorGav.addAll(processor.getClasspath());
                }
                if (processor.isSide("server")) {
                    serverProcessorGav.add(processor.getJar());
                    serverProcessorGav.addAll(processor.getClasspath());
                }
            }

            // Collect libraries that are declared by the installer
            var libraries = new HashMap<String, ReferencedLibrary>();
            for (var library : installerProfile.getLibraries()) {
                for (var referencedLibrary : ReferencedLibrary.of(library)) {
                    referencedLibrary.setClientClasspath(true);
                    var previousLib = libraries.put(referencedLibrary.getMavenComponentIdString(), referencedLibrary);
                    if (previousLib != null) {
                        if (!previousLib.getSha1Checksum().equals(referencedLibrary.getSha1Checksum())) {
                            throw new IllegalStateException("Duped library with different checksums: " + referencedLibrary.getMavenComponentIdString());
                        }
                    }
                }
            }

            // Collect libraries that are declared by the game profile (classpath)
            for (var library : versionManifest.libraries()) {
                for (var referencedLibrary : ReferencedLibrary.of(library)) {
                    referencedLibrary = Objects.requireNonNullElse(libraries.putIfAbsent(referencedLibrary.getMavenComponentIdString(), referencedLibrary), referencedLibrary);
                    referencedLibrary.setClientClasspath(true);
                }
            }

            return new Metadata(
                    versionManifest.releaseTime(),
                    installerProfile.getMinecraft(),
                    versionManifest.id(),
                    versionManifestText,
                    installerProfileText,
                    new ArrayList<>(libraries.values()),
                    serverUnixArgs,
                    serverWindowsArgs
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (installer != null) {
                try {
                    Files.deleteIfExists(installer);
                } catch (IOException e) {
                    logger.error("Failed to delete temporary file {}", installer, e);
                }
            }
        }
    }

    private static String readEntryAsString(ZipFile zf, String name) throws IOException {
        var result = readEntryAsString(zf, name, null);
        if (result == null) {
            throw new IllegalStateException("Required entry " + name + " is missing.");
        }
        return result;
    }

    @Nullable
    private static String readEntryAsString(ZipFile zf, String name, @Nullable String defaultValue) throws IOException {
        // Strip leading slash
        if (name.startsWith("/")) {
            name = name.substring(1);
        }

        var installProfile = zf.getEntry(name);
        if (installProfile == null) {
            return defaultValue;
        }

        try (var input = zf.getInputStream(installProfile)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
