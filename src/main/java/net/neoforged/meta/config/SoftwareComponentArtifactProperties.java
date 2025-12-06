package net.neoforged.meta.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record SoftwareComponentArtifactProperties(@Nullable String classifier,
                                                  @DefaultValue("jar") @Nullable String extension,
                                                  @DefaultValue("false") boolean optional) {
}
