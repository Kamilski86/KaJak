package com.example.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object – repräsentiert eine Menge eines Produkts ohne Serien-EPC.
 * Wird verwendet wenn Produkte nicht einzeln serialisiert sind.
 *
 * Beispiel: 500 Einheiten der GTIN 0614141007346.
 *
 * Immutable – kein @Setter, nur @Builder.
 */
@Getter
@Builder
public class QuantityElement {

    /**
     * EPC Class – identifiziert das Produkt ohne Seriennummer.
     * Beispiel: "urn:epc:class:lgtin:0614141.107346.101"
     */
    private final String epcClass;

    /**
     * Menge der Einheiten.
     */
    private final Double quantity;

    /**
     * Maßeinheit (optional).
     * Beispiel: "KGM" (Kilogramm), "EA" (Stück)
     */
    private final String uom;
}
