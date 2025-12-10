package net.neoforged.meta.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyAuthenticationProvider authenticationProvider;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isEmpty()) {
            var authentication = new ApiKeyAuthenticationToken(apiKey);
            var authenticated = authenticationProvider.authenticate(authentication);

            if (authenticated.isAuthenticated()) {
                SecurityContextHolder.getContext().setAuthentication(authenticated);
            }
        }

        filterChain.doFilter(request, response);
    }
}
