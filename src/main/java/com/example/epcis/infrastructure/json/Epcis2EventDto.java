package infrastructure.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Data Transfer Object – repräsentiert ein EPCIS 2.0 JSON Event.
 *
 * Wird ausschließlich für die JSON-Ausgabe verwendet.
 * Niemals direkt im Domain-Code verwenden.
 *
 * @JsonInclude(NON_NULL) → fehlende Felder werden nicht als "null" ausgegeben.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Epcis2EventDto {

    /**
     * EPCIS 2.0 JSON-LD @context – Pflichtfeld laut GS1 Standard.
     */
    @JsonProperty("@context")
    private final String context;

    /**
     * Event-Typ als String.
     * Beispiel: "ObjectEvent", "AggregationEvent"
     */
    private final String type;

    /**
     * Eindeutige Event-ID (URI).
     * Beispiel: "ni:///sha-256;...?ver=CBV2.0"
     */
    private final String eventID;

    /**
     * Zeitstempel des Events im ISO 8601 Format.
     * Beispiel: "2024-01-15T10:30:00+02:00"
     */
    private final String eventTime;

    /**
     * Zeitzone des Events.
     * Beispiel: "+02:00"
     */
    private final String eventTimeZoneOffset;

    /**
     * Zeitstempel der Aufzeichnung.
     */
    private final String recordTime;

    /**
     * Aktion: ADD, OBSERVE oder DELETE.
     */
    private final String action;

    /**
     * Business Step URI.
     * Beispiel: "urn:epcglobal:cbv:bizstep:shipping"
     */
    private final String bizStep;

    /**
     * Disposition URI.
     * Beispiel: "urn:epcglobal:cbv:disp:in_transit"
     */
    private final String disposition;

    /**
     * Read Point als Objekt mit "id"-Feld (EPCIS 2.0 Struktur).
     */
    private final IdObject readPoint;

    /**
     * Business Location als Objekt mit "id"-Feld.
     */
    private final IdObject bizLocation;

    /**
     * EPC-Liste (nur ObjectEvent).
     */
    private final List<String> epcList;

    /**
     * Mengenliste (nur ObjectEvent, optional).
     */
    private final List<QuantityElementDto> quantityList;

    /**
     * Parent ID (nur AggregationEvent).
     */
    private final String parentID;

    /**
     * Child EPC-Liste (nur AggregationEvent).
     */
    private final List<String> childEPCs;

    /**
     * Child Mengenliste (nur AggregationEvent, optional).
     */
    private final List<QuantityElementDto> childQuantityList;

    /**
     * Business Transaktionen.
     */
    private final List<BizTransactionDto> bizTransactionList;

    // ─────────────────────────────────────────────
    // INNER CLASSES – Value Objects für JSON-Struktur
    // ─────────────────────────────────────────────

    /**
     * EPCIS 2.0 Struktur für readPoint und bizLocation.
     * Beispiel: { "id": "urn:epc:id:sgln:..." }
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IdObject {
        private final String id;
    }

    /**
     * EPCIS 2.0 Struktur für quantityElement.
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuantityElementDto {
        private final String epcClass;
        private final Double quantity;
        private final String uom;
    }

    /**
     * EPCIS 2.0 Struktur für bizTransaction.
     * Beispiel: { "type": "urn:epcglobal:cbv:btt:po", "bizTransaction": "urn:..." }
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BizTransactionDto {
        private final String type;
        private final String bizTransaction;
    }
}
