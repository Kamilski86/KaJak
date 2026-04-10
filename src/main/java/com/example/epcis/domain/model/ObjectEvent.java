package com.example.epcis.domain.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * EPCIS ObjectEvent – beschreibt eine Aktion an einem oder mehreren EPCs.
 * Beispiel: Ware wird gescannt, versendet oder empfangen.
 */
@Getter
@SuperBuilder
public class ObjectEvent extends EpcisEvent {

    @Override
    public String getEventTypeName() {
        return "ObjectEvent";
    }

    /**
     * Liste der betroffenen EPCs (Electronic Product Codes).
     * Beispiel: "urn:epc:id:sgtin:0614141.107346.2017"
     */
    private List<String> epcList;

    /**
     * Optionale Mengenliste für Produkte ohne Serien-EPC.
     * Beispiel: 500 Einheiten einer bestimmten GTIN.
     */
    private List<QuantityElement> quantityList;
}
