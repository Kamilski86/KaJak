package com.canda.epcis.domain.model;

/**
 * EPCIS Action – definiert was mit einem EPC oder einer Aggregation passiert ist.
 * Gilt für ObjectEvent und AggregationEvent.
 */
public enum Action {

    /** Objekt taucht neu auf / wird einem Parent hinzugefügt */
    ADD,

    /** Objekt wird beobachtet, kein Zustandswechsel */
    OBSERVE,

    /** Objekt verschwindet / wird vom Parent entfernt */
    DELETE
}
