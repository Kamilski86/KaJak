package com.canda.epcis.application.query;

import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * EPCIS 2.0 REST Binding — SimpleEventQuery Interface.
 *
 * Parameter-Namen folgen dem EPCIS 2.0 Prefix-Schema:
 *   EQ_   = equals
 *   GE_   = greater or equal
 *   LT_   = less than
 *   MATCH_ = matches (sucht in Listen: epcList, childEPCs, parentID)
 *
 * Wrappt EpcisEventRepository und gibt EpcisEventEntity zurück.
 * Der API-Layer parst den gespeicherten EPCIS 2.0 JSON-Payload direkt als Object.
 *
 * Hinweis orderBy="recordTime": recordTime liegt im JSON-Payload,
 * nicht als eigene DB-Spalte — Fallback auf eventTime in Phase 1.
 */
@Service
public class QueryEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryEventUseCase.class);

    public static final int    DEFAULT_MAX_EVENT_COUNT  = 1000;
    public static final int    HARD_MAX_EVENT_COUNT     = 10000;
    public static final String DEFAULT_ORDER_BY         = "eventTime";
    public static final String DEFAULT_ORDER_DIRECTION  = "DESC";

    private static final Set<String> ALLOWED_ORDER_BY        = Set.of("eventTime", "recordTime");
    private static final Set<String> ALLOWED_ORDER_DIRECTION  = Set.of("ASC", "DESC");

    private final EpcisEventRepository repository;

    public QueryEventUseCase(EpcisEventRepository repository) {
        this.repository = repository;
    }

    /**
     * SimpleEventQuery mit EPCIS 2.0 Parameternamen.
     * Alle Parameter optional, kombinierbar.
     *
     * @param eqEventType      EQ_eventType  — "ObjectEvent" | "AggregationEvent" | null
     * @param eqAction         EQ_action     — "ADD" | "OBSERVE" | "DELETE" | null
     * @param eqBizStep        EQ_bizStep    — URI | null
     * @param eqDisposition    EQ_disposition — URI | null
     * @param eqReadPoint      EQ_readPoint  — SGLN URI | null
     * @param eqBizLocation    EQ_bizLocation — SGLN URI | null
     * @param matchEpc         MATCH_epc     — sucht in epcList UND childEPCs | null
     * @param matchParentId    MATCH_parentID — SSCC URI | null
     * @param gln              Phase-1-spezifisch: sucht in readPoint + bizLocation | null
     * @param ltEventTime      LT_eventTime  — eventTime &lt; Wert | null
     * @param geEventTime      GE_eventTime  — eventTime &gt;= Wert | null
     * @param maxEventCount    max Ergebnisse (default 1000, max 10000)
     * @param orderBy          "eventTime" | "recordTime" | null → default "eventTime"
     * @param orderDirection   "ASC" | "DESC" | null → default "DESC"
     * @throws IllegalArgumentException bei ungültigem orderBy oder orderDirection
     */
    public List<EpcisEventEntity> simpleEventQuery(
            String eqEventType, String eqAction, String eqBizStep, String eqDisposition,
            String eqReadPoint, String eqBizLocation, String matchEpc, String matchParentId,
            String gln, OffsetDateTime ltEventTime, OffsetDateTime geEventTime,
            Integer maxEventCount, String orderBy, String orderDirection) {

        int limit = clamp(maxEventCount);
        Sort sort = buildSort(orderBy, orderDirection);
        PageRequest pageRequest = PageRequest.of(0, limit, sort);

        List<EpcisEventEntity> results = repository.search(
                eqEventType, eqAction, eqBizStep, eqDisposition,
                eqReadPoint, eqBizLocation, matchParentId, matchEpc, gln,
                geEventTime, ltEventTime,
                pageRequest
        ).getContent();

        log.info("SimpleEventQuery → {} result(s) [EQ_eventType={} EQ_action={} MATCH_epc={} limit={}]",
                results.size(), eqEventType, eqAction, matchEpc, limit);
        return results;
    }

    /**
     * Einzelnes Event per EPCIS eventID abrufen.
     * eventID muss URL-encoded übergeben werden wenn sie Slashes enthält.
     *
     * @throws EpcisQueryException wenn kein Event mit dieser ID gefunden wurde (→ HTTP 404)
     */
    public EpcisEventEntity getEventById(String eventId) {
        return repository.findByEventId(eventId)
                .orElseThrow(() -> {
                    log.warn("getEventById → not found: {}", eventId);
                    return new EpcisQueryException("Event not found: " + eventId);
                });
    }

    // ─────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────

    private int clamp(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_MAX_EVENT_COUNT;
        return Math.min(requested, HARD_MAX_EVENT_COUNT);
    }

    private Sort buildSort(String orderBy, String orderDirection) {
        String resolvedOrderBy        = orderBy        != null ? orderBy        : DEFAULT_ORDER_BY;
        String resolvedOrderDirection = orderDirection != null ? orderDirection : DEFAULT_ORDER_DIRECTION;

        if (!ALLOWED_ORDER_BY.contains(resolvedOrderBy)) {
            throw new IllegalArgumentException(
                    "Invalid orderBy value: '" + resolvedOrderBy + "'. Allowed: " + ALLOWED_ORDER_BY);
        }
        if (!ALLOWED_ORDER_DIRECTION.contains(resolvedOrderDirection.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid orderDirection value: '" + resolvedOrderDirection + "'. Allowed: " + ALLOWED_ORDER_DIRECTION);
        }

        // recordTime is inside the JSON payload — not a DB column.
        // Phase 1: fall back to eventTime for both cases.
        Sort.Direction direction = "ASC".equalsIgnoreCase(resolvedOrderDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, "eventTime");
    }
}
