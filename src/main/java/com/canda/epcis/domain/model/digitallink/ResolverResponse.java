package com.canda.epcis.domain.model.digitallink;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Response from the GS1 Digital Link Resolver.
 *
 * Contains links to all available data services for the identified object.
 * Format follows the GS1 Digital Link 1.1.2 Resolver Response structure.
 *
 * Example linkTypes (GS1 Web Vocabulary):
 *   gs1:epcis    — EPCIS Event History endpoint
 *   gs1:pip      — Product Information Page
 *   gs1:location — Current location (from Inventory Service)
 *   gs1:certificationInfo — Product certifications (placeholder)
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolverResponse {

    /** The resolved EPC URN, e.g. "urn:epc:id:sgtin:4056019.010532.12345". */
    private final String identifier;

    /** Identifier type, e.g. "SGTIN", "SSCC", "SGLN". */
    private final String identifierType;

    @Singular
    private final List<LinkSet> linkSets;

    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkSet {

        /** Context base URL, e.g. "https://id.canda.com". */
        private final String context;

        @Singular
        private final List<Link> links;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {

        /** GS1 Web Vocabulary link type, e.g. "gs1:epcis", "gs1:location". */
        private final String linkType;

        /** URL of the target service. */
        private final String href;

        /** Human-readable label. */
        private final String title;

        /** MIME type of the target resource, e.g. "application/json". */
        private final String type;
    }
}
