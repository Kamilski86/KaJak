package com.canda.epcis.domain.service;

/**
 * Thrown when a rendered EPCIS 2.0 JSON document fails validation against
 * the official GS1 EPCIS 2.0 JSON Schema.
 * This indicates a bug in the renderer or an unsupported mapping case —
 * it must never be caused by valid input data alone.
 * Maps to HTTP 500 (Internal Server Error): the input was valid but the
 * system produced non-conformant output.
 */
public class Epcis2SchemaValidationException extends RuntimeException {

    public Epcis2SchemaValidationException(String message) {
        super(message);
    }

    public Epcis2SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
