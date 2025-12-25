package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Member status enum with case-insensitive deserialization support
 */
public enum MemberStatus {
    ACTIVE,
    INVITED,
    SUSPENDED;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static MemberStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.toUpperCase());
    }
}
