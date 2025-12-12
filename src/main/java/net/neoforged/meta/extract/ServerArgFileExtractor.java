package net.neoforged.meta.extract;

import net.neoforged.meta.manifests.version.MinecraftArguments;
import net.neoforged.meta.manifests.version.OsCondition;
import net.neoforged.meta.manifests.version.Rule;
import net.neoforged.meta.manifests.version.RuleAction;
import net.neoforged.meta.manifests.version.UnresolvedArgument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ServerArgFileExtractor {
    private static final String CLASSPATH_SEPARATOR_PLACEHOLDER = "${classpath_separator}";
    private static final String LIBRARY_DIRECTORY_PLACEHOLDER = "${library_directory}";
    private static final List<Rule> UNIX_CONDITION = List.of(
            new Rule(RuleAction.ALLOWED, Map.of(), new OsCondition("linux", null, null)),
            new Rule(RuleAction.ALLOWED, Map.of(), new OsCondition("osx", null, null))
    );
    private static final List<Rule> WINDOWS_CONDITION = List.of(
            new Rule(RuleAction.ALLOWED, Map.of(), new OsCondition("windows", null, null))
    );

    // We consider something an option if it has the form -option-abc, --option-abc followed by a spaces
    private static final Pattern OPTION = Pattern.compile("^(-{1,2}[a-z-]*[a-z])\\s+(\\S.*)");

    // The first bare class-name option is considered to be the main class. This is just a heuristic,
    // but we've used a very small number of main classes in the past.
    private static final List<Pattern> MAIN_CLASS_PATTERNS = List.of(
            Pattern.compile(Pattern.quote("cpw.mods.bootstraplauncher.BootstrapLauncher")),
            Pattern.compile(Pattern.quote("net.neoforged.fml.startup.") + "[A-Z][a-zA-Z0-9]*")
    );

    private ServerArgFileExtractor() {
    }

    public static ServerStartupArgs consolidateServerArgs(String unixArgsFile, String windowsArgsFile) {
        var unixArgs = parseArgFile(unixArgsFile, ':');
        var windowsArgs = parseArgFile(windowsArgsFile, ';');

        if (!unixArgs.mainClass().equals(windowsArgs.mainClass())) {
            throw new IllegalArgumentException("Different main class on windows vs. unix: "
                    + windowsArgs.mainClass() + " != " + unixArgs.mainClass());
        }

        var jvmArgs = combineArgumentLists(unixArgs.jvmArgs, windowsArgs.jvmArgs);
        var programArgs = combineArgumentLists(unixArgs.programArgs, windowsArgs.programArgs);

        return new ServerStartupArgs(
                new MinecraftArguments(programArgs, jvmArgs),
                unixArgs.mainClass()
        );
    }

    public record ServerStartupArgs(
            MinecraftArguments arguments,
            String mainClass
    ) {
    }

    private static ArrayList<UnresolvedArgument> combineArgumentLists(List<String> unixArgs, List<String> windowsArgs) {
        var commonArgs = new ArrayList<>(unixArgs);
        commonArgs.retainAll(windowsArgs);

        var result = new ArrayList<UnresolvedArgument>();
        ListIterator<String> unixArgsIt = unixArgs.listIterator();
        ListIterator<String> windowsArgsIt = windowsArgs.listIterator();
        for (var commonArg : commonArgs) {
            addSpecificArgs(commonArg, unixArgsIt, result, UNIX_CONDITION);
            addSpecificArgs(commonArg, windowsArgsIt, result, WINDOWS_CONDITION);
            result.add(new UnresolvedArgument.Value(commonArg));
        }
        addSpecificArgs(null, unixArgsIt, result, UNIX_CONDITION);
        addSpecificArgs(null, windowsArgsIt, result, WINDOWS_CONDITION);
        return result;
    }

    private static void addSpecificArgs(String commonArg, ListIterator<String> platformArgs, List<UnresolvedArgument> result, List<Rule> condition) {
        var batch = new ArrayList<String>();
        while (platformArgs.hasNext()) {
            String arg = platformArgs.next();
            if (arg.equals(commonArg)) {
                break; // Matches the common arg, and will be added through the common arg
            }
            batch.add(arg);
        }
        if (!batch.isEmpty()) {
            result.add(new UnresolvedArgument.ConditionalValue(batch, condition));
        }
    }

    public static ArgumentList parseArgFile(String argFileContent, char pathSeparator) {
        var lines = new ArrayList<>(Arrays.stream(argFileContent.split("\n")).flatMap(s -> {
            // Arg-Files were inconsistent in that they did not contain single arg per line
            // Some lines are of the form "-option arg" where that has to be passed as two
            // separate, escaped arguments on the command line.
            var m = OPTION.matcher(s);
            if (m.matches()) {
                return Stream.of(m.group(1), m.group(2));
            } else {
                return Stream.of(s);
            }
        }).toList());

        String librariesDirectory = getAndReplaceLibrariesDirectory(lines);

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.startsWith("-DlegacyClassPath=")) {
                line = "-DlegacyClassPath=" + translateClassPathItem(line.substring("-DlegacyClassPath=".length()), pathSeparator, librariesDirectory);
            } else if (i > 0) {
                var previousArg = lines.get(i - 1);
                if ("-p".equals(previousArg) || "--module-path".equals(previousArg) || "-cp".equals(previousArg) || "-classpath".equals(previousArg)) {
                    line = translateClassPathItem(line, pathSeparator, librariesDirectory);
                }
            }

            lines.set(i, line);
        }

        // Find the main class
        int mainClassIndex = -1;
        outer:
        for (int i = 0; i < lines.size(); i++) {
            for (var mainClassPattern : MAIN_CLASS_PATTERNS) {
                if (mainClassPattern.matcher(lines.get(i)).matches()) {
                    mainClassIndex = i;
                    break outer;
                }
            }
        }

        if (mainClassIndex == -1) {
            throw new IllegalStateException("Failed to find main class in command line: " + lines);
        }

        List<String> jvmArgs = lines.subList(0, mainClassIndex);
        String mainClass = lines.get(mainClassIndex);
        List<String> programArgs = lines.subList(mainClassIndex + 1, lines.size());

        return new ArgumentList(jvmArgs, mainClass, programArgs);
    }

    public record ArgumentList(List<String> jvmArgs, String mainClass, List<String> programArgs) {
    }

    private static String translateClassPathItem(String line, char pathSeparator, String librariesDirectory) {
        // Auto-translate separators to a common placeholder, and replace the libraries directory with the placeholder
        line = Arrays.stream(line.split(Pattern.quote(String.valueOf(pathSeparator))))
                .map(item -> {
                    if (librariesDirectory != null && item.startsWith(librariesDirectory + "/")) {
                        return LIBRARY_DIRECTORY_PLACEHOLDER + item.substring(librariesDirectory.length());
                    } else {
                        return item;
                    }
                }).collect(Collectors.joining(CLASSPATH_SEPARATOR_PLACEHOLDER));
        return line;
    }

    private static @Nullable String getAndReplaceLibrariesDirectory(List<String> lines) {
        String librariesDirectory = null;
        // Find the argument setting the libraries directory
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.startsWith("-DlibraryDirectory=")) {
                librariesDirectory = line.substring("-DlibraryDirectory=".length());
                lines.set(i, "-DlibraryDirectory=" + LIBRARY_DIRECTORY_PLACEHOLDER);
            }
        }
        return librariesDirectory;
    }

}
