package com.canda.epcis.application.port;

import com.canda.epcis.domain.model.EpcisEvent;

import java.util.List;

/**
 * Inbound port: parses a raw EPCIS 1.2 XML document into domain events.
 * Implementations live in the infrastructure layer (xml adapter).
 */
public interface EventParser {
    List<EpcisEvent> parse(String xml);
}
