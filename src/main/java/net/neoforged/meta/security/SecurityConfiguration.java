package net.neoforged.meta.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfiguration {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    public SecurityConfiguration(ApiKeyAuthenticationProvider apiKeyAuthenticationProvider) {
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
    }

    /**
     * Security configuration for API endpoints - uses API key authentication
     * No session creation, no CSRF protection for API calls
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher(RequestMatchers.allOf(
                        PathPatternRequestMatcher.pathPattern("/v1/**"),
                        new RequestHeaderRequestMatcher(ApiKeyAuthenticationFilter.API_KEY_HEADER)
                ))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API usage does not require CSRF
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyAuthenticationProvider),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    /**
     * Security configuration for UI endpoints - uses OAuth2/OIDC authentication if configured
     * Cookie-based sessions with CSRF protection enabled
     */
    @Bean
    @Order(2)
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) {
        var csrfTokenHandler = new CsrfTokenRequestAttributeHandler();
        csrfTokenHandler.setCsrfRequestAttributeName("_csrf");

        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(withDefaults())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenHandler)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .build();
    }
}
