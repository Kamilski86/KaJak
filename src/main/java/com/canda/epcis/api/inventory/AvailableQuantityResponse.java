package com.canda.epcis.api.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GSPM-kompatibles Response-Format für verfügbare Mengen (REQ301).
 * Entspricht DM RFIDInventory2GSPM.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailableQuantityResponse {

    private final OffsetDateTime timestamp;
    private final String messageId;
    private final List<LocationQuantity> locations;

    @Getter
    @Builder
    public static class LocationQuantity {
        private final String sgln;
        private final List<GtinQuantity> items;
    }

    @Getter
    @Builder
    public static class GtinQuantity {
        /** GTIN-13 numerisch (ohne Prüfziffer-Berechnung, direkte Extraktion aus SGTIN URN). */
        private final String gtin;
        private final int qty;
    }
}
