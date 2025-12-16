package net.neoforged.meta.security;

import net.neoforged.meta.config.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfiguration {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;
    private final SecurityProperties securityProperties;

    public SecurityConfiguration(ApiKeyAuthenticationProvider apiKeyAuthenticationProvider, SecurityProperties securityProperties) {
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
        this.securityProperties = securityProperties;
    }

    /**
     * Security configuration for API endpoints - uses API key authentication or OIDC token authentication
     * No session creation, no CSRF protection for API calls
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher(RequestMatchers.allOf(
                        PathPatternRequestMatcher.pathPattern("/v1/**"),
                        RequestMatchers.anyOf(
                                new RequestHeaderRequestMatcher(ApiKeyAuthenticationFilter.API_KEY_HEADER),
                                new RequestHeaderRequestMatcher("Authorization")
                        )
                ))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API usage does not require CSRF
                .csrf(csrf -> csrf.disable())
                // Support JWT tokens from GitHub Actions OIDC
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
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

        // Configure request cache to exclude API requests
        var requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(BrowserAwareAuthenticationEntryPoint.BROWSER_REQUEST_MATCHER);

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
                .requestCache(cache -> cache.requestCache(requestCache))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new BrowserAwareAuthenticationEntryPoint("/oauth2/authorization/dex"))
                )
                .build();
    }

    /**
     * JWT decoder for validating GitHub Actions OIDC tokens
     * GitHub Actions OIDC issuer: https://token.actions.githubusercontent.com
     * Required audience: neoforge-meta-api
     * Validates repository claim against allowed repositories
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        var githubActionsOidcIssuer = "https://token.actions.githubusercontent.com";
        var jwtDecoder = NimbusJwtDecoder.withJwkSetUri(githubActionsOidcIssuer + "/.well-known/jwks").build();

        var audienceValidator = new JwtClaimValidator<>("aud", "neoforge-meta-api"::equals);
        var repositoryValidator = new JwtClaimValidator<String>("repository",
            repository -> securityProperties.getAllowedRepositories().isEmpty() ||
                         securityProperties.getAllowedRepositories().contains(repository));

        var withIssuer = JwtValidators.createDefaultWithIssuer(githubActionsOidcIssuer);
        var withAudienceAndRepository = new DelegatingOAuth2TokenValidator<>(
            withIssuer,
            audienceValidator,
            repositoryValidator
        );

        jwtDecoder.setJwtValidator(withAudienceAndRepository);
        return jwtDecoder;
    }
}
