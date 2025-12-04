package net.neoforged.meta.manifests.version;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;

public record AssetIndex(Map<String, AssetObject> objects) {
    public static AssetIndex from(Path path) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(path.toFile(), AssetIndex.class);
    }
}
