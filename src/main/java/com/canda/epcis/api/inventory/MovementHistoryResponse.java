package com.canda.epcis.api.inventory;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MovementHistoryResponse {

    private final String epcUrn;
    private final int totalCount;
    private final List<MovementEntry> movements;

    @Getter
    @Builder
    public static class MovementEntry {
        private final String eventId;
        private final String eventType;
        private final String action;
        private final String bizStep;
        private final String disposition;
        private final String bizLocation;
        private final String readPoint;
        private final OffsetDateTime eventTime;
    }
}
