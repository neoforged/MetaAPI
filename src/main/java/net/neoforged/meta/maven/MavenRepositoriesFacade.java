package net.neoforged.meta.maven;

import net.neoforged.meta.config.MavenRepositoryProperties;
import net.neoforged.meta.config.MetaApiProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class MavenRepositoriesFacade {
    private static final Logger logger = LoggerFactory.getLogger(MavenRepositoriesFacade.class);

    private final Map<String, RestClient> restClients;
    private final tools.jackson.dataformat.xml.XmlMapper xmlMapper;

    public MavenRepositoriesFacade(MetaApiProperties properties) {
        this.xmlMapper = new XmlMapper();
        this.restClients = properties.getMavenRepositories().stream()
                .collect(Collectors.toMap(
                        MavenRepositoryProperties::getId,
                        mr -> {
                            var builder = RestClient.builder();
                            for (var entry : mr.getHeaders().entrySet()) {
                                builder.defaultHeader(entry.getKey(), entry.getValue());
                            }
                            builder.requestInterceptor((request, body, execution) -> {
                                long start = System.nanoTime();
                                var response = execution.execute(request, body);
                                long end = System.nanoTime();
                                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(end - start);
                                logger.info("{} {} -> {} in {}ms", request.getMethod(), request.getURI(), response.getStatusCode().value(), elapsedMillis);
                                return response;
                            });
                            String baseUrl = mr.getUrl().toString();
                            if (!baseUrl.endsWith("/")) {
                                baseUrl += "/";
                            }
                            return builder.baseUrl(baseUrl).build();
                        }
                ));
    }

    /**
     * Retrieve the list of versions for a Maven artifact from maven-metadata.xml.
     *
     * @param repositoryId The repository ID (must be configured in maven-repositories)
     * @param groupId      The Maven group ID (e.g., "net.neoforged")
     * @param artifactId   The Maven artifact ID (e.g., "neoforge")
     * @return List of version strings, or empty list if not found or on error
     */
    public List<String> listComponentVersions(String repositoryId, String groupId, String artifactId) {
        try {
            String metadataPath = getComponentPath(groupId, artifactId) + "/maven-metadata.xml";

            logger.debug("Fetching maven-metadata.xml from repository '{}' for {}:{} at path: {}",
                    repositoryId, groupId, artifactId, metadataPath);

            // Fetch maven-metadata.xml as String
            String xmlContent = getRestClient(repositoryId)
                    .get()
                    .uri(metadataPath)
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .body(String.class);

            if (xmlContent == null || xmlContent.isBlank()) {
                throw new RuntimeException("Empty or null maven-metadata.xml returned for " + groupId + ":" + artifactId + " in repository " + repositoryId);
            }

            var metadata = xmlMapper.readValue(xmlContent, MavenMetadata.class);
            if (metadata == null || metadata.getVersioning() == null) {
                throw new RuntimeException("No metadata found for " + groupId + ":" + artifactId + " in repository " + repositoryId);
            }

            List<String> versions = metadata.getVersioning().getVersions();
            if (versions == null) {
                throw new RuntimeException("No versions found for " + groupId + ":" + artifactId + " in repository " + repositoryId);
            }

            logger.info("Found {} versions for {}:{} in repository '{}'", versions.size(), groupId, artifactId, repositoryId);
            return versions;

        } catch (RestClientException | JacksonException e) {
            throw new RuntimeException("Error fetching versions for " + groupId + ":" + artifactId + " from repository " + repositoryId + ": " + e, e);
        }
    }

    public HttpHeaders headArtifact(String repositoryId, String groupId, String artifactId, String version, @Nullable String classifier, @Nullable String extension) {
        var result = headOptionalArtifact(repositoryId, groupId, artifactId, version, classifier, extension);
        if (result == null) {
            throw new IllegalStateException("Required artifact is missing.");
        }
        return result;
    }

    @Nullable
    public HttpHeaders headOptionalArtifact(String repositoryId, String groupId, String artifactId, String version, @Nullable String classifier, @Nullable String extension) {
        String url = getArtifactPath(repositoryId, groupId, artifactId, version, classifier, extension);
        var request = getRestClient(repositoryId)
                .head()
                .uri(url)
                // Workaround for https://github.com/spring-projects/spring-framework/issues/35966
                .header("Accept-Encoding", "")
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::isSameCodeAs, (_, _) -> {
                });

        try {
            var entity = request.toBodilessEntity();

            if (entity.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return null;
            }

            return entity.getHeaders();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve headers from " + url + " for repository " + repositoryId, e);
        }
    }

    public byte[] getArtifact(String repositoryId, String groupId, String artifactId, String version, @Nullable String classifier, @Nullable String extension) {
        String url = getArtifactPath(repositoryId, groupId, artifactId, version, classifier, extension);

        try {
            return getRestClient(repositoryId).get().uri(url).retrieve().body(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve headers from " + url + " for repository " + repositoryId, e);
        }
    }

    @Nullable
    public byte[] getOptionalArtifact(String repositoryId, String groupId, String artifactId, String version, @Nullable String classifier, @Nullable String extension) {
        String url = getArtifactPath(repositoryId, groupId, artifactId, version, classifier, extension);

        try {
            return getRestClient(repositoryId).get().uri(url).retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::isSameCodeAs, (_, _) -> {
                    })
                    .body(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve headers from " + url + " for repository " + repositoryId, e);
        }
    }

    private RestClient getRestClient(String repositoryId) {
        return Objects.requireNonNull(restClients.get(repositoryId), () -> "No repository is configured for id '" + repositoryId + "'");
    }

    private static String getComponentPath(String groupId, String artifactId) {
        return Stream.concat(
                        Arrays.stream(groupId.split("\\.")),
                        Stream.of(artifactId)
                ).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));

    }

    public String getArtifactPath(String repositoryId, String groupId, String artifactId, String version, @Nullable String classifier, @Nullable String extension) {
        // repositoryId is unused by we still ask for it to allow for repository-specific layouts later.
        var encodedVersion = URLEncoder.encode(version, StandardCharsets.UTF_8);
        var path = getComponentPath(groupId, artifactId) + "/" + encodedVersion + "/" + URLEncoder.encode(artifactId, StandardCharsets.UTF_8) + "-" + encodedVersion;
        if (classifier != null) {
            path += "-" + URLEncoder.encode(classifier, StandardCharsets.UTF_8);
        }
        if (extension == null) {
            path += ".jar";
        } else {
            path += "." + URLEncoder.encode(extension, StandardCharsets.UTF_8);
        }
        return path;
    }
}
