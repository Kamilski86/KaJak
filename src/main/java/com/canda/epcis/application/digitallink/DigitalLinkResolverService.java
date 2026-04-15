package com.canda.epcis.application.digitallink;

import com.canda.epcis.application.inventory.EpcNotFoundException;
import com.canda.epcis.application.inventory.InventoryQueryService;
import com.canda.epcis.config.DigitalLinkConfig;
import com.canda.epcis.domain.model.EpcState;
import com.canda.epcis.domain.model.digitallink.DigitalLinkUri;
import com.canda.epcis.domain.model.digitallink.ResolverResponse;
import com.canda.epcis.domain.model.digitallink.ResolverResponse.Link;
import com.canda.epcis.domain.model.digitallink.ResolverResponse.LinkSet;
import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves GS1 Digital Link URIs to available data services.
 *
 * Links together:
 *   1. EPCIS Event History   — Phase 1 Query API
 *   2. Current Inventory     — Phase 2 Inventory Service
 *   3. SSCC pallet content   — Phase 2 Inventory Service (when identifier is SSCC)
 *
 * Link types follow the GS1 Web Vocabulary:
 *   gs1:epcis    — EPCIS Event History
 *   gs1:location — Current inventory location
 */
@Service
public class DigitalLinkResolverService {

    private static final Logger log = LoggerFactory.getLogger(DigitalLinkResolverService.class);

    private final DigitalLinkParser parser;
    private final EpcisEventRepository eventRepository;
    private final InventoryQueryService inventoryQueryService;
    private final DigitalLinkConfig config;

    public DigitalLinkResolverService(DigitalLinkParser parser,
                                      EpcisEventRepository eventRepository,
                                      InventoryQueryService inventoryQueryService,
                                      DigitalLinkConfig config) {
        this.parser = parser;
        this.eventRepository = eventRepository;
        this.inventoryQueryService = inventoryQueryService;
        this.config = config;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resolve
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the Digital Link URI and returns a ResolverResponse with all available links.
     *
     * @param digitalLinkUri raw Digital Link URI string
     * @return resolver response
     */
    public ResolverResponse resolve(String digitalLinkUri) {
        DigitalLinkUri dlUri = parser.parse(digitalLinkUri);
        return resolveByDigitalLinkUri(dlUri);
    }

    /**
     * Resolves directly from a known EPC URN (internal use).
     *
     * @param epcUrn the EPC Pure Identity URN
     * @return resolver response
     */
    public ResolverResponse resolveByEpcUrn(String epcUrn) {
        // Build a minimal DigitalLinkUri from the EPC URN for resolution
        String dlUrl = parser.toDigitalLink(epcUrn, config.getBaseUrl());
        DigitalLinkUri dlUri = parser.parse(dlUrl);
        return resolveByDigitalLinkUri(dlUri);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event History
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all EPCIS events matching the identifier in the Digital Link URI.
     * Uses the EPC URN (converted from the Digital Link) to search event history.
     *
     * @param dlUri parsed Digital Link URI
     * @return list of matching EPCIS event entities
     */
    public List<EpcisEventEntity> getEventHistory(DigitalLinkUri dlUri) {
        Optional<String> epcUrn = dlUri.toEpcUrn();
        if (epcUrn.isEmpty()) {
            log.warn("Cannot determine EPC URN for Digital Link — event history unavailable: {}",
                    dlUri.getOriginalUri());
            return List.of();
        }

        return eventRepository.search(
                null, null, null, null, null, null, null,
                epcUrn.get(), null, null, null,
                PageRequest.of(0, 1000)
        ).getContent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Current Location
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current inventory state for the EPC identified by the Digital Link.
     *
     * @param dlUri parsed Digital Link URI
     * @return EpcState if found, empty otherwise
     */
    public Optional<EpcState> getCurrentLocation(DigitalLinkUri dlUri) {
        Optional<String> epcUrn = dlUri.toEpcUrn();
        if (epcUrn.isEmpty()) return Optional.empty();

        try {
            return Optional.of(inventoryQueryService.getEpcState(epcUrn.get()));
        } catch (EpcNotFoundException e) {
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private ResolverResponse resolveByDigitalLinkUri(DigitalLinkUri dlUri) {
        Optional<String> epcUrnOpt = dlUri.toEpcUrn();

        String identifier     = epcUrnOpt.orElse(dlUri.getPrimaryValue());
        String identifierType = deriveIdentifierType(dlUri.getPrimaryAi());

        List<Link> links = new ArrayList<>();

        // gs1:epcis — EPCIS Event History link
        epcUrnOpt.ifPresent(epcUrn -> {
            String epcisHref = config.getBaseUrl() +
                               "/epcis/query/events?epcMatch=" + epcUrn;
            links.add(Link.builder()
                    .linkType("gs1:epcis")
                    .href(epcisHref)
                    .title("EPCIS Event History")
                    .type("application/json")
                    .build());
        });

        // gs1:location — current inventory location
        epcUrnOpt.ifPresent(epcUrn -> {
            try {
                inventoryQueryService.getEpcState(epcUrn);
                String locationHref = config.getBaseUrl() +
                                      "/inventory/epc?epc=" + epcUrn;
                links.add(Link.builder()
                        .linkType("gs1:location")
                        .href(locationHref)
                        .title("Current Location")
                        .type("application/json")
                        .build());
            } catch (EpcNotFoundException e) {
                log.debug("No inventory state for EPC: {}", epcUrn);
            }
        });

        // For SSCC: add pallet content link
        if ("00".equals(dlUri.getPrimaryAi())) {
            epcUrnOpt.ifPresent(epcUrn -> {
                String palletHref = config.getBaseUrl() +
                                    "/inventory/pallet/" + epcUrn;
                links.add(Link.builder()
                        .linkType("gs1:epcis")
                        .href(palletHref)
                        .title("Pallet Content")
                        .type("application/json")
                        .build());
            });
        }

        LinkSet linkSet = LinkSet.builder()
                .context(config.getBaseUrl())
                .links(links)
                .build();

        return ResolverResponse.builder()
                .identifier(identifier)
                .identifierType(identifierType)
                .linkSet(linkSet)
                .build();
    }

    private String deriveIdentifierType(String primaryAi) {
        return switch (primaryAi) {
            case "01"  -> "SGTIN";
            case "00"  -> "SSCC";
            case "414" -> "SGLN";
            case "253" -> "GDTI";
            case "255" -> "GCN";
            case "401" -> "GINC";
            case "402" -> "GSIN";
            default    -> primaryAi;
        };
    }
}
