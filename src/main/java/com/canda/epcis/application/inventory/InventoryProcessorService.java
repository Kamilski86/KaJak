package com.canda.epcis.application.inventory;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateEntity;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryEntity;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verarbeitet ein einzelnes EPCIS Event und aktualisiert EPC State,
 * SSCC Content und Movement History.
 *
 * Idempotenz: Wenn eventId bereits in movement_history für eine EPC vorhanden → skip.
 * Out-of-Order (REQ217): Out-of-Order Events werden in History gespeichert,
 * aber aktualisieren bizLocation / lastEventId NICHT.
 */
@Service
public class InventoryProcessorService {

    private static final Logger log = LoggerFactory.getLogger(InventoryProcessorService.class);

    // CBV URN-Präfixe die zu Short-Names normalisiert werden
    private static final List<String> CBV_URN_PREFIXES = List.of(
            "urn:epcglobal:cbv:disp:",
            "urn:epcglobal:cbv:bizstep:"
    );

    private final EpcStateRepository epcStateRepository;
    private final SsccContentRepository ssccContentRepository;
    private final MovementHistoryRepository movementHistoryRepository;

    public InventoryProcessorService(EpcStateRepository epcStateRepository,
                                     SsccContentRepository ssccContentRepository,
                                     MovementHistoryRepository movementHistoryRepository) {
        this.epcStateRepository = epcStateRepository;
        this.ssccContentRepository = ssccContentRepository;
        this.movementHistoryRepository = movementHistoryRepository;
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * Verarbeitet ein einzelnes EPCIS Event in einer eigenen Transaktion (REQUIRES_NEW).
     *
     * REQUIRES_NEW stellt sicher, dass ein Fehler in diesem Event die äußere Transaktion
     * (Capture oder Rebuild) niemals als rollback-only markiert. Jedes Event ist atomar
     * und unabhängig verarbeitbar.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(EpcisEvent event) {
        if (event instanceof ObjectEvent oe) {
            processObjectEvent(oe);
        } else if (event instanceof AggregationEvent ae) {
            processAggregationEvent(ae);
        } else {
            log.warn("INVENTORY_SKIP unsupported event type: {}", event.getClass().getSimpleName());
        }
    }

    // ─────────────────────────────────────────────
    // ObjectEvent
    // ─────────────────────────────────────────────

    private void processObjectEvent(ObjectEvent event) {
        List<String> epcs = event.getEpcList();
        if (epcs == null || epcs.isEmpty()) return;

        for (String epcUrn : epcs) {
            // BUG1-FIX: effectiveEventId immer berechnen — ermöglicht Idempotenz-Check
            // auch für Events ohne eventID (deterministischer Hash statt random UUID).
            String effectiveEventId = resolveEffectiveEventId(event, epcUrn);
            if (movementHistoryRepository.existsByEpcUrnAndEventId(epcUrn, effectiveEventId)) {
                log.debug("INVENTORY_SKIP idempotent epc={} eventId={}", epcUrn, effectiveEventId);
                continue;
            }

            saveMovementHistory(epcUrn, event, effectiveEventId);

            boolean outOfOrder = isOutOfOrder(epcUrn, event.getEventTime());
            updateEpcState(epcUrn, event, outOfOrder);
        }
    }

    // ─────────────────────────────────────────────
    // AggregationEvent
    // ─────────────────────────────────────────────

    private void processAggregationEvent(AggregationEvent event) {
        Action action = event.getAction();
        String ssccUrn = event.getParentId();

        if (action == Action.ADD) {
            processAggregationAdd(event, ssccUrn);
        } else if (action == Action.DELETE) {
            processAggregationDelete(event, ssccUrn);
        } else if (action == Action.OBSERVE) {
            processAggregationObserve(event);
        }
    }

    private void processAggregationAdd(AggregationEvent event, String ssccUrn) {
        List<String> childEpcs = event.getChildEpcs();
        if (childEpcs == null || childEpcs.isEmpty()) return;

        for (String childEpc : childEpcs) {
            String effectiveEventId = resolveEffectiveEventId(event, childEpc);
            if (movementHistoryRepository.existsByEpcUrnAndEventId(childEpc, effectiveEventId)) {
                log.debug("INVENTORY_SKIP idempotent epc={} eventId={}", childEpc, effectiveEventId);
                continue;
            }

            saveMovementHistory(childEpc, event, effectiveEventId);

            // REQ210: SGTIN darf nur in einer SSCC sein — aus alter SSCC entfernen
            if (ssccUrn != null) {
                List<SsccContentEntity> existingAssignments = ssccContentRepository.findByChildEpc(childEpc);
                for (SsccContentEntity existing : existingAssignments) {
                    if (!existing.getSsccUrn().equals(ssccUrn)) {
                        log.info("INVENTORY_REQ210 moving epc={} from sscc={} to sscc={}",
                                childEpc, existing.getSsccUrn(), ssccUrn);
                        ssccContentRepository.deleteBySsccUrnAndChildEpcIn(
                                existing.getSsccUrn(), List.of(childEpc));
                    }
                }

                // BUG2+3-FIX: bizLocation-Fallback auf readPoint wenn bizLocation null
                if (!ssccContentRepository.existsBySsccUrnAndChildEpc(ssccUrn, childEpc)) {
                    ssccContentRepository.save(SsccContentEntity.builder()
                            .ssccUrn(ssccUrn)
                            .childEpc(childEpc)
                            .bizLocation(effectiveBizLocation(event))
                            .addedAt(event.getEventTime() != null ? event.getEventTime() : OffsetDateTime.now())
                            .build());
                }
            }

            boolean outOfOrder = isOutOfOrder(childEpc, event.getEventTime());
            updateEpcState(childEpc, event, outOfOrder);

            // REQ401: sscc-Feld NUR beim ersten Packing Event setzen
            if (ssccUrn != null) {
                Optional<EpcStateEntity> entityOpt = epcStateRepository.findByEpcUrn(childEpc);
                entityOpt.ifPresent(entity -> {
                    if (entity.getSscc() == null) {
                        entity.setSscc(ssccUrn);
                        entity.setUpdateDate(OffsetDateTime.now());
                        epcStateRepository.save(entity);
                    }
                });
            }
        }
    }

    private void processAggregationDelete(AggregationEvent event, String ssccUrn) {
        if (ssccUrn == null) return;

        List<String> childEpcs = event.getChildEpcs();

        // REQ215: Leere childEPCs → ALLE SGTINs dieser SSCC entfernen
        if (childEpcs == null || childEpcs.isEmpty()) {
            List<SsccContentEntity> allContent = ssccContentRepository.findBySsccUrn(ssccUrn);
            for (SsccContentEntity content : allContent) {
                String effectiveEventId = resolveEffectiveEventId(event, content.getChildEpc());
                if (!movementHistoryRepository.existsByEpcUrnAndEventId(content.getChildEpc(), effectiveEventId)) {
                    saveMovementHistory(content.getChildEpc(), event, effectiveEventId);
                    boolean outOfOrder = isOutOfOrder(content.getChildEpc(), event.getEventTime());
                    updateEpcState(content.getChildEpc(), event, outOfOrder);
                }
            }
            log.info("INVENTORY_REQ215 delete all from sscc={} count={}", ssccUrn, allContent.size());
            ssccContentRepository.deleteBySsccUrn(ssccUrn);
        } else {
            // Partielles DELETE — nur genannte SGTINs entfernen
            for (String childEpc : childEpcs) {
                String effectiveEventId = resolveEffectiveEventId(event, childEpc);
                if (movementHistoryRepository.existsByEpcUrnAndEventId(childEpc, effectiveEventId)) {
                    log.debug("INVENTORY_SKIP idempotent epc={} eventId={}", childEpc, effectiveEventId);
                    continue;
                }
                saveMovementHistory(childEpc, event, effectiveEventId);
                boolean outOfOrder = isOutOfOrder(childEpc, event.getEventTime());
                updateEpcState(childEpc, event, outOfOrder);
            }
            ssccContentRepository.deleteBySsccUrnAndChildEpcIn(ssccUrn, childEpcs);
        }
    }

    private void processAggregationObserve(AggregationEvent event) {
        List<String> childEpcs = event.getChildEpcs();
        if (childEpcs == null || childEpcs.isEmpty()) return;

        for (String childEpc : childEpcs) {
            String effectiveEventId = resolveEffectiveEventId(event, childEpc);
            if (movementHistoryRepository.existsByEpcUrnAndEventId(childEpc, effectiveEventId)) {
                log.debug("INVENTORY_SKIP idempotent epc={} eventId={}", childEpc, effectiveEventId);
                continue;
            }
            saveMovementHistory(childEpc, event, effectiveEventId);
            boolean outOfOrder = isOutOfOrder(childEpc, event.getEventTime());
            updateEpcState(childEpc, event, outOfOrder);
        }
    }

    // ─────────────────────────────────────────────
    // EpcState update
    // ─────────────────────────────────────────────

    /**
     * Legt einen neuen EpcState an oder aktualisiert einen bestehenden.
     *
     * REQ401 Update-Regel:
     * - Nicht existent → CREATE
     * - Existent + Event NEUER → UPDATE bizLocation, lastEventId, currentStatus
     * - Existent + Event ÄLTER (out-of-order) → nur Disposition-Timestamps updaten
     */
    private void updateEpcState(String epcUrn, EpcisEvent event, boolean isOutOfOrder) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<EpcStateEntity> existing = epcStateRepository.findByEpcUrn(epcUrn);

        String currentStatus = resolveCurrentStatus(event);
        String dispositionShort = stripCbvPrefix(event.getDisposition());

        if (existing.isEmpty()) {
            // CREATE — BUG2-FIX: effectiveBizLocation als Fallback auf readPoint
            EpcStateEntity entity = EpcStateEntity.builder()
                    .epcUrn(epcUrn)
                    .currentStatus(currentStatus)
                    .bizLocation(event.getAction() == Action.DELETE ? null : effectiveBizLocation(event))
                    .lastEventId(event.getEventId())
                    .lastEventTime(event.getEventTime())
                    .createDate(now)
                    .updateDate(now)
                    .build();

            setDispositionTimestamp(entity, dispositionShort, event.getEventTime());
            epcStateRepository.save(entity);
            log.debug("INVENTORY_CREATE_STATE epc={} status={}", epcUrn, currentStatus);

        } else {
            EpcStateEntity entity = existing.get();
            entity.setUpdateDate(now);

            if (!isOutOfOrder) {
                // UPDATE — BUG2-FIX: effectiveBizLocation als Fallback auf readPoint
                entity.setCurrentStatus(currentStatus);
                entity.setLastEventId(event.getEventId());
                entity.setLastEventTime(event.getEventTime());

                if (event.getAction() == Action.DELETE) {
                    entity.setBizLocation(null);
                } else {
                    entity.setBizLocation(effectiveBizLocation(event));
                }
            }

            // Disposition-Timestamp: NUR wenn neuer als bestehender Wert
            updateDispositionTimestampIfNewer(entity, dispositionShort, event.getEventTime());
            epcStateRepository.save(entity);
            log.debug("INVENTORY_UPDATE_STATE epc={} outOfOrder={} status={}", epcUrn, isOutOfOrder, entity.getCurrentStatus());
        }
    }

