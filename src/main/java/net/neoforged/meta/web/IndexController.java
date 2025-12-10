package net.neoforged.meta.web;

import net.neoforged.meta.db.BrokenSoftwareComponentVersionDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    private final BrokenSoftwareComponentVersionDao brokenSoftwareComponentVersionDao;

    public IndexController(BrokenSoftwareComponentVersionDao brokenSoftwareComponentVersionDao) {
        this.brokenSoftwareComponentVersionDao = brokenSoftwareComponentVersionDao;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("brokenVersionCount", brokenSoftwareComponentVersionDao.count());
        return "index";
    }
}
