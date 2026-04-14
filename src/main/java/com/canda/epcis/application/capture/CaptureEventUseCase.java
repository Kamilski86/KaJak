package com.canda.epcis.application.capture;

import com.canda.epcis.application.downstream.subscription.SubscriptionDispatcher;
import com.canda.epcis.application.inventory.InventoryProcessorService;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.CaptureResult;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.FilterResult;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.persistence.audit.CaptureAuditEntity;
import com.canda.epcis.infrastructure.persistence.audit.CaptureAuditRepository;
import com.canda.epcis.infrastructure.xml.EpcisXmlParser;
import com.canda.epcis.infrastructure.xml.EpcisXmlValidator;
import com.canda.epcis.infrastructure.json.Epcis2JsonRenderer;
import com.canda.epcis.infrastructure.persistence.JsonDatabaseWriter;
import com.canda.epcis.infrastructure.persistence.JsonFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vollständiger Capture-Prozess für EPCIS 1.2 XML Events.
 *
 * Verarbeitungsschritte:
 * 1. XSD-Validierung → EpcisValidationException bei Fehler (→ HTTP 400)
 * 2. XML Parsen → Domain-Objekte
 * 3. Pro Event: EPC filtern, ggf. Event droppen
 * 4. Akzeptierte Events: JSON rendern + in DB + in Datei schreiben
 * 5. CaptureAudit speichern
 * 6. CaptureResult zurückgeben
 *
 * Fehler in einzelnen Events unterbrechen NICHT die gesamte Session.
 * Fehler werden in CaptureResult.errors gesammelt.
 */
