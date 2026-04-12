package com.canda.epcis.api.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * HTTP Response für GET /epcis/query/events.
 * Events sind geparste JSON-Payloads aus der DB (EPCIS 2.0 JSON).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResponse {

    private final int totalResults;
    private final List<Object> events;
}
