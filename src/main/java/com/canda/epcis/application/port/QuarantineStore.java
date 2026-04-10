package com.canda.epcis.application.port;

/**
 * Outbound port: persists an event fragment that could not be safely converted.
 */
public interface QuarantineStore {
    void quarantine(String reason, String errorCode, String rawFragment);
}
