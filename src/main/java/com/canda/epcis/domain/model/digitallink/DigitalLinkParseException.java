package com.canda.epcis.domain.model.digitallink;

/**
 * Thrown when a URI cannot be parsed as a valid GS1 Digital Link URI 1.1.2.
 *
 * Handled by GlobalExceptionHandler → HTTP 400 Bad Request.
 */
public class DigitalLinkParseException extends RuntimeException {

    public DigitalLinkParseException(String message) {
        super(message);
    }

    public DigitalLinkParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
