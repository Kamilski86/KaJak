package com.canda.epcis.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Letzter bekannter Zustand einer SGTIN.
 * Entspricht REQ401 — EPC State DB.
 *
 * Felder gemäß REQ401:
 * - epcUrn       : SGTIN als URN
 * - currentStatus: letzter bizStep/disposition-basierter Status
 * - bizLocation  : letzter bekannter Standort (SGLN)
 * - lastEventId  : eventID des letzten verarbeiteten Events
 * - sscc         : SSCC aus erstem Packing AggregationEvent (falls vorhanden)
 *
 * Timestamp-Felder (REQ401):
 * Jeder Disposition-Wert bekommt seinen eigenen Timestamp.
 * updateDate: technischer Wert — wird bei jeder Änderung auf now() gesetzt.
 * createDate: wird einmalig beim ersten Erfassen gesetzt.
 *
 * Update-Regel (REQ401 + REQ217):
 * Ein Event aktualisiert bizLocation und lastEventId NUR wenn eventTime
 * des neuen Events NEUER ist als das letzte bereits verarbeitete Event.
 * Out-of-Order Events werden gespeichert aber ändern den aktuellen Status NICHT.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpcState {

    private String epcUrn;
    private String currentStatus;       // z.B. "in_transit", "in_progress", "encoded"
    private String bizLocation;         // SGLN URI
    private String lastEventId;
    private String sscc;                // SSCC URI, optional
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;
    private OffsetDateTime lastEventTime;

    // Disposition-spezifische Timestamps (REQ401)
    // Nur der Timestamp der Disposition wird gesetzt wenn das Event diese Disposition hat
    // und das Event neuer ist als der bestehende Timestamp für diese Disposition
    private OffsetDateTime encodedAt;
    private OffsetDateTime inProgressAt;
    private OffsetDateTime inTransitAt;
    private OffsetDateTime accessibleForCustomerAt;
    private OffsetDateTime availableNotAccessibleForCustomerAt;
    private OffsetDateTime retiredAt;
    private OffsetDateTime soldAt;
}
