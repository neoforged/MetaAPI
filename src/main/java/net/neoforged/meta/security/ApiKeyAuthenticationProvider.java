package net.neoforged.meta.security;

import net.neoforged.meta.config.SecurityProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {
    private final SecurityProperties securityProperties;

    public ApiKeyAuthenticationProvider(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String apiKey = (String) authentication.getCredentials();

        if (securityProperties.getApiKeys().contains(apiKey)) {
            return new ApiKeyAuthenticationToken(
                apiKey,
                List.of(new SimpleGrantedAuthority("ROLE_API"))
            );
        }

        return authentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
