package com.canda.epcis.api.capture;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * HTTP Response für POST /epcis/capture/events.
 * Direkt aus CaptureResult gemappt.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptureResponse {

    private final String sessionId;
    private final int totalReceived;
    private final int totalAccepted;
    private final int totalFiltered;
    private final int totalDropped;
    private final List<String> captureIds;
    private final List<ErrorDetail> errors;

    @Getter
    @Builder
    public static class ErrorDetail {
        private final String eventId;
        private final String errorCode;
        private final String message;
    }
}
