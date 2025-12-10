package net.neoforged.meta.manifests.version;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MinecraftVersionManifest(String id, Map<String, MinecraftDownload> downloads,
                                       List<MinecraftLibrary> libraries,
                                       AssetIndexReference assetIndex, String assets, JavaVersionReference javaVersion,
                                       String mainClass, MinecraftArguments arguments,
                                       @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS", timezone = "UTC") Instant releaseTime) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(new SimpleModule().addDeserializer(Instant.class, new LenientInstantDeserializer()))
            .build();

    public static MinecraftVersionManifest from(Path path) {
        return MAPPER.readValue(path.toFile(), MinecraftVersionManifest.class);
    }

    public static MinecraftVersionManifest from(InputStream input) {
        return MAPPER.readValue(input, MinecraftVersionManifest.class);
    }

    public static MinecraftVersionManifest from(String jsonContent) {
        return MAPPER.readValue(jsonContent, MinecraftVersionManifest.class);
    }

    public byte[] toByteArray() {
        return MAPPER.writeValueAsBytes(this);
    }
}
