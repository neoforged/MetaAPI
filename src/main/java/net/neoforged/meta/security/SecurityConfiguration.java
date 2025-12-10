package net.neoforged.meta.security;

import net.neoforged.meta.config.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfiguration {

    private final SecurityProperties securityProperties;
    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;
    private final GroupsToRolesMapper groupsToRolesMapper;
    private final boolean oauth2Enabled;

    public SecurityConfiguration(
            SecurityProperties securityProperties,
            ApiKeyAuthenticationProvider apiKeyAuthenticationProvider,
            GroupsToRolesMapper groupsToRolesMapper,
            @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository) {
        this.securityProperties = securityProperties;
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
        this.groupsToRolesMapper = groupsToRolesMapper;
        this.oauth2Enabled = clientRegistrationRepository != null;
    }

    /**
     * Security configuration for API endpoints - uses API key authentication
     * No session creation, no CSRF protection for API calls
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/v1/**", "/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().permitAll() // API endpoints are public, but can be authenticated with API key
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // API usage does not require CSRF
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyAuthenticationProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
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
                        .requestMatchers("/", "/versions", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
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
