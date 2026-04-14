package com.canda.epcis.api.inventory;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class PalletResponse {

    private final String ssccUrn;
    private final String bizLocation;
    private final int childCount;
    private final List<String> childEpcs;
    private final OffsetDateTime lastUpdated;
}
