package com.example.epcis.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object – repräsentiert eine Geschäftstransaktion die mit einem Event verknüpft ist.
 * Beispiel: Bestellnummer, Lieferschein, Rechnung.
 *
 * Immutable – kein @Setter, nur @Builder.
 */
@Getter
@Builder
public class BusinessTransaction {

    /**
     * Typ der Transaktion.
     * Beispiel: "urn:epcglobal:cbv:btt:po" (Purchase Order)
     *           "urn:epcglobal:cbv:btt:desadv" (Lieferschein)
     */
    private final String type;

    /**
     * Referenz-ID der Transaktion.
     * Beispiel: "urn:epc:id:gdti:0614141.00001.1618034"
     */
    private final String value;
}
