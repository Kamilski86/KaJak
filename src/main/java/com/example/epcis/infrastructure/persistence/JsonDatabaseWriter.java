package com.example.epcis.infrastructure.persistence;

import com.example.epcis.domain.model.EpcisEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonDatabaseWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonDatabaseWriter.class);

    private final EpcisEventRepository repository;

    public JsonDatabaseWriter(EpcisEventRepository repository) {
        this.repository = repository;
    }

    public void write(EpcisEvent event, String json) {
        EpcisEventEntity entity = EpcisEventEntity.builder()
                .eventType(event.getEventTypeName())
                .eventTime(event.getEventTime())
                .payload(json)
                // createdAt is set automatically by @CreationTimestamp
                .build();

        EpcisEventEntity saved = repository.save(entity);
        log.info("Event saved to DB: id={}, type={}, eventTime={}",
                saved.getId(), saved.getEventType(), saved.getEventTime());
    }
}
