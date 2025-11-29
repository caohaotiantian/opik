package com.comet.opik.infrastructure.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific API Key scopes for method execution
 *
 * This annotation is used to enforce API Key scope-based access control.
 *
 * Scope validation is only performed when API Key authentication is used.
 * Session-based authentication bypasses scope checks.
 *
 * Common scopes:
 * - "read": For GET operations
 * - "write": For POST/PUT operations
 * - "delete": For DELETE operations
 * - "*": Wildcard (full access)
 *
 * Example usage:
 * <pre>
 * {@code
 * @GET
 * @RequiresScope("read")
 * public Response getProject(@PathParam("id") String id) {
 *     // Implementation
 * }
 *
 * @POST
 * @RequiresScope("write")
 * public Response createProject(@Valid ProjectCreateRequest request) {
 *     // Implementation
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresScope {

    /**
     * Required scopes (any one is sufficient)
     *
     * @return array of required scopes
     */
    String[] value() default {};

    /**
     * Custom error message when scope check fails
     *
     * @return custom error message
     */
    String message() default "";

    /**
     * Whether to require all scopes (AND logic) instead of any scope (OR logic)
     *
     * @return true to require all scopes, false to require any scope
     */
    boolean requireAll() default false;
}
