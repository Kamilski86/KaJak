package com.canda.epcis.application.inventory;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Berechnet den gesamten Inventory-State neu aus allen gespeicherten Events.
 * Idempotent — kann mehrfach aufgerufen werden.
 *
 * Ablauf:
 * 1. epc_state, sscc_content, movement_history komplett leeren
 * 2. Alle Events aus epcis_event in chronologischer Reihenfolge (event_time ASC) laden
 * 3. Jeden Event durch InventoryProcessorService verarbeiten
 */
@Service
public class InventoryRebuildService {

    private static final Logger log = LoggerFactory.getLogger(InventoryRebuildService.class);

    private final EpcisEventRepository epcisEventRepository;
    private final EpcStateRepository epcStateRepository;
    private final SsccContentRepository ssccContentRepository;
    private final MovementHistoryRepository movementHistoryRepository;
    private final InventoryProcessorService inventoryProcessorService;
    private final ObjectMapper objectMapper;

    public InventoryRebuildService(EpcisEventRepository epcisEventRepository,
                                   EpcStateRepository epcStateRepository,
                                   SsccContentRepository ssccContentRepository,
                                   MovementHistoryRepository movementHistoryRepository,
                                   InventoryProcessorService inventoryProcessorService,
                                   ObjectMapper objectMapper) {
        this.epcisEventRepository = epcisEventRepository;
        this.epcStateRepository = epcStateRepository;
        this.ssccContentRepository = ssccContentRepository;
        this.movementHistoryRepository = movementHistoryRepository;
        this.inventoryProcessorService = inventoryProcessorService;
        this.objectMapper = objectMapper;
    }

    /**
     * Leert alle Inventory-Tabellen und verarbeitet alle Events neu.
     *
     * Kein @Transactional auf der Methode: jedes process()-Call läuft in einer eigenen
     * Transaktion (REQUIRES_NEW in InventoryProcessorService). Ein einzelnes Event-Fehler
     * wirft die gesamte Rebuild-Operation nicht ab.
     * Die deleteAll()-Calls nutzen die eingebauten Repository-Transaktionen.
     *
     * @return RebuildResult mit Statistiken
     */
    public RebuildResult rebuild() {
        long startMs = System.currentTimeMillis();
        log.info("REBUILD_START clearing inventory tables");

        // Inventory-Tabellen leeren
        movementHistoryRepository.deleteAll();
        ssccContentRepository.deleteAll();
        epcStateRepository.deleteAll();

        // Alle Events chronologisch laden
        List<EpcisEventEntity> allEvents = epcisEventRepository.findAll(
                Sort.by(Sort.Direction.ASC, "eventTime", "id"));

        log.info("REBUILD_PROCESSING eventCount={}", allEvents.size());

        int processed = 0;
        int skipped = 0;
        int errors = 0;

        for (EpcisEventEntity entity : allEvents) {
            try {
                EpcisEvent event = deserializeEvent(entity);
                if (event == null) {
                    skipped++;
                    continue;
                }
                inventoryProcessorService.process(event);
                processed++;
            } catch (Exception e) {
                log.error("REBUILD_EVENT_ERROR entityId={} error={}", entity.getId(), e.getMessage(), e);
                errors++;
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("REBUILD_COMPLETE processed={} skipped={} errors={} durationMs={}",
                processed, skipped, errors, durationMs);

        return RebuildResult.builder()
                .eventsProcessed(processed)
                .eventsSkipped(skipped)
                .errors(errors)
                .durationMs(durationMs)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────
    // Deserialization: EpcisEventEntity → EpcisEvent
    // ─────────────────────────────────────────────

    /**
     * Rekonstruiert ein Domain-Event aus dem EPCIS 2.0 JSON-Payload.
     * Gibt null zurück wenn der Event-Typ unbekannt ist oder der Payload defekt ist.
     */
    private EpcisEvent deserializeEvent(EpcisEventEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getPayload());
            String type = node.path("type").asText();

            return switch (type) {
                case "ObjectEvent"      -> deserializeObjectEvent(node);
                case "AggregationEvent" -> deserializeAggregationEvent(node);
                default -> {
                    log.warn("REBUILD_SKIP unknown event type={} entityId={}", type, entity.getId());
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("REBUILD_DESERIALIZE_ERROR entityId={} error={}", entity.getId(), e.getMessage());
            return null;
        }
    }

    private ObjectEvent deserializeObjectEvent(JsonNode node) {
        List<String> epcList = new ArrayList<>();
        JsonNode epcListNode = node.path("epcList");
        if (epcListNode.isArray()) {
            epcListNode.forEach(n -> epcList.add(n.asText()));
        }

        return ObjectEvent.builder()
                .eventId(nullableText(node, "eventID"))
                .eventTime(parseOffsetDateTime(node.path("eventTime").asText(null)))
                .eventTimeZoneOffset(nullableText(node, "eventTimeZoneOffset"))
                .action(parseAction(node.path("action").asText(null)))
                .bizStep(nullableText(node, "bizStep"))
                .disposition(nullableText(node, "disposition"))
                .bizLocation(node.path("bizLocation").path("id").asText(null))
                .readPoint(node.path("readPoint").path("id").asText(null))
                .epcList(epcList)
                .build();
    }

    private AggregationEvent deserializeAggregationEvent(JsonNode node) {
        List<String> childEpcs = new ArrayList<>();
        JsonNode childEpcsNode = node.path("childEPCs");
        if (childEpcsNode.isArray()) {
            childEpcsNode.forEach(n -> childEpcs.add(n.asText()));
        }

        return AggregationEvent.builder()
                .eventId(nullableText(node, "eventID"))
                .eventTime(parseOffsetDateTime(node.path("eventTime").asText(null)))
                .eventTimeZoneOffset(nullableText(node, "eventTimeZoneOffset"))
                .action(parseAction(node.path("action").asText(null)))
                .bizStep(nullableText(node, "bizStep"))
                .disposition(nullableText(node, "disposition"))
                .bizLocation(node.path("bizLocation").path("id").asText(null))
                .readPoint(node.path("readPoint").path("id").asText(null))
                .parentId(nullableText(node, "parentID"))
                .childEpcs(childEpcs)
                .build();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private OffsetDateTime parseOffsetDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Action parseAction(String text) {
        if (text == null) return null;
        try {
            return Action.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────
    // Result DTO
    // ─────────────────────────────────────────────

    @Getter
    @Builder
    public static class RebuildResult {
        private final int eventsProcessed;
        private final int eventsSkipped;
        private final int errors;
        private final long durationMs;
        private final OffsetDateTime completedAt;
    }
}
