package com.comet.opik.infrastructure.usagelimit;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.io.Serializable;

@Builder
public record Quota(int limit, int used, @NotNull QuotaType type) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum QuotaType implements Serializable {
        OPIK_SPAN_COUNT
    }
}
