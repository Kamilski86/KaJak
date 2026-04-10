package com.canda.epcis.infrastructure.persistence;

import com.canda.epcis.application.port.QuarantineStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DatabaseQuarantineStore implements QuarantineStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQuarantineStore.class);

    private final QuarantineRepository repository;

    public DatabaseQuarantineStore(QuarantineRepository repository) {
        this.repository = repository;
    }

    @Override
    public void quarantine(String reason, String errorCode, String rawFragment) {
        QuarantineEntity entity = QuarantineEntity.builder()
                .reason(reason)
                .errorCode(errorCode)
                .rawFragment(rawFragment)
                .build();
        QuarantineEntity saved = repository.save(entity);
        log.warn("Event quarantined: id={}, errorCode={}, reason={}", saved.getId(), errorCode, reason);
    }
}
