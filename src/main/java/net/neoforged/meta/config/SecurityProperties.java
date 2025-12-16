package net.neoforged.meta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "meta-api.security")
public class SecurityProperties {
    private List<String> apiKeys = new ArrayList<>();

    /**
     * Groups reported in the OpenID token that have user access.
     */
    private Set<String> userGroups = new HashSet<>();

    /**
     * Groups reported in the OpenID token that have admin access.
     */
    private Set<String> adminGroups = new HashSet<>();

    /**
     * Allowed GitHub repositories for OIDC token authentication (format: owner/repo)
     */
    private Set<String> allowedRepositories = new HashSet<>();

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public Set<String> getUserGroups() {
        return userGroups;
    }

    public void setUserGroups(Set<String> userGroups) {
        this.userGroups = userGroups;
    }

    public Set<String> getAdminGroups() {
        return adminGroups;
    }

    public void setAdminGroups(Set<String> adminGroups) {
        this.adminGroups = adminGroups;
    }

    public Set<String> getAllowedRepositories() {
        return allowedRepositories;
    }

    public void setAllowedRepositories(Set<String> allowedRepositories) {
        this.allowedRepositories = allowedRepositories;
    }
}
