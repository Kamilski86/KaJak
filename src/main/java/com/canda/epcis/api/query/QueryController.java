package com.canda.epcis.api.query;

import com.canda.epcis.application.query.QueryEventUseCase;
import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * EPCIS 2.0 REST Binding — Query Interface.
 *
 * GET /epcis/query/events               — SimpleEventQuery (EPCIS 2.0 Parameternamen)
 * GET /epcis/query/events/{eventID}     — Einzelnes Event per EPCIS 2.0 eventID
 *
 * Parameter-Namenskonvention (EPCIS 2.0 REST Binding):
 *   EQ_*     = equals
 *   GE_*     = greater or equal
 *   LT_*     = less than
 *   MATCH_*  = matches (sucht in Listen)
 *
 * Hinweis zu {eventID} als @PathVariable:
 *   EPCIS 2.0 definiert GET /events/{eventID} als kanonischen Pfad.
 *   Clients müssen die eventID URL-encoden (Doppelpunkte: %3A, Slashes: %2F).
 *   Für urn:uuid:... IDs (kein Slash im NSS) reicht %3A-Encoding — kein Tomcat-Fix nötig.
 *   eventIDs mit Slashes im NSS erfordern zusätzliche Tomcat-Konfiguration (Phase 2+).
 *
 * Fehlerbehandlung delegiert an GlobalExceptionHandler:
 *   EpcisQueryException      → 404
 *   IllegalArgumentException → 400 (ungültige orderBy/orderDirection)
 *   Exception                → 500
 */
@RestController
@RequestMapping("/epcis/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryEventUseCase queryEventUseCase;
    private final ObjectMapper objectMapper;

    public QueryController(QueryEventUseCase queryEventUseCase, ObjectMapper objectMapper) {
        this.queryEventUseCase = queryEventUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * EPCIS 2.0 SimpleEventQuery.
     * Alle Parameter optional, kombinierbar.
     *
     * Beispiel:
     * GET /epcis/query/events?EQ_eventType=ObjectEvent&MATCH_epc=urn:epc:id:sgtin:...&GE_eventTime=2024-01-01T00:00:00Z
     */
    @GetMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QueryResponse> queryEvents(
            @RequestParam(name = "EQ_eventType",    required = false) String eqEventType,
            @RequestParam(name = "EQ_action",       required = false) String eqAction,
            @RequestParam(name = "EQ_bizStep",      required = false) String eqBizStep,
            @RequestParam(name = "EQ_disposition",  required = false) String eqDisposition,
            @RequestParam(name = "EQ_readPoint",    required = false) String eqReadPoint,
            @RequestParam(name = "EQ_bizLocation",  required = false) String eqBizLocation,
            @RequestParam(name = "MATCH_epc",       required = false) String matchEpc,
            @RequestParam(name = "MATCH_parentID",  required = false) String matchParentId,
            @RequestParam(name = "gln",             required = false) String gln,
            @RequestParam(name = "GE_eventTime",    required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime geEventTime,
            @RequestParam(name = "LT_eventTime",    required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ltEventTime,
            @RequestParam(required = false) Integer maxEventCount,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDirection) {

        List<EpcisEventEntity> entities = queryEventUseCase.simpleEventQuery(
                eqEventType, eqAction, eqBizStep, eqDisposition,
                eqReadPoint, eqBizLocation, matchEpc, matchParentId, gln,
                ltEventTime, geEventTime,
                maxEventCount, orderBy, orderDirection);

        List<Object> events = entities.stream()
                .map(e -> parsePayload(e.getPayload(), e.getId()))
                .toList();

        log.info("GET /epcis/query/events → {} result(s)", events.size());
        return ResponseEntity.ok(QueryResponse.builder()
                .totalResults(events.size())
                .events(events)
                .build());
    }

    /**
     * Einzelnes Event per EPCIS 2.0 eventID.
     * eventID muss URL-encoded sein wenn sie Sonderzeichen enthält
     * (z.B. urn:uuid:a9c8... → urn%3Auuid%3Aa9c8...).
     */
    @GetMapping(value = "/events/{eventID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getEventById(@PathVariable String eventID) {
        EpcisEventEntity entity = queryEventUseCase.getEventById(eventID);
        log.info("GET /epcis/query/events/{} → found", eventID);
        return ResponseEntity.ok(parsePayload(entity.getPayload(), entity.getId()));
    }

    // ─────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────

    private Object parsePayload(String payload, Long entityId) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Stored payload for entity id=" + entityId + " is not valid JSON: " + e.getMessage(), e);
        }
    }
}
