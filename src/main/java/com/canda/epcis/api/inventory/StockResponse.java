package com.canda.epcis.api.inventory;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StockResponse {

    private final String gln;
    private final int totalCount;
    private final List<EpcStateResponse> items;
}
