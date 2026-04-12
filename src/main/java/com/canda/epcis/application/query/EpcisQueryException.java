package com.canda.epcis.application.query;

/**
 * Thrown when a query cannot be fulfilled — e.g. requested event does not exist.
 * Maps to HTTP 404 in the QueryController.
 */
public class EpcisQueryException extends RuntimeException {

    public EpcisQueryException(String message) {
        super(message);
    }
}
