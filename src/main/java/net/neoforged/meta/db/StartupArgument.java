package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StartupArgument {
    @Column(nullable = false)
    private String argument;
    @Column(nullable = false)
    private boolean windows;
    @Column(nullable = false)
    private boolean linux;
    @Column(nullable = false)
    private boolean mac;

    public static StartupArgument common(String arg) {
        var result = new StartupArgument();
        result.setWindows(true);
        result.setLinux(true);
        result.setMac(true);
        result.setArgument(arg);
        return result;
    }

    public String getArgument() {
        return argument;
    }

    public void setArgument(String argument) {
        this.argument = argument;
    }

    public boolean isWindows() {
        return windows;
    }

    public void setWindows(boolean windows) {
        this.windows = windows;
    }

    public boolean isLinux() {
        return linux;
    }

    public void setLinux(boolean linux) {
        this.linux = linux;
    }

    public boolean isMac() {
        return mac;
    }

    public void setMac(boolean mac) {
        this.mac = mac;
    }
}
