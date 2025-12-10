package net.neoforged.meta.security;

import net.neoforged.meta.config.SecurityProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps OIDC groups claim to Spring Security roles.
 */
@Component
public class GroupsToRolesMapper implements GrantedAuthoritiesMapper {
    private final SecurityProperties securityProperties;

    public GroupsToRolesMapper(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> result = new HashSet<>(authorities);
        for (var authority : authorities) {
            if (authority instanceof OAuth2UserAuthority oAuthAuthority) {
                if (oAuthAuthority.getAttributes().get("groups") instanceof List<?> groupsList) {
                    for (Object group : groupsList) {
                        if (securityProperties.getUserGroups().contains(group.toString())) {
                            result.add(new SimpleGrantedAuthority("ROLE_user"));
                        }
                        if (securityProperties.getAdminGroups().contains(group.toString())) {
                            result.add(new SimpleGrantedAuthority("ROLE_admin"));
                        }
                    }
                }
            }
        }
        return result;
    }
}
