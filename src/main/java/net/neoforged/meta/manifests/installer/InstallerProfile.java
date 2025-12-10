package net.neoforged.meta.manifests.installer;

import net.neoforged.meta.manifests.version.MinecraftLibrary;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstallerProfile {
    private static final ObjectMapper mapper = new ObjectMapper();

    private String json;
    private String version;
    private String minecraft;
    // Extra libraries needed by processors, that may differ from the installer version's library list. Uses the same format as Mojang for simplicities sake.
    protected List<MinecraftLibrary> libraries = new ArrayList<>();
    // Executable jars to be run after all libraries have been downloaded.
    protected List<Processor> processors = new ArrayList<>();

    public static InstallerProfile from(String input) {
        return mapper.readValue(input, InstallerProfile.class);
    }

    public byte[] toByteArray() {
        return mapper.writeValueAsBytes(this);
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMinecraft() {
        return minecraft;
    }

    public void setMinecraft(String minecraft) {
        this.minecraft = minecraft;
    }

    public List<MinecraftLibrary> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<MinecraftLibrary> libraries) {
        this.libraries = libraries;
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public void setProcessors(List<Processor> processors) {
        this.processors = processors;
    }

    public static class Processor {
        // Which side this task is to be run on, Currently know sides are "client", "server" and "extract", if this omitted, assume all sides.
        private List<String> sides = new ArrayList<>();
        // The executable jar to run, The installer will run it in-process, but external tools can run it using java -jar {file}, so MANIFEST Main-Class entry must be valid.
        private String jar;
        // Dependency list of files needed for this jar to run. Anything listed here SHOULD be listed in {@see Install#libraries} so the installer knows to download it.
        private List<String> classpath = new ArrayList<>();
        /*
         * Arguments to pass to the jar, can be in the following formats:
         * [Artifact] : A artifact path in the target maven style repo, where all libraries are downloaded to.
         * {DATA_ENTRY} : A entry in the Install#data map, extract as a file, there are a few extra specified values to allow the same processor to run on both sides:
         *   {MINECRAFT_JAR} - The vanilla minecraft jar we are dealing with, /versions/VERSION/VERSION.jar on the client and /minecraft_server.VERSION.jar for the server
         *   {SIDE} - Either the exact string "client", "server", and "extract" depending on what side we are installing.
         */
        private List<String> args = new ArrayList<>();
        /*
         *  Files output from this task, used for verifying the process was successful, or if the task needs to be rerun.
         *  Keys are either a [Artifact] or {DATA_ENTRY}, if it is a {DATA_ENTRY} then that MUST be a [Artifact]
         *  Values are either a {DATA_ENTRY} or 'value', if it is a {DATA_ENTRY} then that entry MUST be a quoted string literal
         *    The end string literal is the sha1 hash of the specified artifact.
         */
        private Map<String, String> outputs = new HashMap<>();

        public boolean isSide(String side) {
            return sides.isEmpty() || sides.contains(side);
        }

        public List<String> getSides() {
            return sides;
        }

        public void setSides(List<String> sides) {
            this.sides = sides;
        }

        public String getJar() {
            return jar;
        }

        public void setJar(String jar) {
            this.jar = jar;
        }

        public List<String> getClasspath() {
            return classpath;
        }

        public void setClasspath(List<String> classpath) {
            this.classpath = classpath;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getOutputs() {
            return outputs;
        }

        public void setOutputs(Map<String, String> outputs) {
            this.outputs = outputs;
        }
    }
}
