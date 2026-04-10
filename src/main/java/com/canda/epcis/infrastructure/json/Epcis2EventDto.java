package com.canda.epcis.infrastructure.json;

import com.canda.epcis.domain.model.Action;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for EPCIS 2.0 JSON event output.
 * Used exclusively for serialisation — never in domain code.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Epcis2EventDto {

    private final String type;
    private final String eventID;
    private final OffsetDateTime eventTime;
    private final String eventTimeZoneOffset;
    private final OffsetDateTime recordTime;
    private final Action action;
    private final String bizStep;
    private final String disposition;
    private final IdObject readPoint;
    private final IdObject bizLocation;
    private final List<String> epcList;
    private final List<QuantityElementDto> quantityList;
    private final String parentID;
    private final List<String> childEPCs;
    private final List<QuantityElementDto> childQuantityList;
    private final List<BizTransactionDto> bizTransactionList;
    private final List<SourceDto> sourceList;
    private final List<DestinationDto> destinationList;
    private final ErrorDeclarationDto errorDeclaration;
    /** ILMD fields rendered as a JSON object (ObjectEvent only). */
    private final Map<String, String> ilmd;
    /** Extension fields rendered as a JSON object. */
    private final Map<String, String> extension;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IdObject {
        private final String id;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuantityElementDto {
        private final String epcClass;
        private final Double quantity;
        private final String uom;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BizTransactionDto {
        private final String type;
        private final String bizTransaction;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceDto {
        private final String type;
        private final String source;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DestinationDto {
        private final String type;
        private final String destination;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDeclarationDto {
        private final OffsetDateTime declaringTime;
        private final String reason;
        private final List<String> correctingEventIDs;
    }
}
