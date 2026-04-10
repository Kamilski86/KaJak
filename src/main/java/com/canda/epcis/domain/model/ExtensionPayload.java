package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Namespace-safe container for custom extension elements on any EPCIS event.
 * Represents a flat map of local-name → string-value extracted from
 * EPCIS 1.2 extension elements within the event body.
 *
 * Limitation: multi-level nesting and namespace collision resolution are not
 * supported. Events with complex extensions are routed to quarantine.
 * See docs/mapping-matrix.md §Extensions for the documented mapping decision.
 */
@Getter
@Builder
public class ExtensionPayload {
    private final Map<String, String> fields;
}
