package net.neoforged.meta.web;

import net.neoforged.meta.config.MetaApiProperties;
import net.neoforged.meta.db.NeoForgeVersion;
import net.neoforged.meta.db.SoftwareComponentVersionDao;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class ComponentsController {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentsController.class);

    private static final Comparator<SoftwareComponentVersionDao.VersionSummary> MAVEN_VERSION_COMPARATOR = Comparator.comparing(vs -> new DefaultArtifactVersion(vs.version()));
    private static final Comparator<SoftwareComponentVersionDao.VersionSummary> RELEASED_COMPARATOR = Comparator.comparing(SoftwareComponentVersionDao.VersionSummary::released);

    private final MetaApiProperties apiProperties;
    private final SoftwareComponentVersionDao dao;

    public ComponentsController(MetaApiProperties apiProperties, SoftwareComponentVersionDao dao) {
        this.apiProperties = apiProperties;
        this.dao = dao;
    }

    @RequestMapping("/ui/components")
    public String getComponents(Model model) {

        var components = new ArrayList<ComponentModel>();
        for (var component : apiProperties.getComponents()) {
            var versionSummary = dao.getVersionSummary(component.getGroupId(), component.getArtifactId());
            if (versionSummary.isEmpty()) {
                // No version of this component are known.
                components.add(new ComponentModel(
                        component.getGroupId(),
                        component.getArtifactId(),
                        0,
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }

            // Find the highest version in Maven ordering and the last released version
            var latestByMavenOrder = versionSummary.stream().max(MAVEN_VERSION_COMPARATOR).get();
            var latestByReleaseTime = versionSummary.stream().max(RELEASED_COMPARATOR).get();
            // No version of this component are known.
            components.add(new ComponentModel(
                    component.getGroupId(),
                    component.getArtifactId(),
                    versionSummary.size(),
                    latestByMavenOrder.version(),
                    latestByMavenOrder.released(),
                    latestByReleaseTime.version(),
                    latestByReleaseTime.released()
            ));
        }
        model.addAttribute("components", components);

        return "components";
    }

    @RequestMapping("/ui/components/{groupId}/{artifactId}/versions")
    @Transactional(readOnly = true)
    public String getComponentVersions(@PathVariable("groupId") String groupId, @PathVariable("artifactId") String artifactId, Model model) {
        // This is necessary to validate that only known groupIds + artifactIds are valid URLs
        if (apiProperties.getComponents().stream().noneMatch(c -> c.getGroupId().equals(groupId) && c.getArtifactId().equals(artifactId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var versions = dao.findSummaryByGA(groupId, artifactId);
        model.addAttribute("groupId", groupId);
        model.addAttribute("artifactId", artifactId);
        model.addAttribute("versions", versions);

        return "component-versions";
    }

    @RequestMapping("/ui/components/{groupId}/{artifactId}/versions/{version}")
    @Transactional(readOnly = true)
    public String getComponentVersionDetail(@PathVariable("groupId") String groupId,
                                            @PathVariable("artifactId") String artifactId,
                                            @PathVariable("version") String versionId,
                                            Model model) {
        // This is necessary to validate that only known groupIds + artifactIds are valid URLs
        var component = apiProperties.getComponents().stream().filter(c -> c.getGroupId().equals(groupId) && c.getArtifactId().equals(artifactId)).findFirst().orElse(null);
        if (component == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var repository = apiProperties.getMavenRepositories().stream().filter(r -> r.getId().equals(component.getMavenRepositoryId())).findFirst().orElse(null);
        if (repository == null) {
            LOG.error("Configuration error: component {}:{} references unknown Maven repository {}", component.getGroupId(), component.getArtifactId(), component.getMavenRepositoryId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        var details = dao.findByGAV(groupId, artifactId, versionId);
        if (details == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("groupId", groupId);
        model.addAttribute("artifactId", artifactId);
        model.addAttribute("version", details.getVersion());
        model.addAttribute("details", details);
        model.addAttribute("warnings", List.copyOf(details.getWarnings()));
        model.addAttribute("artifacts", List.copyOf(details.getArtifacts()));
        model.addAttribute("repository", repository);
        if (details instanceof NeoForgeVersion neoForgeVersion) {
            model.addAttribute("neoForgeVersion", neoForgeVersion);
            model.addAttribute("neoForgeVersionLibraries", List.copyOf(neoForgeVersion.getLibraries()));
        }

        return "component-version-detail";
    }

}
