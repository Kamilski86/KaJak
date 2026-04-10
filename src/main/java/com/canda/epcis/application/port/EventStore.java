package com.canda.epcis.application.port;

import com.canda.epcis.domain.model.EpcisEvent;

/**
 * Outbound port: persists a converted domain event and its rendered JSON payload.
 * Implementations live in the infrastructure layer (persistence adapter).
 */
public interface EventStore {
    void save(EpcisEvent event, String renderedJson);
}
