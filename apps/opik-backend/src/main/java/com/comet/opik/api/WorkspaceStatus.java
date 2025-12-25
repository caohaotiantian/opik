package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Workspace status enum with case-insensitive deserialization support
 */
public enum WorkspaceStatus {
    ACTIVE,
    SUSPENDED,
    DELETED;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static WorkspaceStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.toUpperCase());
    }
}
