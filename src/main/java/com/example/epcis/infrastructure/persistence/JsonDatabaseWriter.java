package com.example.epcis.infrastructure.persistence;

import com.example.epcis.domain.model.AggregationEvent;
import com.example.epcis.domain.model.EpcisEvent;
import com.example.epcis.domain.model.ObjectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Schreibt konvertierte EPCIS 2.0 JSON Events in PostgreSQL.
 * Ersetzt JsonFileWriter als primäre Persistenzschicht.
 *
 * JsonFileWriter bleibt aktiv als zusätzliches Audit-Log.
 */
@Component
public class JsonDatabaseWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonDatabaseWriter.class);

    private final EpcisEventRepository repository;

    public JsonDatabaseWriter(EpcisEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Speichert ein konvertiertes EPCIS 2.0 JSON Event in der Datenbank.
     *
     * @param event  Original Domain-Event (für Metadaten)
     * @param json   EPCIS 2.0 JSON als String
     */
    public void write(EpcisEvent event, String json) {
        EpcisEventEntity entity = EpcisEventEntity.builder()
                .eventType(resolveEventType(event))
                .eventTime(event.getEventTime())
                .payload(json)
                .createdAt(OffsetDateTime.now())
                .build();

        EpcisEventEntity saved = repository.save(entity);
        log.info("Event in DB gespeichert: id={}, type={}, eventTime={}",
                saved.getId(), saved.getEventType(), saved.getEventTime());
    }

    private String resolveEventType(EpcisEvent event) {
        if (event instanceof ObjectEvent)      return "ObjectEvent";
        if (event instanceof AggregationEvent) return "AggregationEvent";
        return event.getClass().getSimpleName();
    }
}
