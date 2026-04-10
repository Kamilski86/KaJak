package com.example.epcis.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Response-DTO für GET /api/events
 * Gibt Metadaten + das konvertierte JSON-Payload zurück.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EpcisEventResponse {
    private final Long id;
    private final String eventType;
    private final OffsetDateTime eventTime;
    private final OffsetDateTime createdAt;
    private final Object payload; // parsed JSON, kein escaped String
}
