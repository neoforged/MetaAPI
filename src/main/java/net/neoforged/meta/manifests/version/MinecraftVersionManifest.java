package net.neoforged.meta.manifests.version;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record MinecraftVersionManifest(String id, Map<String, MinecraftDownload> downloads,
                                       List<MinecraftLibrary> libraries,
                                       AssetIndexReference assetIndex, String assets, JavaVersionReference javaVersion,
                                       String mainClass, MinecraftArguments arguments) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static MinecraftVersionManifest from(Path path) {
        return MAPPER.readValue(path.toFile(), MinecraftVersionManifest.class);
    }

    public static MinecraftVersionManifest from(String jsonContent) {
        return MAPPER.readValue(jsonContent, MinecraftVersionManifest.class);
    }
}
