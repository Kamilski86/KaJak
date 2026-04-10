package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Instance/Lot Master Data carried inside an ObjectEvent.
 * Represents a flat map of local-name → string-value extracted from the
 * EPCIS 1.2 &lt;ilmd&gt; element.
 *
 * Limitation: nested ILMD structures and multi-namespace attribute disambiguation
 * are not supported. Events with structurally complex ILMD are routed to quarantine.
 * See docs/mapping-matrix.md §ILMD for the documented mapping decision.
 */
@Getter
@Builder
public class IlmdPayload {
    private final Map<String, String> fields;
}
