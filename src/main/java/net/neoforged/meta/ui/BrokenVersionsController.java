package net.neoforged.meta.ui;

import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class BrokenVersionsController {
    private final BrokenSoftwareComponentVersionDao dao;

    public BrokenVersionsController(BrokenSoftwareComponentVersionDao dao) {
        this.dao = dao;
    }

    @GetMapping("/ui/broken-versions")
    @Transactional(readOnly = true)
    public String brokenVersions(Model model) {
        var versions = dao.findAll(Sort.by(Sort.Direction.DESC, "lastAttempt"));
        model.addAttribute("versions", versions);

        return "broken-versions";
    }

    @PostMapping(value = "/ui/broken-versions", params = "action=retry-all")
    @Transactional
    public String retryBrokenVersion(RedirectAttributes redirectAttributes) {
        var count = dao.retryAll();
        redirectAttributes.addFlashAttribute("successMessage", "Successfully flagged " + count + " versions for retry.");
        return "redirect:/ui/broken-versions";
    }

    @GetMapping("/ui/broken-versions/version/{groupId}/{artifactId}/{version}")
    @Transactional(readOnly = true)
    public String brokenVersion(@PathVariable("groupId") String groupId,
                                @PathVariable("artifactId") String artifactId,
                                @PathVariable("version") String versionId,
                                Model model) {
        var version = dao.findByGAV(groupId, artifactId, versionId);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("version", version);
        model.addAttribute("errors", List.copyOf(version.getErrors()));

        return "broken-version";
    }

    @PostMapping(value = "/ui/broken-versions/version/{groupId}/{artifactId}/{version}", params = "action=retry")
    @Transactional
    public String retryBrokenVersion(@PathVariable("groupId") String groupId,
                                     @PathVariable("artifactId") String artifactId,
                                     @PathVariable("version") String versionId,
                                     RedirectAttributes redirectAttributes) {
        var version = dao.findByGAV(groupId, artifactId, versionId);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        version.setRetry(true);

        redirectAttributes.addAttribute("groupId", version.getGroupId());
        redirectAttributes.addAttribute("artifactId", version.getArtifactId());
        redirectAttributes.addAttribute("version", version.getVersionString());
        redirectAttributes.addFlashAttribute("successMessage", "Successfully flagged for retry.");

        return "redirect:/ui/broken-versions/version/{groupId}/{artifactId}/{version}";
    }
}
