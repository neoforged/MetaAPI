package net.neoforged.meta.util;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OsType {
    WINDOWS("windows"),
    MAC("osx"),
    LINUX("linux");

    private final String serializedName;

    OsType(String serializedName) {
        this.serializedName = serializedName;
    }

    @JsonValue
    public String getSerializedName() {
        return serializedName;
    }

    public static OsType current() {
        if (OsUtil.isWindows()) {
            return WINDOWS;
        } else if (OsUtil.isMac()) {
            return MAC;
        } else if (OsUtil.isLinux()) {
            return LINUX;
        }
        throw new UnsupportedOperationException("Unknown OS: " + System.getProperty("os.name"));
    }
}
