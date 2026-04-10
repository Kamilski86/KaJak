package com.example.epcis.infrastructure.xml;

/**
 * Thrown when EPCIS XML cannot be structurally parsed (malformed DOM).
 * Distinct from EpcisValidationException, which signals XSD schema violations.
 * Both map to HTTP 400 since the fault is in the client request.
 */
public class EpcisParsingException extends RuntimeException {

    public EpcisParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
