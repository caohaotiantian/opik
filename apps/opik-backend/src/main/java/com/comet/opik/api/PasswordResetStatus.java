package com.comet.opik.api;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Password reset token status enumeration
 */
public enum PasswordResetStatus {
    PENDING("pending"),
    USED("used"),
    EXPIRED("expired");

    private final String value;

    PasswordResetStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PasswordResetStatus fromValue(String value) {
        for (PasswordResetStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown password reset status: " + value);
    }
}
