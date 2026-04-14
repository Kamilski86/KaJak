package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Inhalt einer SSCC (Palette) — welche SGTINs sind aktuell zugeordnet.
 * Wird aus AggregationEvents berechnet (REQ205).
 *
 * ADD    → SGTINs zur SSCC hinzufügen
 * DELETE → SGTINs von SSCC entfernen
 *          wenn childEPCs leer → alle SGTINs entfernen (REQ215)
 */
@Getter
@Builder
public class SsccContent {

    private final String ssccUrn;
    private final List<String> childEpcs;
    private final String bizLocation;       // Standort der SSCC (von letztem Event)
    private final OffsetDateTime lastUpdated;
}
