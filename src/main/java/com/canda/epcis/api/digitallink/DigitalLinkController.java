package com.canda.epcis.api.digitallink;

import com.canda.epcis.application.digitallink.DigitalLinkParser;
import com.canda.epcis.application.digitallink.DigitalLinkResolverService;
import com.canda.epcis.domain.model.digitallink.DigitalLinkUri;
import com.canda.epcis.domain.model.digitallink.ResolverResponse;
import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for GS1 Digital Link resolution (Phase 4 — Block B).
 *
 * GET /digitallink/resolve          — resolve Digital Link URI to available services
 * GET /digitallink/resolve/{ai}/{v} — resolve by AI + value directly
 * GET /digitallink/history          — EPCIS event history for identifier
 * GET /digitallink/location         — current inventory location
 * GET /digitallink/parse            — parse URI (debug / testing)
 * GET /digitallink/convert          — convert EPC URN to Digital Link URL
 */
@RestController
@RequestMapping("/digitallink")
public class DigitalLinkController {

    private final DigitalLinkParser parser;
    private final DigitalLinkResolverService resolverService;

    public DigitalLinkController(DigitalLinkParser parser,
                                 DigitalLinkResolverService resolverService) {
        this.parser = parser;
        this.resolverService = resolverService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/resolve?uri=...
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a full GS1 Digital Link URI to a ResolverResponse with all available links.
     *
     * @param uri full Digital Link URI, e.g. https://id.example.com/01/04012345999990/21/ABC123
     */
    @GetMapping("/resolve")
    public ResponseEntity<ResolverResponse> resolve(@RequestParam String uri) {
        ResolverResponse response = resolverService.resolve(uri);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/resolve/{ai}/{value}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Short-form resolve: provide AI and value directly in the path.
     * Example: GET /digitallink/resolve/01/04012345999990
     *
     * @param ai    Application Identifier, e.g. "01" or "gtin"
     * @param value primary identifier value
     */
    @GetMapping("/resolve/{ai}/{value}")
    public ResponseEntity<ResolverResponse> resolveByAi(
            @PathVariable String ai,
            @PathVariable String value) {

        // Build a synthetic Digital Link URI from the AI + value and resolve it
        String syntheticUri = "https://placeholder.gs1.org/" + ai + "/" + value;
        ResolverResponse response = resolverService.resolve(syntheticUri);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/history?uri=...
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the full EPCIS event history for the identifier in the Digital Link URI.
     *
     * @param uri full Digital Link URI
     */
    @GetMapping("/history")
    public ResponseEntity<List<EpcisEventEntity>> history(@RequestParam String uri) {
        DigitalLinkUri dlUri = parser.parse(uri);
        List<EpcisEventEntity> events = resolverService.getEventHistory(dlUri);
        return ResponseEntity.ok(events);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/location?uri=...
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current inventory location for the identifier in the Digital Link URI.
     * Returns 404 if the EPC is not known to the inventory service.
     *
     * @param uri full Digital Link URI
     */
    @GetMapping("/location")
    public ResponseEntity<?> location(@RequestParam String uri) {
        DigitalLinkUri dlUri = parser.parse(uri);
        Optional<?> location = resolverService.getCurrentLocation(dlUri);
        return location
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/parse?uri=...
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the Digital Link URI and returns its structural breakdown.
     * Intended for debugging and testing only.
     *
     * @param uri full Digital Link URI
     */
    @GetMapping("/parse")
    public ResponseEntity<ParseResponse> parse(@RequestParam String uri) {
        DigitalLinkUri dlUri = parser.parse(uri);
        ParseResponse response = new ParseResponse(
                dlUri.getOriginalUri(),
                dlUri.getScheme(),
                dlUri.getHost(),
                dlUri.getPrimaryAi(),
                dlUri.getPrimaryKey(),
                dlUri.getPrimaryValue(),
                dlUri.getQualifiers(),
                dlUri.getDataAttributes(),
                dlUri.toEpcUrn().orElse(null)
        );
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /digitallink/convert?epc=...&baseUrl=...
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a GS1 EPC Pure Identity URN to a GS1 Digital Link URL.
     *
     * @param epc     EPC URN, e.g. urn:epc:id:sgtin:4056019.010532.12345
     * @param baseUrl optional base URL override, defaults to configured value
     */
    @GetMapping("/convert")
    public ResponseEntity<Map<String, String>> convert(
            @RequestParam String epc,
            @RequestParam(required = false, defaultValue = "https://id.canda.com") String baseUrl) {

        String digitalLinkUrl = parser.toDigitalLink(epc, baseUrl);
        return ResponseEntity.ok(Map.of(
                "epcUrn", epc,
                "digitalLinkUrl", digitalLinkUrl
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response record
    // ─────────────────────────────────────────────────────────────────────────

    public record ParseResponse(
            String originalUri,
            String scheme,
            String host,
            String primaryAi,
            String primaryKey,
            String primaryValue,
            Map<String, String> qualifiers,
            Map<String, String> dataAttributes,
            String epcUrn) {}
}
