package net.neoforged.meta.db;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "neoforge_version")
public class NeoForgeVersion extends SoftwareComponentVersion {
    @ManyToOne
    @Fetch(FetchMode.JOIN)
    private MinecraftVersion minecraftVersion;

    @Column(nullable = false)
    private String launcherProfileId;

    /**
     * The vanilla launcher profile stored directly in the database.
     * Automatically compressed using deflate.
     */
    @Column(nullable = false, columnDefinition = "BLOB")
    @Convert(converter = DeflateConverter.class)
    private String launcherProfile;

    /**
     * The legacyinstaller profile content stored directly in the database.
     * Automatically compressed using deflate.
     */
    @Column(nullable = false, columnDefinition = "BLOB")
    @Convert(converter = DeflateConverter.class)
    private String installerProfile;

    @ElementCollection
    @CollectionTable(name = "neoforge_version_libraries")
    private List<ReferencedLibrary> libraries = new ArrayList<>();

    @Embedded
    @AttributeOverride(name = "mainClass", column = @Column(name = "client_main_class"))
    @AssociationOverride(name = "jvmArgs", joinTable = @JoinTable(name = "neoforge_client_jvm_args"))
    @AssociationOverride(name = "programArgs", joinTable = @JoinTable(name = "neoforge_client_program_args"))
    private StartupArguments clientStartup = new StartupArguments();

    @Embedded
    @AttributeOverride(name = "mainClass", column = @Column(name = "server_main_class"))
    @AssociationOverride(name = "jvmArgs", joinTable = @JoinTable(name = "neoforge_server_jvm_args"))
    @AssociationOverride(name = "programArgs", joinTable = @JoinTable(name = "neoforge_server_program_args"))
    private StartupArguments serverStartup = new StartupArguments();

    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(MinecraftVersion minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getLauncherProfileId() {
        return launcherProfileId;
    }

    public void setLauncherProfileId(String launcherProfileId) {
        this.launcherProfileId = launcherProfileId;
    }

    public String getLauncherProfile() {
        return launcherProfile;
    }

    public void setLauncherProfile(String launcherProfile) {
        this.launcherProfile = launcherProfile;
    }

    public String getInstallerProfile() {
        return installerProfile;
    }

    public void setInstallerProfile(String installerProfile) {
        this.installerProfile = installerProfile;
    }

    public List<ReferencedLibrary> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<ReferencedLibrary> libraries) {
        this.libraries = libraries;
    }

    public StartupArguments getClientStartup() {
        return clientStartup;
    }

    public void setClientStartup(StartupArguments clientStartup) {
        this.clientStartup = clientStartup;
    }

    public StartupArguments getServerStartup() {
        return serverStartup;
    }

    public void setServerStartup(StartupArguments serverStartup) {
        this.serverStartup = serverStartup;
    }
}
