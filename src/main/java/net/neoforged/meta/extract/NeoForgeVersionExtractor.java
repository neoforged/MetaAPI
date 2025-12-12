package net.neoforged.meta.extract;

import net.neoforged.meta.db.DiscoveryLogMessage;
import net.neoforged.meta.db.ReferencedLibrary;
import net.neoforged.meta.db.StartupArgument;
import net.neoforged.meta.db.StartupArguments;
import net.neoforged.meta.manifests.installer.InstallerProfile;
import net.neoforged.meta.manifests.version.MinecraftVersionManifest;
import net.neoforged.meta.manifests.version.Rule;
import net.neoforged.meta.manifests.version.UnresolvedArgument;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public final class NeoForgeVersionExtractor {
    private static final Logger logger = LoggerFactory.getLogger(NeoForgeVersionExtractor.class);
    static final String CLASSPATH_SEPARATOR = "${classpath_separator}";

    private NeoForgeVersionExtractor() {
    }

    public record Metadata(
            Instant releaseTime,
            String minecraftVersion,
            String launcherProfileId,
            String launcherProfile,
            String installerProfile,
            List<ReferencedLibrary> libraries,
            StartupArguments clientStartup,
            StartupArguments serverStartup,
            List<DiscoveryLogMessage> warnings
    ) {
    }

    public static Metadata extract(byte[] installerJarContent) {
        Path installer = null;
        try {
            installer = Files.createTempFile("installer", "zip");
            Files.write(installer, installerJarContent);

            List<DiscoveryLogMessage> warnings = new ArrayList<>();
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

            // Consolidate unix+windows server arguments
            var clientStartup = new StartupArguments();
            clientStartup.setJvmArgs(convert(versionManifest.arguments().jvm()));
            clientStartup.setProgramArgs(convert(versionManifest.arguments().game()));
            clientStartup.setMainClass(versionManifest.mainClass());
            var serverArgFiles = ServerArgFileExtractor.consolidateServerArgs(serverUnixArgs, serverWindowsArgs);
            var serverStartup = new StartupArguments();
            serverStartup.setJvmArgs(convert(serverArgFiles.arguments().jvm()));
            serverStartup.setProgramArgs(convert(serverArgFiles.arguments().game()));
            serverStartup.setMainClass(serverArgFiles.mainClass());

            // Collect libraries that are declared by the game profile (classpath)
            var libraries = new HashMap<String, ReferencedLibrary>();
            var librariesByPath = new HashMap<String, ReferencedLibrary>();
            for (var library : versionManifest.libraries()) {
                for (var referencedLibrary : ReferencedLibrary.of(library)) {
                    libraries.put(referencedLibrary.getMavenComponentIdString(), referencedLibrary);
                    referencedLibrary.setClientClasspath(true);
                    librariesByPath.put(referencedLibrary.getMavenRepositoryPath(), referencedLibrary);
                }
            }

            // Find libraries referenced in the module path, which was used on Minecraft before 1.21.10
            var jvmArgsInOrder = versionManifest.arguments().jvm().stream().flatMap(NeoForgeVersionExtractor::streamPotentialValues).toList();
            for (int i = 0; i < jvmArgsInOrder.size() - 1; i++) {
                var arg = jvmArgsInOrder.get(i);
                if ("-p".equals(arg) || "--module-path".equals(arg)) {
                    var modulePath = jvmArgsInOrder.get(i + 1);
                    var modulePathItems = modulePath.split(Pattern.quote(CLASSPATH_SEPARATOR));
                    for (var modulePathItem : modulePathItems) {
                        if (modulePathItem.startsWith("${library_directory}/")) {
                            var relativePath = modulePathItem.substring("${library_directory}/".length());
                            var referencedLib = librariesByPath.get(relativePath);
                            if (referencedLib == null) {
                                throw new IllegalStateException("Module path references path not in library list: " + relativePath);
                            }
                            referencedLib.setClientModulePath(true);
                        }
                    }
                }
            }

            // Collect libraries that are declared by the installer
            for (var library : installerProfile.getLibraries()) {
                for (var referencedLibrary : ReferencedLibrary.of(library)) {
                    var previousLib = libraries.putIfAbsent(referencedLibrary.getMavenComponentIdString(), referencedLibrary);
                    if (previousLib != null) {
                        if (!previousLib.getSha1Checksum().equals(referencedLibrary.getSha1Checksum())) {
                            throw new IllegalStateException("Duped library with different checksums: " + referencedLibrary.getMavenComponentIdString());
                        }
                        referencedLibrary = previousLib;
                    }

                    referencedLibrary.setClientInstaller(clientProcessorGav.contains(referencedLibrary.getMavenComponentIdString()));
                    referencedLibrary.setServerInstaller(serverProcessorGav.contains(referencedLibrary.getMavenComponentIdString()));
                }
            }

            return new Metadata(
                    versionManifest.releaseTime(),
                    installerProfile.getMinecraft(),
                    versionManifest.id(),
                    versionManifestText,
                    installerProfileText,
                    new ArrayList<>(libraries.values()),
                    clientStartup,
                    serverStartup,
                    warnings
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

    private static List<StartupArgument> convert(List<UnresolvedArgument> arguments) {
        var result = new ArrayList<StartupArgument>();
        for (var argument : arguments) {
            switch (argument) {
                case UnresolvedArgument.ConditionalValue _ -> throw new IllegalStateException("(Neo)Forge never had conditional args");
                case UnresolvedArgument.Value value -> result.add(StartupArgument.common(value.value()));
            }
        }
        return result;
    }

    private static Stream<String> streamPotentialValues(UnresolvedArgument argument) {
        return switch (argument) {
            case UnresolvedArgument.ConditionalValue conditionalValue -> conditionalValue.value().stream();
            case UnresolvedArgument.Value value -> Stream.of(value.value());
        };
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
