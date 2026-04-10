package com.example.epcis.domain.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.List;



/**
 * Basisklasse für alle EPCIS Event-Typen.
 * Enthält alle Felder die ObjectEvent und AggregationEvent gemeinsam haben.
 */
@Getter
@SuperBuilder
public abstract class EpcisEvent {

    public abstract String getEventTypeName();

    /** Eindeutige Event-ID (wird in EPCIS 2.0 als @context-konformes Feld gesetzt) */
    private String eventId;

    /** Zeitstempel des Events */
    private OffsetDateTime eventTime;

    /** Zeitzone als String, z.B. "+02:00" */
    private String eventTimeZoneOffset;

    /** Zeitstempel wann das Event aufgezeichnet wurde */
    private OffsetDateTime recordTime;

    /**
     * Business Step – beschreibt den Schritt im Geschäftsprozess.
     * Beispiel: "urn:epcglobal:cbv:bizstep:shipping"
     */
    private String bizStep;

    /**
     * Disposition – Zustand des Objekts nach dem Event.
     * Beispiel: "urn:epcglobal:cbv:disp:in_transit"
     */
    private String disposition;

    /** Leseort des Events */
    private String readPoint;

    /** Geschäftsort des Events */
    private String bizLocation;

    /** Liste der Geschäftstransaktionen die mit diesem Event verbunden sind */
    private List<BusinessTransaction> bizTransactionList;

    private Action action;
}
