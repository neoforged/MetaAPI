package net.neoforged.meta.web;

import net.neoforged.meta.db.MinecraftVersion;
import net.neoforged.meta.db.MinecraftVersionDao;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller("uiVersionsController")
public class VersionsController {

    private final MinecraftVersionDao minecraftVersionDao;

    public VersionsController(MinecraftVersionDao minecraftVersionDao) {
        this.minecraftVersionDao = minecraftVersionDao;
    }

    @GetMapping("/ui/minecraft-versions")
    @Transactional(readOnly = true)
    public String versions(Model model) {
        // Load recent Minecraft versions for display
        List<MinecraftVersion> minecraftVersions = minecraftVersionDao.findAll(Sort.by(Sort.Direction.DESC, "released"));
        model.addAttribute("minecraftVersions", minecraftVersions);

        return "minecraft-versions";
    }

    @GetMapping("/ui/minecraft-versions/version/{version}")
    @Transactional(readOnly = true)
    public String versionDetail(@PathVariable String version, Model model) {
        MinecraftVersion minecraftVersion = minecraftVersionDao.getByVersion(version);
        if (minecraftVersion == null) {
            return "error/404";
        }

        model.addAttribute("version", minecraftVersion);

        return "minecraft-version-detail";
    }
}
