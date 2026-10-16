package net.neoforged.meta.jobs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.neoforged.meta.util.HashingUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for creating a fake Minecraft launcher manifest server for testing.
 * <p>
 * This server can serve:
 * - The main launcher manifest (version_manifest_v2.json)
 * - Individual version manifests for each Minecraft version
 * <p>
 * Example usage:
 * <pre>
 * FakeLauncherManifestServer server = new FakeLauncherManifestServer();
 * server.addVersion("1.21.4", "release")
 *       .withSha1("abc123abc123abc123abc123abc123abc123abc1")
 *       .withReleaseTime("2024-06-13T12:00:00+00:00")
 *       .withManifest(21);  // Java 21
 * server.addVersion("24w50a", "snapshot")
 *       .withSha1("def456def456def456def456def456def456def4")
 *       .withReleaseTime("2024-12-11T15:30:00+00:00")
 *       .withManifest(21);  // Java 21
 *
 * // Use server.getManifestUrl() to get the launcher manifest URL
 * // Individual version manifests will be served at /manifests/{version-id}.json
 *
 * server.close();
 * </pre>
 */
public class FakeLauncherManifestServer implements AutoCloseable {

    private final HttpServer server;
    private final List<VersionEntry> versions = new ArrayList<>();
    private final Map<String, String> versionManifests = new HashMap<>();

    public FakeLauncherManifestServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create fake launcher manifest server.", e);
        }

        // Set up single root context that dispatches all requests
        server.createContext("/", new RootHandler());

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

    /**
     * Clear all configured versions.
     */
    public void clear() {
        this.versions.clear();
        this.versionManifests.clear();
    }

    /**
     * Get the URL for the launcher manifest.
     */
    public URI getManifestUrl() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/mc/game/version_manifest_v2.json");
    }

    /**
     * Get the base URL of this server.
     */
    public URI getBaseUrl() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    /**
     * Add a Minecraft version to the launcher manifest.
     *
     * @param id   Version ID (e.g., "1.21.4", "24w50a")
     * @param type Version type (e.g., "release", "snapshot", "old_beta")
     * @return A builder for configuring the version
     */
    public VersionBuilder addVersion(String id, String type) {
        VersionEntry entry = new VersionEntry(id, type);
        versions.add(entry);
        return new VersionBuilder(entry);
    }

    /**
     * Builder for version metadata.
     */
    public class VersionBuilder {
        private final VersionEntry entry;

        private VersionBuilder(VersionEntry entry) {
            this.entry = entry;
        }

        public VersionBuilder withSha1(String sha1) {
            entry.sha1 = sha1;
            return this;
        }

        public VersionBuilder withReleaseTime(OffsetDateTime releaseTime) {
            entry.releaseTime = releaseTime;
            return this;
        }

        public VersionBuilder withReleaseTime(String releaseTime) {
            entry.releaseTime = OffsetDateTime.parse(releaseTime);
            return this;
        }

        /**
         * Set the version manifest JSON content and configure the server to serve it.
         *
         * @param javaMajorVersion The Java major version required by this Minecraft version
         * @return This builder for chaining
         */
        public VersionBuilder withManifest(int javaMajorVersion) {
            // Build the manifest JSON. The job parses this and iterates over `libraries`,
            // so it must be present (even if empty) like in real Mojang manifests.
            String manifestJson = String.format("""
                            {
                                "id": "%s",
                                "type": "%s",
                                "javaVersion": {
                                    "majorVersion": %d
                                },
                                "libraries": []
                            }""",
                    entry.id,
                    entry.type,
                    javaMajorVersion
            );

            return withManifestRaw(manifestJson);
        }

        /**
         * Set the version manifest using raw JSON content.
         * Use this for advanced test scenarios where you need full control over the manifest structure.
         *
         * @param manifestJson The raw JSON content for the version manifest
         * @return This builder for chaining
         */
        public VersionBuilder withManifestRaw(String manifestJson) {
            // Generate a unique URL for this version's manifest
            String manifestPath = "/manifests/" + entry.id + ".json";
            entry.url = getBaseUrl().resolve(manifestPath);

            // Store the manifest content
            versionManifests.put(manifestPath, manifestJson);

            if (entry.sha1 == null) {
                entry.sha1 = HashingUtil.sha1(manifestJson);
            }

            return this;
        }
    }

    /**
     * Metadata for a single Minecraft version.
     */
    private static class VersionEntry {
        final String id;
        final String type;
        URI url;
        String sha1;
        OffsetDateTime releaseTime = OffsetDateTime.now();

        VersionEntry(String id, String type) {
            this.id = id;
            this.type = type;
        }

        String toJson() {
            return String.format("""
                            {
                                "id": "%s",
                                "type": "%s",
                                "url": "%s",
                                "sha1": "%s",
                                "releaseTime": "%s"
                            }""",
                    id,
                    type,
                    url,
                    sha1,
                    releaseTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            );
        }
    }

    /**
     * Root HTTP handler that dispatches all requests based on the path.
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Handle launcher manifest request
            if ("/mc/game/version_manifest_v2.json".equals(path)) {
                handleLauncherManifest(exchange);
                return;
            }

            // Handle individual version manifest requests
            if (versionManifests.containsKey(path)) {
                handleVersionManifest(exchange, versionManifests.get(path));
                return;
            }

            // Return 404 for unknown paths
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }

        private void handleLauncherManifest(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"versions\": [\n");

            for (int i = 0; i < versions.size(); i++) {
                json.append("    ").append(versions.get(i).toJson());
                if (i < versions.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("  ]\n");
            json.append("}\n");

            byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private void handleVersionManifest(HttpExchange exchange, String manifestJson) throws IOException {
            byte[] response = manifestJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
