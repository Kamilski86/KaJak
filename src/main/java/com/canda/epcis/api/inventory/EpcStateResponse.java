package com.canda.epcis.api.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EpcStateResponse {

    private final String epcUrn;
    private final String currentStatus;
    private final String bizLocation;
    private final String lastEventId;
    private final String sscc;
    private final OffsetDateTime lastEventTime;
    private final OffsetDateTime createDate;
    private final OffsetDateTime updateDate;

    // Disposition-Timestamps (REQ401)
    private final OffsetDateTime encodedAt;
    private final OffsetDateTime inProgressAt;
    private final OffsetDateTime inTransitAt;
    private final OffsetDateTime accessibleForCustomerAt;
    private final OffsetDateTime availableNotAccessibleForCustomerAt;
    private final OffsetDateTime retiredAt;
    private final OffsetDateTime soldAt;
}
