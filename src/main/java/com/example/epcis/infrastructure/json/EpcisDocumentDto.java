package com.example.epcis.infrastructure.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * EPCIS 2.0 document envelope.
 *
 * In EPCIS 2.0 JSON-LD the @context belongs to the document, not individual events.
 * Structure: { "@context": "...", "type": "EPCISDocument", "epcisBody": { "eventList": [...] } }
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EpcisDocumentDto {

    @JsonProperty("@context")
    private final String context;

    private final String type;

    private final EpcisBody epcisBody;

    @Getter
    @Builder
    public static class EpcisBody {
        private final List<Epcis2EventDto> eventList;
    }
}
