package com.canda.epcis.application.port;

/**
 * Outbound port: writes rendered event JSON to the file-system audit trail.
 * Best-effort — failures are logged but do not roll back the transaction.
 * Implementations live in the infrastructure layer (persistence adapter).
 */
public interface EventAuditWriter {
    void write(String renderedJson);
}
