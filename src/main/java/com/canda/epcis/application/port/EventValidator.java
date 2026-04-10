package com.canda.epcis.application.port;

/**
 * Inbound port: validates a raw EPCIS 1.2 XML document (Stage 1 — structural/XSD).
 * Throws a domain-visible exception on failure; returns normally on success.
 */
public interface EventValidator {
    void validate(String xml);
}
