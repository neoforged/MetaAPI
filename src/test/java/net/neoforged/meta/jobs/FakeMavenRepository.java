package net.neoforged.meta.jobs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Helper class for creating a fake Maven repository HTTP server for testing.
 * Mocks the NeoForged Maven API (/api/maven/versions endpoint).
 * <p>
 * Example usage:
 * <pre>
 * FakeMavenRepository repo = new FakeMavenRepository();
 * repo.addArtifact("releases", "net.neoforged", "neoforge")
 *     .withVersion("21.3.0")
 *     .withVersion("21.3.1")
 *     .withVersion("21.3.2");
 *
 * // Use repo.getBaseUrl() to get the repository URL
 *
 * repo.stop();
 * </pre>
 */
public class FakeMavenRepository implements AutoCloseable {
    private static final String MAVEN_METADATA_XML = "maven-metadata.xml";
    private final HttpServer server;
    private final Map<String, ArtifactMetadata> artifacts = new HashMap<>();
    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));

    private static final Logger LOG = LoggerFactory.getLogger(FakeMavenRepository.class);

    public FakeMavenRepository() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create fake maven repository.", e);
        }
        var handler = new RequestHandler();
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                LOG.error("Uncaught Exception", e);
                var errorOut = new ByteArrayOutputStream();
                e.printStackTrace(new PrintWriter(errorOut));
                exchange.sendResponseHeaders(500, errorOut.toByteArray().length);
                exchange.getResponseBody().write(errorOut.toByteArray());
            } finally {
                exchange.close();
            }

        });
        start();
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        server.setExecutor(null); // Use default executor
        server.start();
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public void clear() {
        this.artifacts.clear();
    }

    /**
     * Get the base URL of this repository.
     */
    public URI getBaseUrl() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    /**
     * Add an artifact to this repository.
     *
     * @param repository Maven repository (e.g., "releases", "snapshots")
     * @param groupId    Maven group ID
     * @param artifactId Maven artifact ID
     * @return A builder for configuring the artifact metadata
     */
    public ArtifactMetadataBuilder addArtifact(String repository, String groupId, String artifactId) {
        String key = repository + ":" + groupId + ":" + artifactId;
        ArtifactMetadata metadata = new ArtifactMetadata(repository, groupId, artifactId);
        artifacts.put(key, metadata);

        return new ArtifactMetadataBuilder(metadata);
    }

    /**
     * Configure the server to return a 404 for a specific artifact.
     */
    public void addMissingArtifact(String repository, String groupId, String artifactId) {
        String key = repository + ":" + groupId + ":" + artifactId;
        artifacts.put(key, null); // null indicates 404
    }

    /**
     * Configure the server to return invalid JSON for a specific artifact.
     */
    public void addInvalidArtifact(String repository, String groupId, String artifactId) {
        String key = repository + ":" + groupId + ":" + artifactId;
        ArtifactMetadata metadata = new ArtifactMetadata(repository, groupId, artifactId);
        metadata.invalid = true;
        artifacts.put(key, metadata);
    }

    /**
     * Builder for artifact metadata.
     */
    public static class ArtifactMetadataBuilder {
        private final ArtifactMetadata metadata;

        private ArtifactMetadataBuilder(ArtifactMetadata metadata) {
            this.metadata = metadata;
        }

        public ArtifactMetadataBuilder withVersion(String version, Consumer<MavenVersionBuilder> builderConsumer) {
            Map<String, FileContent> files = new HashMap<>();
            var builder = new MavenVersionBuilder(metadata.groupId, metadata.artifactId, version, files);
            builderConsumer.accept(builder);
            metadata.versions.put(version, files);
            return this;
        }

        public ArtifactMetadataBuilder withSnapshot(boolean snapshot) {
            metadata.snapshot = snapshot;
            return this;
        }
    }

    record FileContent(byte[] content, Instant lastModified) {
    }

    /**
     * Metadata for a Maven artifact.
     */
    private static class ArtifactMetadata {
        final String repository;
        final String groupId;
        final String artifactId;
        final Map<String, Map<String, FileContent>> versions = new HashMap<>();
        boolean snapshot = false;
        boolean invalid = false;

        ArtifactMetadata(String repository, String groupId, String artifactId) {
            this.repository = repository;
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        public byte[] createMavenMetadataXml() {
            if (invalid) {
                return new byte[0];
            }

            try {
                var document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
                var rootEl = document.createElement("metadata");
                document.appendChild(rootEl);

                var rootGroupIdEl = document.createElement("groupId");
                rootGroupIdEl.setTextContent(groupId);
                rootEl.appendChild(rootGroupIdEl);
                var rootArtifactIdEl = document.createElement("artifactId");
                rootArtifactIdEl.setTextContent(artifactId);
                rootEl.appendChild(rootArtifactIdEl);

                var versioningEl = document.createElement("versioning");
                rootEl.appendChild(versioningEl);

                var versionsEl = document.createElement("versions");
                versioningEl.appendChild(versionsEl);

                for (var version : this.versions.keySet()) {
                    var versionEl = document.createElement("version");
                    versionEl.setTextContent(version);
                    versionsEl.appendChild(versionEl);
                }

                // Java XML APIs... Verbosity personified.
                var tf = TransformerFactory.newInstance();
                var transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

                var bos = new ByteArrayOutputStream();
                transformer.transform(new DOMSource(document), new StreamResult(bos));
                return bos.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * HTTP handler that routes requests to the appropriate artifact metadata.
     */
    private class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            var parts = new ArrayList<>(Arrays.asList(path.substring(1).split("/")));
            if (parts.size() < 4) {
                // at least repository/group/artifact/filename
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            var repositoryId = parts.removeFirst();
            var filename = parts.removeLast();

            if (MAVEN_METADATA_XML.equals(filename)) {
                var artifactId = parts.removeLast();
                var groupId = String.join(".", parts);

                String key = repositoryId + ":" + groupId + ":" + artifactId;
                ArtifactMetadata metadata = artifacts.get(key);

                if (metadata == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] response = metadata.createMavenMetadataXml();
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                return;
            }

            var version = parts.removeLast();
            var artifactId = parts.removeLast();
            var groupId = String.join(".", parts);

            String key = repositoryId + ":" + groupId + ":" + artifactId;
            ArtifactMetadata metadata = artifacts.get(key);

            if (metadata == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            var files = metadata.versions.get(version);
            var file = files.get(filename);

            if (file == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().set("Last-Modified", HTTP_DATE_FORMAT.format(file.lastModified));
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(file.content.length));
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
            } else if (exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(200, file.content.length);
                exchange.getResponseBody().write(file.content);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
