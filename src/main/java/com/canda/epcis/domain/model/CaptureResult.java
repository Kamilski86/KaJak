package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Gesamtergebnis einer Capture-Session.
 * Wird als HTTP Response zurückgegeben und in capture_audit persistiert.
 */
@Getter
@Builder
public class CaptureResult {

    private final String sessionId;
    private final int totalReceived;
    private final int totalAccepted;
    /** Events bei denen EPCs gefiltert wurden, das Event selbst aber behalten wurde. */
    private final int totalFiltered;
    /** Events die komplett abgelehnt wurden. */
    private final int totalDropped;
    private final List<String> captureIds;
    private final List<CaptureError> errors;

    @Getter
    @Builder
    public static class CaptureError {
        private final String eventId;
        private final String errorCode;
        private final String message;
    }
}
