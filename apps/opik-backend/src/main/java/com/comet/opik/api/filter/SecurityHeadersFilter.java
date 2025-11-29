package com.comet.opik.api.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * JAX-RS filter for adding security headers to all HTTP responses
 *
 * Security headers included:
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-XSS-Protection: Enables browser XSS protection
 * - Strict-Transport-Security: Enforces HTTPS
 * - Content-Security-Policy: Controls resource loading
 * - Referrer-Policy: Controls referrer information
 * - Permissions-Policy: Controls browser features
 *
 * These headers improve the security posture of the application by:
 * 1. Preventing common web attacks (XSS, clickjacking, MIME sniffing)
 * 2. Enforcing HTTPS usage
 * 3. Limiting browser features and third-party access
 */
@Slf4j
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
            ContainerResponseContext responseContext) {

        var headers = responseContext.getHeaders();

        // X-Frame-Options: Prevents the page from being framed (clickjacking protection)
        if (!headers.containsKey("X-Frame-Options")) {
            headers.add("X-Frame-Options", "DENY");
        }

        // X-Content-Type-Options: Prevents MIME type sniffing
        if (!headers.containsKey("X-Content-Type-Options")) {
            headers.add("X-Content-Type-Options", "nosniff");
        }

        // X-XSS-Protection: Enables browser XSS filter
        if (!headers.containsKey("X-XSS-Protection")) {
            headers.add("X-XSS-Protection", "1; mode=block");
        }

        // Strict-Transport-Security: Enforces HTTPS for 1 year
        // Note: Only add if using HTTPS
        if (requestContext.getSecurityContext().isSecure()) {
            if (!headers.containsKey("Strict-Transport-Security")) {
                headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }

        // Content-Security-Policy: Controls what resources can be loaded
        // This is a basic policy that can be customized based on needs
        if (!headers.containsKey("Content-Security-Policy")) {
            headers.add("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
        }

        // Referrer-Policy: Controls how much referrer information is sent
        if (!headers.containsKey("Referrer-Policy")) {
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        // Permissions-Policy: Disables unnecessary browser features
        if (!headers.containsKey("Permissions-Policy")) {
            headers.add("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        }

        log.debug("Security headers added to response for path: '{}'", requestContext.getUriInfo().getPath());
    }
}
