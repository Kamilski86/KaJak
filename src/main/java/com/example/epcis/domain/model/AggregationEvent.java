package com.example.epcis.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * EPCIS AggregationEvent – beschreibt das Zusammenfassen oder Trennen von Objekten.
 * Beispiel: Einzelne SGTINs werden auf eine SSCC-Palette gepackt.
 */
@Getter
@Setter
@SuperBuilder
public class AggregationEvent extends EpcisEvent {

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
     * Aktion die durchgeführt wurde.
     * ADD    = Objekte werden dem Parent hinzugefügt (Palettieren)
     * OBSERVE = Aggregation wird beobachtet
     * DELETE = Objekte werden vom Parent entfernt (Depalettieren)
     */
    private Action action;

    /**
     * Optionale Mengenliste für Kinder ohne Serien-EPC.
     */
    private List<QuantityElement> childQuantityList;
}
