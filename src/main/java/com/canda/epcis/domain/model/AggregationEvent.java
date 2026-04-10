package com.canda.epcis.domain.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * EPCIS AggregationEvent – beschreibt das Zusammenfassen oder Trennen von Objekten.
 * Beispiel: Einzelne SGTINs werden auf eine SSCC-Palette gepackt.
 */
@Getter
@SuperBuilder
public class AggregationEvent extends EpcisEvent {

    @Override
    public String getEventTypeName() {
        return "AggregationEvent";
    }

    /**
     * Der übergeordnete EPC – z.B. die Palette (SSCC).
     * Kann null sein bei DISAGGREGATION-Events.
     */
    private String parentId;

    /**
     * Liste der untergeordneten EPCs – z.B. die einzelnen Kartons auf der Palette.
     */
    private List<String> childEpcs;

    /**
     * Optionale Mengenliste für Kinder ohne Serien-EPC.
     */
    private List<QuantityElement> childQuantityList;
}
