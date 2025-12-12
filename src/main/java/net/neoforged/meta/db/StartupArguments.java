package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OrderColumn;

import java.util.ArrayList;
import java.util.List;

@Embeddable
public class StartupArguments {
    @ElementCollection
    @OrderColumn
    private List<StartupArgument> jvmArgs = new ArrayList<>();

    @ElementCollection
    @OrderColumn
    private List<StartupArgument> programArgs = new ArrayList<>();

    @Column(nullable = false)
    private String mainClass;

    public List<StartupArgument> getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(List<StartupArgument> jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public List<StartupArgument> getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(List<StartupArgument> programArgs) {
        this.programArgs = programArgs;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }
}
