package com.canda.epcis.api.inventory;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RebuildResponse {

    private final int eventsProcessed;
    private final int eventsSkipped;
    private final int errors;
    private final long durationMs;
    private final OffsetDateTime completedAt;
}
