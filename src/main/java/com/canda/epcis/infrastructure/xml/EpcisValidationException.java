package com.canda.epcis.infrastructure.xml;

/**
 * Wird geworfen wenn eingehendes XML nicht gegen das EPCIS 1.2 XSD-Schema validiert.
 * Der REST-Controller fängt diese Exception und gibt HTTP 400 zurück.
 */
public class EpcisValidationException extends RuntimeException {

    public EpcisValidationException(String message) {
        super(message);
    }

    public EpcisValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
