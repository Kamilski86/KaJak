package com.canda.epcis.application.port;

import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.infrastructure.json.EpcisDocumentDto;

import java.util.List;

/**
 * Outbound port: renders domain events into EPCIS 2.0 JSON/JSON-LD structures.
 * Implementations live in the infrastructure layer (json adapter).
 */
public interface EventRenderer {
    String renderEvent(EpcisEvent event);
    EpcisDocumentDto renderDocument(List<EpcisEvent> events);
}
