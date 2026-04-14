package com.canda.epcis.application.inventory;

/**
 * Thrown when an EPC (SGTIN or SSCC) is not found in the inventory.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class EpcNotFoundException extends RuntimeException {

    public EpcNotFoundException(String epcUrn) {
        super("EPC not found: " + epcUrn);
    }
}
