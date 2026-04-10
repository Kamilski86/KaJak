package com.canda.epcis.infrastructure.json;

/**
 * Wird geworfen wenn ein Domain-Event nicht in EPCIS 2.0 JSON gerendert werden kann.
 * Ursachen: nicht unterstützter Event-Typ, Jackson-Fehler, ungültige Daten.
 */
public class EpcisRenderingException extends RuntimeException {

    public EpcisRenderingException(String message) {
        super(message);
    }

    public EpcisRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
