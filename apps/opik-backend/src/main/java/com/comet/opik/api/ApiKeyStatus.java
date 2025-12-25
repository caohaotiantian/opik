package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * API Key status enum with case-insensitive deserialization support
 */
public enum ApiKeyStatus {
    ACTIVE,
    REVOKED;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static ApiKeyStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.toUpperCase());
    }
}
