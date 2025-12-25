package com.comet.opik.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuthenticationConfig {

    /**
     * Authentication type enumeration
     */
    public enum AuthType {
        /**
         * Local authentication using database-stored credentials
         */
        LOCAL,
        /**
         * Remote authentication using external React service
         */
        REMOTE
    }

    public record UrlConfig(@Valid @JsonProperty @NotNull String url) {
    }

    @Valid @JsonProperty
    private boolean enabled;

    /**
     * Authentication type: LOCAL or REMOTE
     * Default: LOCAL (use local database authentication)
     */
    @Valid @JsonProperty
    private AuthType type = AuthType.LOCAL;

    @Valid @JsonProperty
    private int apiKeyResolutionCacheTTLInSec;

    @Valid @JsonProperty
    private UrlConfig reactService;
}
