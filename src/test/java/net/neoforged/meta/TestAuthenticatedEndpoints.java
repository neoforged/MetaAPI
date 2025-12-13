package net.neoforged.meta;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
public class TestAuthenticatedEndpoints {

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @MethodSource("provideEndpoints")
    void testAllEndpointsAuthenticatedForApiConsumers(String method, String path) throws Exception {
        var restClient = RestClient.create();
        var baseUrl = "http://localhost:" + port;

        try (var response = restClient
                .method(HttpMethod.valueOf(method))
                .uri(baseUrl + path)
                .exchange((_, clientResponse) -> clientResponse)) {
            int statusCode = response.getStatusCode().value();
            assertEquals(401, statusCode,
                    "Expected 401 Unauthorized for " + method + " " + path + " but got " + statusCode);
        }
    }

    @ParameterizedTest
    @MethodSource("provideEndpoints")
    void testAllEndpointsAuthenticatedForBrowsers(String method, String path) throws Exception {
        var restClient = RestClient.create();
        var baseUrl = "http://localhost:" + port;

        try (var response = restClient
                .method(HttpMethod.valueOf(method))
                .uri(baseUrl + path)
                .header("Accept", "text/html")
                .exchange((_, clientResponse) -> clientResponse)) {
            int statusCode = response.getStatusCode().value();
            assertEquals(302, statusCode,
                    "Expected redirect for " + method + " " + path + " but got " + statusCode);
            String location = response.getHeaders().getFirst("Location");
            assertNotNull(location, "location");
            location = new UrlPathHelper().removeSemicolonContent(location); // sometimes ;jsessionid= is appended
            assertEquals(baseUrl + "/oauth2/authorization/dex", location);
        }
    }

    /**
     * Automatically discover all registered API endpoints from Spring.
     */
    static Stream<Arguments> provideEndpoints(
            @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> {
                    var mapping = entry.getKey();
                    var patterns = new HashSet<>(mapping.getPatternValues());
                    var httpMethods = mapping.getMethodsCondition().getMethods();

                    patterns.remove("/"); // The index page is white-listed to be accessible anonymously

                    // If no methods specified, default to GET
                    final var finalMethods = httpMethods.isEmpty()
                            ? Set.of(RequestMethod.GET)
                            : httpMethods;

                    return patterns.stream()
                            .flatMap(pattern -> finalMethods.stream()
                                    .map(requestMethod -> {
                                        // Replace path variables with dummy values
                                        var resolvedPath = pattern.replaceAll("\\{[^}]+}", "123");
                                        return Arguments.of(requestMethod.name(), resolvedPath);
                                    }));
                })
                .distinct();
    }
}
