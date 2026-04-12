package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Ergebnis der EPC-Filterung für ein einzelnes Event.
 * Immutable Value Object.
 *
 * eventShouldBeDropped = true wenn:
 * - parentID eines AggregationEvent kein SSCC ist
 * - nach Filterung keine EPCs mehr übrig sind
 */
@Getter
@Builder
public class FilterResult {

    private final List<String> acceptedEpcs;
    private final List<FilteredEpc> filteredEpcs;
    private final boolean eventShouldBeDropped;
    /** Nur gesetzt wenn eventShouldBeDropped = true. */
    private final String dropReason;

    /**
     * Erlaubte Werte für FilteredEpc.reason:
     * FORBIDDEN_GID, FORBIDDEN_USDOD, FORBIDDEN_ADI, FORBIDDEN_BIC,
     * UNSUPPORTED_FORMAT, INVALID_PARENT_ID_NOT_SSCC
     */
    @Getter
    @Builder
    public static class FilteredEpc {
        private final String epc;
        private final String reason;
    }
}
