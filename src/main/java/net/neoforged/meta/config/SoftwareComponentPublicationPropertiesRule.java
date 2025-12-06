package net.neoforged.meta.config;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;

/**
 * Describes the properties of a single publication of a software component, and which version ranges of that
 * component the properties apply to. Since rules are evaluated in the order they're defined in, the version
 * ranges of multiple rules can overlap, and a publication of the component can exist without the artifacts
 * of a matching rule, if a rule of higher order matches it too.
 *
 * @param versionRanges The version ranges this rule applies to. If empty, it applies to all.
 * @param artifacts     The artifacts that are part of this type of publication.
 */
public record SoftwareComponentPublicationPropertiesRule(
        List<VersionRange> versionRanges,
        Set<SoftwareComponentArtifactProperties> artifacts
) {
    public SoftwareComponentPublicationPropertiesRule {
        if (versionRanges == null) {
            versionRanges = List.of();
        }
        if (artifacts == null) {
            artifacts = Set.of();
        }
    }

    public boolean matchesVersion(String version) {
        if (versionRanges.isEmpty()) {
            return true;
        }

        var artifactVersion = new DefaultArtifactVersion(version);
        for (var versionRange : versionRanges) {
            if (versionRange.containsVersion(artifactVersion)) {
                return true;
            }
        }
        return false;
    }
}