    // ─────────────────────────────────────────────
    // Movement History
    // ─────────────────────────────────────────────

    private void saveMovementHistory(String epcUrn, EpcisEvent event, String effectiveEventId) {
        movementHistoryRepository.save(MovementHistoryEntity.builder()
                .epcUrn(epcUrn)
                .eventId(effectiveEventId)
                .eventType(event.getEventTypeName())
                .action(event.getAction() != null ? event.getAction().name() : "UNKNOWN")
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .bizLocation(event.getBizLocation())
                .readPoint(event.getReadPoint())
                .eventTime(event.getEventTime())
                .recordedAt(OffsetDateTime.now())
                .build());
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /**
     * Berechnet den currentStatus aus disposition (Vorrang) oder bizStep.
     * Gibt Short-Names zurück (z.B. "in_transit", nicht den vollen URN).
     *
     * Mapping wenn nur bizStep vorhanden:
     * - shipping  → in_transit
     * - receiving → in_progress
     * - encoding  → encoded
     */
    private String resolveCurrentStatus(EpcisEvent event) {
        String disposition = stripCbvPrefix(event.getDisposition());
        if (disposition != null && !disposition.isBlank()) {
            return disposition;
        }

        String bizStep = stripCbvPrefix(event.getBizStep());
        if (bizStep == null) return null;

        return switch (bizStep) {
            case "shipping"  -> "in_transit";
            case "receiving" -> "in_progress";
            case "encoding"  -> "encoded";
            default          -> bizStep;
        };
    }

    /**
     * Prüft ob ein Event out-of-order ist.
     * Out-of-order = eventTime des neuen Events < last_event_time in epc_state.
     */
    private boolean isOutOfOrder(String epcUrn, OffsetDateTime eventTime) {
        if (eventTime == null) return false;
        return epcStateRepository.findByEpcUrn(epcUrn)
                .map(entity -> entity.getLastEventTime() != null
                        && eventTime.isBefore(entity.getLastEventTime()))
                .orElse(false);
    }

    /**
     * BUG1-FIX: Gibt immer eine eindeutige, stabile Event-ID zurück.
     *
     * - Hat das Event eine echte eventID → diese verwenden (Idempotenz gegen externe IDs).
     * - Fehlt die eventID (in EPCIS 1.2 optional) → deterministischer MD5-UUID aus
     *   (epcUrn | eventTime | action | bizStep). Dasselbe logische Event liefert bei
     *   jedem Rebuild denselben Hash → keine Duplikate in movement_history.
     */
    private String resolveEffectiveEventId(EpcisEvent event, String epcUrn) {
        if (event.getEventId() != null) return event.getEventId();
        String fingerprint = epcUrn + "|"
                + (event.getEventTime() != null ? event.getEventTime() : "")
                + "|" + (event.getAction()    != null ? event.getAction()    : "")
                + "|" + (event.getBizStep()   != null ? event.getBizStep()   : "");
        return "auto:" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * BUG2-FIX: Gibt die effektive Standort-URI zurück.
     *
     * AggregationEvents haben laut EPCIS-Standard oft keine bizLocation.
     * Fallback: readPoint verwenden wenn bizLocation null oder leer.
     */
    private String effectiveBizLocation(EpcisEvent event) {
        String loc = event.getBizLocation();
        return (loc != null && !loc.isBlank()) ? loc : event.getReadPoint();
    }

    /**
     * Entfernt bekannte CBV URN-Präfixe um den Short-Name zu erhalten.
     * Unbekannte Formate (bereits Short-Name oder Extension-URI) werden unverändert zurückgegeben.
     */
    private String stripCbvPrefix(String uri) {
        if (uri == null || uri.isBlank()) return null;
        for (String prefix : CBV_URN_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return uri.substring(prefix.length());
            }
        }
        return uri;
    }

    /**
     * Setzt den Disposition-Timestamp beim CREATE (immer setzen).
     */
    private void setDispositionTimestamp(EpcStateEntity entity, String dispositionShort, OffsetDateTime time) {
        if (dispositionShort == null || time == null) return;
        applyDispositionTimestamp(entity, dispositionShort, time);
    }

    /**
     * Aktualisiert den Disposition-Timestamp NUR wenn der neue Wert neuer ist (REQ217).
     */
    private void updateDispositionTimestampIfNewer(EpcStateEntity entity, String dispositionShort, OffsetDateTime time) {
        if (dispositionShort == null || time == null) return;

        OffsetDateTime existing = getExistingDispositionTimestamp(entity, dispositionShort);
        if (existing == null || time.isAfter(existing)) {
            applyDispositionTimestamp(entity, dispositionShort, time);
        }
    }

    private OffsetDateTime getExistingDispositionTimestamp(EpcStateEntity entity, String dispositionShort) {
        return switch (dispositionShort) {
            case "encoded"                              -> entity.getEncodedAt();
            case "in_progress"                          -> entity.getInProgressAt();
            case "in_transit"                           -> entity.getInTransitAt();
            case "sellable_accessible"                  -> entity.getAccessibleForCustomerAt();
            case "sellable_not_accessible"              -> entity.getAvailableNotAccessibleAt();
            case "retail_sold"                          -> entity.getSoldAt();
            case "inactive", "recalled", "non_sellable_other" -> entity.getRetiredAt();
            default                                     -> null;
        };
    }

    private void applyDispositionTimestamp(EpcStateEntity entity, String dispositionShort, OffsetDateTime time) {
        switch (dispositionShort) {
            case "encoded"                              -> entity.setEncodedAt(time);
            case "in_progress"                          -> entity.setInProgressAt(time);
            case "in_transit"                           -> entity.setInTransitAt(time);
            case "sellable_accessible"                  -> entity.setAccessibleForCustomerAt(time);
            case "sellable_not_accessible"              -> entity.setAvailableNotAccessibleAt(time);
            case "retail_sold"                          -> entity.setSoldAt(time);
            case "inactive", "recalled", "non_sellable_other" -> entity.setRetiredAt(time);
            default -> { /* unbekannte Disposition — kein Timestamp-Feld vorhanden */ }
        }
    }
}