@Service
public class CaptureEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureEventUseCase.class);

    private final EpcisXmlValidator xmlValidator;
    private final EpcisXmlParser xmlParser;
    private final EpcFilterService epcFilterService;
    private final Epcis2JsonRenderer jsonRenderer;
    private final JsonDatabaseWriter databaseWriter;
    private final JsonFileWriter fileWriter;
    private final CaptureAuditRepository auditRepository;
    private final InventoryProcessorService inventoryProcessorService;
    private final SubscriptionDispatcher subscriptionDispatcher;

    public CaptureEventUseCase(EpcisXmlValidator xmlValidator,
                               EpcisXmlParser xmlParser,
                               EpcFilterService epcFilterService,
                               Epcis2JsonRenderer jsonRenderer,
                               JsonDatabaseWriter databaseWriter,
                               JsonFileWriter fileWriter,
                               CaptureAuditRepository auditRepository,
                               InventoryProcessorService inventoryProcessorService,
                               SubscriptionDispatcher subscriptionDispatcher) {
        this.xmlValidator = xmlValidator;
        this.xmlParser = xmlParser;
        this.epcFilterService = epcFilterService;
        this.jsonRenderer = jsonRenderer;
        this.databaseWriter = databaseWriter;
        this.fileWriter = fileWriter;
        this.auditRepository = auditRepository;
        this.inventoryProcessorService = inventoryProcessorService;
        this.subscriptionDispatcher = subscriptionDispatcher;
    }

    /**
     * @param xml      EPCIS 1.2 XML-Dokument
     * @param sourceId Datenquelle — z.B. "STORE-DE-001", "DC-HAMBURG", "PARTNER-DM". Pflichtfeld.
     * @throws IllegalArgumentException  wenn sourceId null oder leer
     * @throws com.canda.epcis.infrastructure.xml.EpcisValidationException wenn XML nicht valide
     */
    @Transactional
    public CaptureResult capture(String xml, String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be null or blank");
        }

        String sessionId = UUID.randomUUID().toString();
        OffsetDateTime receivedAt = OffsetDateTime.now();

        log.info("CAPTURE_START sessionId={} sourceId={} xmlLength={}", sessionId, sourceId, xml != null ? xml.length() : 0);

        // Stage 1: XSD-Validierung — wirft EpcisValidationException bei Fehler
        xmlValidator.validate(xml);

        // Stage 2: XML parsen
        List<EpcisEvent> events = xmlParser.parse(xml);
        int totalReceived = events.size();
        log.info("CAPTURE_PARSED sessionId={} eventCount={}", sessionId, totalReceived);

        // Stage 3-4: Pro Event filtern, rendern und speichern
        List<String> captureIds = new ArrayList<>();
        List<CaptureResult.CaptureError> errors = new ArrayList<>();
        int accepted = 0;
        int filtered = 0;
        int dropped = 0;

        for (EpcisEvent event : events) {
            String eventId = event.getEventId();
            try {
                FilterResult filterResult = applyEpcFilter(event);

                if (filterResult.isEventShouldBeDropped()) {
                    log.warn("CAPTURE_EVENT_DROPPED sessionId={} eventId={} reason={}",
                            sessionId, eventId, filterResult.getDropReason());
                    dropped++;
                    errors.add(CaptureResult.CaptureError.builder()
                            .eventId(eventId)
                            .errorCode("EVENT_DROPPED")
                            .message(filterResult.getDropReason())
                            .build());
                    continue;
                }

                EpcisEvent eventToSave = buildFilteredEvent(event, filterResult);
                String json = jsonRenderer.renderEvent(eventToSave);
                databaseWriter.save(eventToSave, json);
                fileWriter.write(json);
                inventoryProcessorService.process(eventToSave);
                subscriptionDispatcher.dispatch(eventToSave, json);
                // TODO Phase 3 Schicht 3+4: arrivalNotificationService.onReceivingEvent(eventToSave)
                // TODO Phase 3 Schicht 3+4: goodsReceiptNotificationService.onReceivingEvent(eventToSave)
                // TODO Phase 3 Schicht 3+4: trolleyNotificationService.onTrolleyReadEvent(eventToSave)

                captureIds.add(eventId != null ? eventId : "unknown");
                accepted++;

                if (!filterResult.getFilteredEpcs().isEmpty()) {
                    filtered++;
                }

            } catch (Exception e) {
                log.error("CAPTURE_EVENT_ERROR sessionId={} eventId={} error={}", sessionId, eventId, e.getMessage(), e);
                errors.add(CaptureResult.CaptureError.builder()
                        .eventId(eventId)
                        .errorCode("PROCESSING_ERROR")
                        .message(e.getMessage())
                        .build());
            }
        }

        // Stage 5: Audit speichern
        auditRepository.save(CaptureAuditEntity.builder()
                .sessionId(sessionId)
                .sourceId(sourceId)
                .receivedAt(receivedAt)
                .totalReceived(totalReceived)
                .totalAccepted(accepted)
                .totalFiltered(filtered)
                .totalDropped(dropped)
                .totalErrors(errors.size())
                .build());

        log.info("CAPTURE_COMPLETE sessionId={} received={} accepted={} dropped={} errors={}",
                sessionId, totalReceived, accepted, dropped, errors.size());

        return CaptureResult.builder()
                .sessionId(sessionId)
                .totalReceived(totalReceived)
                .totalAccepted(accepted)
                .totalFiltered(filtered)
                .totalDropped(dropped)
                .captureIds(captureIds)
                .errors(errors)
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────

    private FilterResult applyEpcFilter(EpcisEvent event) {
        if (event instanceof ObjectEvent oe) {
            return epcFilterService.filterObjectEvent(oe);
        } else if (event instanceof AggregationEvent ae) {
            return epcFilterService.filterAggregationEvent(ae);
        }
        return FilterResult.builder()
                .acceptedEpcs(List.of())
                .filteredEpcs(List.of())
                .eventShouldBeDropped(false)
                .build();
    }

    private EpcisEvent buildFilteredEvent(EpcisEvent event, FilterResult filterResult) {
        if (filterResult.getFilteredEpcs().isEmpty()) {
            return event; // nothing was filtered — return original unchanged
        }
        if (event instanceof ObjectEvent oe) {
            return epcFilterService.applyFilter(oe, filterResult.getAcceptedEpcs());
        } else if (event instanceof AggregationEvent ae) {
            return epcFilterService.applyFilter(ae, filterResult.getAcceptedEpcs());
        }
        return event;
    }
}
