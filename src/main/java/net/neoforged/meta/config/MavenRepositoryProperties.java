package net.neoforged.meta.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Validated
public class MavenRepositoryProperties {
    @NotNull
    private String id;
    @NotNull
    private URI url;
    @NotNull
    private Map<String, String> headers = new HashMap<>();

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public URI getUrl() {
        return url;
    }
    public void setUrl(URI url) {
        this.url = url;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
