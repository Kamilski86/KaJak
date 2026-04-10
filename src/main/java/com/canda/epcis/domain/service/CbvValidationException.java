package com.canda.epcis.domain.service;

/**
 * Thrown when an EPCIS event contains a vocabulary field value that is not
 * permitted by the GS1 Core Business Vocabulary (CBV) standard.
 * Maps to HTTP 422 (Unprocessable Entity) — the XML is structurally valid
 * but semantically incorrect per the business vocabulary.
 */
public class CbvValidationException extends RuntimeException {

    public CbvValidationException(String message) {
        super(message);
    }

    public CbvValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
