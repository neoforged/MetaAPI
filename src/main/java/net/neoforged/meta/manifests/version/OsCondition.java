package net.neoforged.meta.manifests.version;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.neoforged.meta.util.OsUtil;

import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OsCondition(String name, String version, String arch) {
    public boolean nameMatches() {
        if (name == null) {
            return true;
        }
        return switch (name) {
            case "linux" -> OsUtil.isLinux();
            case "osx" -> OsUtil.isMac();
            case "windows" -> OsUtil.isWindows();
            default -> false;
        };
    }

    public boolean versionMatches() {
        return version == null || Pattern.compile(version).matcher(System.getProperty("os.version")).find();
    }

    public boolean archMatches() {
        return arch == null || Pattern.compile(arch).matcher(System.getProperty("os.arch")).find();
    }

    public boolean platformMatches() {
        return nameMatches() && versionMatches() && archMatches();
    }
}
