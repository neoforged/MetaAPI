package net.neoforged.meta.ui;

import net.neoforged.meta.db.MinecraftVersionDao;
import net.neoforged.meta.maven.NeoForgeVersionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

@Controller("uiVersionsController")
public class MinecraftVersionsController {
    private final MinecraftVersionDao minecraftVersionDao;
    private final NeoForgeVersionService neoForgeVersionService;

    public MinecraftVersionsController(MinecraftVersionDao minecraftVersionDao, NeoForgeVersionService neoForgeVersionService) {
        this.minecraftVersionDao = minecraftVersionDao;
        this.neoForgeVersionService = neoForgeVersionService;
    }

    @GetMapping("/ui/minecraft-versions")
    @Transactional(readOnly = true)
    public String versions(Model model) {
        var minecraftVersions = minecraftVersionDao.findAll(Sort.by(Sort.Direction.DESC, "released"));
        model.addAttribute("minecraftVersions", minecraftVersions);
        var latestNeoForgeVersions = neoForgeVersionService.getLatestVersionByMinecraftVersion()
                // Remap to String -> NeoForge Version since Freemarker doesn't support non-string keys
                .entrySet().stream()
                .collect(Collectors.toMap(
                        v -> v.getKey().getVersion(),
                        Map.Entry::getValue
                ));
        model.addAttribute("latestNeoForgeVersions", latestNeoForgeVersions);

        return "minecraft-versions";
    }

    @GetMapping("/ui/minecraft-versions/version/{version}")
    @Transactional(readOnly = true)
    public String versionDetail(@PathVariable String version, Model model) {
        var minecraftVersion = minecraftVersionDao.getByVersion(version);
        if (minecraftVersion == null) {
            return "error/404";
        }

        model.addAttribute("version", minecraftVersion);

        return "minecraft-version-detail";
    }

    @PostMapping("/ui/minecraft-versions/version/{version}/reimport")
    @Transactional
    public String reimportVersion(@PathVariable String version, RedirectAttributes redirect) {
        minecraftVersionDao.setReimportForVersion(version);
        redirect.addFlashAttribute("successMessage", "Reimport has been flagged");
        return "redirect:/ui/minecraft-versions/version/{version}";
    }
}
