package com.canda.epcis.api.capture;

import com.canda.epcis.application.capture.CaptureEventUseCase;
import com.canda.epcis.domain.model.CaptureResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EPCIS Capture API — empfängt EPCIS 1.2 XML Events, filtert EPCs und persistiert.
 *
 * POST /epcis/capture/events
 *   Content-Type: application/xml
 *   X-EPCIS-Source-ID: {sourceId}   ← Pflicht-Header
 *
 * Fehlerbehandlung delegiert vollständig an GlobalExceptionHandler:
 *   EpcisValidationException / EpcisParsingException → 400
 *   MissingRequestHeaderException                    → 400
 *   IllegalArgumentException                         → 400
 *   Exception                                        → 500
 */
@RestController
@RequestMapping("/epcis/capture")
public class CaptureController {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);

    private final CaptureEventUseCase captureEventUseCase;

    public CaptureController(CaptureEventUseCase captureEventUseCase) {
        this.captureEventUseCase = captureEventUseCase;
    }

    @PostMapping(
        value = "/events",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CaptureResponse> captureEvents(
            @RequestBody String xml,
            @RequestHeader("X-EPCIS-Source-ID") String sourceId) {

        log.info("POST /epcis/capture/events sourceId={}", sourceId);
        CaptureResult result = captureEventUseCase.capture(xml, sourceId);
        HttpStatus status = resolveStatus(result);
        return ResponseEntity.status(status).body(toResponse(result));
    }

    // ─────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────

    private HttpStatus resolveStatus(CaptureResult result) {
        if (result.getTotalAccepted() == 0 && result.getTotalReceived() > 0) {
            return HttpStatus.UNPROCESSABLE_ENTITY; // 422 — all events rejected
        }
        if (result.getTotalAccepted() < result.getTotalReceived()) {
            return HttpStatus.MULTI_STATUS; // 207 — partial success
        }
        return HttpStatus.CREATED; // 201 — all accepted
    }

    private CaptureResponse toResponse(CaptureResult result) {
        return CaptureResponse.builder()
                .sessionId(result.getSessionId())
                .totalReceived(result.getTotalReceived())
                .totalAccepted(result.getTotalAccepted())
                .totalFiltered(result.getTotalFiltered())
                .totalDropped(result.getTotalDropped())
                .captureIds(result.getCaptureIds())
                .errors(result.getErrors() == null || result.getErrors().isEmpty() ? null :
                        result.getErrors().stream()
                                .map(e -> CaptureResponse.ErrorDetail.builder()
                                        .eventId(e.getEventId())
                                        .errorCode(e.getErrorCode())
                                        .message(e.getMessage())
                                        .build())
                                .toList())
                .build();
    }
}
