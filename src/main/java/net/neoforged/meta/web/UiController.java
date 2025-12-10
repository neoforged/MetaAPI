package net.neoforged.meta.web;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice(basePackageClasses = {UiController.class})
public class UiController {

    @ModelAttribute
    public void getUserData(Model model, Authentication authentication) {
        // Add user information if authenticated
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("name", oAuth2User.getAttribute("name"));
            userInfo.put("principal", oAuth2User.getName());

            model.addAttribute("user", userInfo);

            // Add roles (without ROLE_ prefix)
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .map(auth -> auth.substring("ROLE_".length()))
                    .toList();
            model.addAttribute("roles", roles);
        }
    }

}
