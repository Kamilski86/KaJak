package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Source party or location for an EPCIS event.
 * Maps to EPCIS 1.2 sourceList/source and EPCIS 2.0 sourceList entry.
 * The type URI comes from CBV source/destination type vocabulary (sdt:).
 */
@Getter
@Builder
public class Source {
    private final String type;
    private final String value;
}
