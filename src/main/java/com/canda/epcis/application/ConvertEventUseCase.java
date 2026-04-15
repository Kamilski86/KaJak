package com.canda.epcis.application;

import com.canda.epcis.application.cbv.CbvValidationService;
import com.canda.epcis.application.port.EventAuditWriter;
import com.canda.epcis.application.port.EventParser;
import com.canda.epcis.application.port.EventRenderer;
import com.canda.epcis.application.port.EventStore;
import com.canda.epcis.application.port.EventValidator;
import com.canda.epcis.application.port.QuarantineStore;
import com.canda.epcis.config.CbvConfig;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.cbv.CbvValidationResult;
import com.canda.epcis.domain.service.CbvValidationException;
import com.canda.epcis.domain.service.CbvVocabularyValidator;
import com.canda.epcis.domain.service.Epcis2JsonSchemaValidator;
import com.canda.epcis.infrastructure.json.EpcisDocumentDto;
import com.canda.epcis.infrastructure.xml.EpcisParsingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConvertEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConvertEventUseCase.class);

    private final EventValidator validator;
    private final EventParser parser;
    private final CbvVocabularyValidator cbvValidator;
    private final CbvValidationService cbvValidationService;
    private final CbvConfig cbvConfig;
    private final Epcis2JsonSchemaValidator jsonSchemaValidator;
    private final EventRenderer renderer;
    private final EventStore eventStore;
    private final EventAuditWriter auditWriter;
    private final QuarantineStore quarantineStore;

    private final Counter receivedCounter;
    private final Counter convertedCounter;
    private final Counter quarantinedCounter;
    private final Counter failedCounter;

    public ConvertEventUseCase(EventValidator validator,
                               EventParser parser,
                               CbvVocabularyValidator cbvValidator,
                               CbvValidationService cbvValidationService,
                               CbvConfig cbvConfig,
                               Epcis2JsonSchemaValidator jsonSchemaValidator,
                               EventRenderer renderer,
                               EventStore eventStore,
                               EventAuditWriter auditWriter,
                               QuarantineStore quarantineStore,
                               MeterRegistry meterRegistry) {
        this.validator = validator;
        this.parser = parser;
        this.cbvValidator = cbvValidator;
        this.cbvValidationService = cbvValidationService;
        this.cbvConfig = cbvConfig;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.renderer = renderer;
        this.eventStore = eventStore;
        this.auditWriter = auditWriter;
        this.quarantineStore = quarantineStore;

        this.receivedCounter   = meterRegistry.counter("epcis.events.received");
        this.convertedCounter  = meterRegistry.counter("epcis.events.converted");
        this.quarantinedCounter = meterRegistry.counter("epcis.events.quarantined");
        this.failedCounter     = meterRegistry.counter("epcis.events.failed");
    }

    @Transactional
    public EpcisDocumentDto convert(String xml) {
        receivedCounter.increment();
        log.info("Conversion started");

        // Stage 1: XSD structural validation — hard reject on malformed input
        try {
            validator.validate(xml);
        } catch (RuntimeException e) {
            failedCounter.increment();
            throw e;
        }

        // Stage 2: parse into canonical domain model
        // Unsupported event types throw EpcisParsingException → quarantine
        List<EpcisEvent> events;
        try {
            events = parser.parse(xml);
        } catch (EpcisParsingException e) {
            quarantineStore.quarantine(e.getMessage(), "UNSUPPORTED_EVENT_TYPE", xml);
            quarantinedCounter.increment();
            throw e;
        }
        log.info("{} event(s) parsed", events.size());

        // Stage 5a: CBV vocabulary validation (hardcoded allowlist) — hard reject on violation
        try {
            events.forEach(cbvValidator::validate);
        } catch (CbvValidationException e) {
            quarantineStore.quarantine(e.getMessage(), "CBV_VIOLATION", xml);
            quarantinedCounter.increment();
            throw e;
        }

        // Stage 5b: CBV DB-backed validation (Phase 4) — strict-mode: quarantine; lenient: warn
        List<EpcisEvent> processableEvents = new ArrayList<>();
        for (EpcisEvent event : events) {
            CbvValidationResult cbvResult = cbvValidationService.validate(event);
            if (!cbvResult.isValid()) {
                if (cbvConfig.isStrictMode()) {
                    cbvValidationService.quarantine(event, xml, null, cbvResult);
                    quarantinedCounter.increment();
                    log.warn("CBV strict-mode quarantine: eventId={} violations={}",
                            event.getEventId(), cbvResult.getViolations().size());
                } else {
                    log.warn("CBV_VIOLATION eventId={} violations={}",
                            event.getEventId(), cbvResult.getViolations());
                    processableEvents.add(event);
                }
            } else {
                processableEvents.add(event);
            }
        }
        events = processableEvents;

        if (events.isEmpty()) {
            quarantinedCounter.increment();
            throw new CbvValidationException(
                    "All events quarantined due to CBV violations (strict-mode=true).");
        }

        // Render → persist (DB transactional) → audit (file best-effort)
        try {
            List<String> eventJsons = events.stream()
                    .map(event -> {
                        String json = renderer.renderEvent(event);
                        eventStore.save(event, json);
                        return json;
                    })
                    .toList();

            eventJsons.forEach(auditWriter::write);

            EpcisDocumentDto document = renderer.renderDocument(events);

            // Stage 4: validate rendered output against official GS1 EPCIS 2.0 JSON Schema
            jsonSchemaValidator.validate(document);

            convertedCounter.increment(events.size());
            log.info("Conversion complete: {} event(s) written", events.size());
            return document;

        } catch (RuntimeException e) {
            failedCounter.increment();
            throw e;
        }
    }
}
