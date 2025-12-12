package net.neoforged.meta.ui;

import net.neoforged.meta.maven.NeoForgeVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NeoForgeVersionsController {
    private final NeoForgeVersionService service;

    public NeoForgeVersionsController(NeoForgeVersionService service) {
        this.service = service;
    }

    @GetMapping("/ui/neoforge-versions")
    @Transactional(readOnly = true)
    public String versions(Model model) {
        model.addAttribute("versions", service.getVersions());
        return "neoforge-versions";
    }

    /**
     * Based on a NeoForge version this handler redirects to the appropriate component version detail page.
     * This indirection is needed since 1.20.1 uses a different artifact id than all other versions.
     */
    @GetMapping("/ui/neoforge-versions/version/{version}")
    @Transactional(readOnly = true)
    public String versionDetail(@PathVariable("version") String versionId, RedirectAttributes redirectAttributes) {
        var version = service.getVersion(versionId);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        redirectAttributes.addAttribute("groupId", version.getGroupId());
        redirectAttributes.addAttribute("artifactId", version.getArtifactId());
        redirectAttributes.addAttribute("version", version.getVersion());

        return "redirect:/ui/components/{groupId}/{artifactId}/versions/{version}";
    }
}
