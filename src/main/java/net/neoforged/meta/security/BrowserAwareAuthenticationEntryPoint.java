package net.neoforged.meta.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.IOException;

/**
 * Custom authentication entry point that redirects browsers to OAuth login
 * but returns 401 Unauthorized for non-browser clients (API clients).
 * <p>
 * Determines if a request is from a browser by checking if the Accept header
 * contains "text/html".
 */
public class BrowserAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final LoginUrlAuthenticationEntryPoint browserEntryPoint;

    public BrowserAwareAuthenticationEntryPoint(String loginFormUrl) {
        this.browserEntryPoint = new LoginUrlAuthenticationEntryPoint(loginFormUrl);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // Check if the request accepts HTML (indicates a browser)
        if (isBrowserDocumentRequest(request)) {
            // Redirect to OAuth login page for browsers
            browserEntryPoint.commence(request, response, authException);
        } else {
            // Return 401 Unauthorized for API clients
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        }
    }

    /**
     * We heuristically consider any request that explicitly asks for text/html without weighting a browser request.
     */
    private boolean isBrowserDocumentRequest(HttpServletRequest request) {
        var mediaTypes = MediaType.parseMediaTypes(request.getHeader("Accept"));
        return mediaTypes.contains(MediaType.TEXT_HTML);
    }
}
