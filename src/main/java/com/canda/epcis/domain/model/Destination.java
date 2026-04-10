package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Destination party or location for an EPCIS event.
 * Maps to EPCIS 1.2 destinationList/destination and EPCIS 2.0 destinationList entry.
 * The type URI comes from CBV source/destination type vocabulary (sdt:).
 */
@Getter
@Builder
public class Destination {
    private final String type;
    private final String value;
}
