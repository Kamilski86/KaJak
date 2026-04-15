package com.canda.epcis.application.digitallink;

import com.canda.epcis.domain.model.digitallink.DigitalLinkParseException;
import com.canda.epcis.domain.model.digitallink.DigitalLinkUri;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses and constructs GS1 Digital Link URIs (GS1 Digital Link Standard 1.1.2, Nov 2022).
 *
 * C&A-specific conversion constants:
 *   Company Prefix : always 7 digits
 *   Item Reference : always 6 digits
 *   SGTIN → GTIN-14 : companyPrefix(7) + itemRef(6) + GS1 Modulo-10 check = 14 digits
 *   SSCC  → SSCC-18 : extension(1) + companyPrefix(7) + serialNumber(9) + check = 18 digits
 *   GLN   → GLN-13  : companyPrefix(7) + locationRef(5) + GS1 Modulo-10 check = 13 digits
 *
 * Supported primary Application Identifiers (AI):
 *   "01" / "gtin"  → GTIN
 *   "00" / "sscc"  → SSCC
 *   "414"/ "gln"   → GLN (location)
 *   "253"/ "gdti"  → GDTI
 *   "255"/ "gcn"   → GCN
 *   "401"/ "ginc"  → GINC
 *   "402"/ "gsin"  → GSIN
 *   "8003"/"grai"  → GRAI
 *   "8004"/"giai"  → GIAI
 *
 * Supported key qualifiers:
 *   "21" / "ser"   → Serial Number (for GTIN)
 *   "10" / "lot"   → Batch/Lot Number (for GTIN)
 *   "22" / "cpv"   → Consumer Product Variant (for GTIN)
 *   "254"/ "glnx"  → GLN Extension (for GLN)
 */
@Service
public class DigitalLinkParser {

    // ─────────────────────────────────────────────────────────────────────────
    // C&A constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final int COMPANY_PREFIX_LENGTH = 7;

    // ─────────────────────────────────────────────────────────────────────────
    // AI → short name maps
    // ─────────────────────────────────────────────────────────────────────────

    private static final Map<String, String> AI_TO_KEY_NAME = Map.of(
            "01",   "gtin",
            "00",   "sscc",
            "414",  "gln",
            "253",  "gdti",
            "255",  "gcn",
            "401",  "ginc",
            "402",  "gsin",
            "8003", "grai",
            "8004", "giai"
    );

    private static final Map<String, String> KEY_NAME_TO_AI;
    static {
        Map<String, String> m = new HashMap<>();
        AI_TO_KEY_NAME.forEach((ai, name) -> m.put(name, ai));
        KEY_NAME_TO_AI = Map.copyOf(m);
    }

    private static final Map<String, String> QUALIFIER_AI_TO_NAME = Map.of(
            "21",  "ser",
            "10",  "lot",
            "22",  "cpv",
            "254", "glnx"
    );

    private static final Map<String, String> QUALIFIER_NAME_TO_AI;
    static {
        Map<String, String> m = new HashMap<>();
        QUALIFIER_AI_TO_NAME.forEach((ai, name) -> m.put(name, ai));
        QUALIFIER_NAME_TO_AI = Map.copyOf(m);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — parse
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses a GS1 Digital Link URI into a {@link DigitalLinkUri} value object.
     *
     * @param uri the Digital Link URI string
     * @return parsed value object
     * @throws DigitalLinkParseException if the URI is not a valid Digital Link
     */
    public DigitalLinkUri parse(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new DigitalLinkParseException("Digital Link URI must not be blank.");
        }

        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException e) {
            throw new DigitalLinkParseException("Invalid URI syntax: " + uri, e);
        }

        String scheme = parsed.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new DigitalLinkParseException(
                    "Digital Link URI must use http or https scheme, got: " + scheme);
        }

        String path = parsed.getPath();
        if (path == null || path.isBlank()) {
            throw new DigitalLinkParseException("Digital Link URI has no path: " + uri);
        }

        // Split path into segments (strip leading slash)
        String[] segments = path.startsWith("/") ? path.substring(1).split("/") : path.split("/");
        if (segments.length < 2) {
            throw new DigitalLinkParseException(
                    "Digital Link path must contain at least one AI and value pair: " + uri);
        }

        // First segment pair: primary AI + primary value
        String rawPrimaryAi = segments[0];
        String primaryAi    = resolveToAi(rawPrimaryAi);
        String primaryKey   = AI_TO_KEY_NAME.getOrDefault(primaryAi, rawPrimaryAi);
        String primaryValue = segments[1];

        // Remaining segment pairs: key qualifiers
        Map<String, String> qualifiers = new LinkedHashMap<>();
        for (int i = 2; i + 1 < segments.length; i += 2) {
            String qualAi = resolveToAi(segments[i]);
            qualifiers.put(qualAi, segments[i + 1]);
        }

        // Query string: data attributes
        Map<String, String> dataAttributes = parseQueryString(parsed.getQuery());

        return DigitalLinkUri.builder()
                .originalUri(uri)
                .scheme(scheme)
                .host(parsed.getHost())
                .primaryAi(primaryAi)
                .primaryKey(primaryKey)
                .primaryValue(primaryValue)
                .qualifiers(qualifiers)
                .dataAttributes(dataAttributes)
                .companyPrefixLength(COMPANY_PREFIX_LENGTH)
                .build();
    }

    /**
     * Returns true if the given URI looks like a valid GS1 Digital Link URI.
     * This is a lightweight check — no exception is thrown.
     */
    public boolean isDigitalLinkUri(String uri) {
        if (uri == null || uri.isBlank()) return false;
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (path == null || path.isBlank()) return false;
            String[] segments = path.startsWith("/") ? path.substring(1).split("/") : path.split("/");
            if (segments.length < 2) return false;
            String ai = resolveToAiSafe(segments[0]);
            return ai != null && AI_TO_KEY_NAME.containsKey(ai);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — EPC URN → Digital Link
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a GS1 EPC Pure Identity URN to a GS1 Digital Link URI.
     *
     * Supported EPC types:
     *   urn:epc:id:sgtin:{company}.{item}.{serial}
     *     → {baseUrl}/01/{gtin14}/21/{serial}
     *
     *   urn:epc:id:sscc:{company}.{serialRef}
     *     → {baseUrl}/00/{sscc18}
     *
     *   urn:epc:id:sgln:{company}.{location}.{extension}
     *     → {baseUrl}/414/{gln13}[/254/{extension}]
     *
     * @param epcUrn  the EPC URN string
     * @param baseUrl base URL, e.g. "https://id.canda.com"
     * @return Digital Link URL string
     * @throws DigitalLinkParseException if the EPC URN is not supported or malformed
     */
    public String toDigitalLink(String epcUrn, String baseUrl) {
        if (epcUrn == null || epcUrn.isBlank()) {
            throw new DigitalLinkParseException("EPC URN must not be blank.");
        }

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        if (epcUrn.startsWith("urn:epc:id:sgtin:")) {
            return sgtinToDigitalLink(epcUrn, base);
        } else if (epcUrn.startsWith("urn:epc:id:sscc:")) {
            return ssccToDigitalLink(epcUrn, base);
        } else if (epcUrn.startsWith("urn:epc:id:sgln:")) {
            return sglnToDigitalLink(epcUrn, base);
        }

        throw new DigitalLinkParseException(
                "Unsupported EPC URN type for Digital Link conversion: " + epcUrn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EPC URN → Digital Link converters
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * urn:epc:id:sgtin:{company(7)}.{item(6)}.{serial}
     *   → {base}/01/{company(7)+item(6)+check(1)}/21/{serial}
     */
    private String sgtinToDigitalLink(String epcUrn, String base) {
        String body = epcUrn.substring("urn:epc:id:sgtin:".length());
        String[] parts = body.split("\\.");
        if (parts.length != 3) {
            throw new DigitalLinkParseException("Invalid SGTIN URN format: " + epcUrn);
        }
        String company = parts[0];
        String item    = parts[1];
        String serial  = parts[2];

        if (company.length() != COMPANY_PREFIX_LENGTH) {
            throw new DigitalLinkParseException(
                    "SGTIN company prefix must be " + COMPANY_PREFIX_LENGTH +
                    " digits, got: " + company.length() + " in " + epcUrn);
        }

        String payload   = company + item;           // 13 digits
        String checkStr  = String.valueOf(gs1CheckDigit(payload));
        String gtin14    = payload + checkStr;       // 14 digits

        return base + "/01/" + gtin14 + "/21/" + serial;
    }

    /**
     * urn:epc:id:sscc:{company(7)}.{serialRef(10)}
     *   serialRef = extension(1) + serialNumber(9)
     *   → {base}/00/{extension(1)+company(7)+serialNumber(9)+check(1)}
     */
    private String ssccToDigitalLink(String epcUrn, String base) {
        String body = epcUrn.substring("urn:epc:id:sscc:".length());
        String[] parts = body.split("\\.");
        if (parts.length != 2) {
            throw new DigitalLinkParseException("Invalid SSCC URN format: " + epcUrn);
        }
        String company   = parts[0];
        String serialRef = parts[1];  // 10 digits = extension(1) + serial(9)

        if (company.length() != COMPANY_PREFIX_LENGTH) {
            throw new DigitalLinkParseException(
                    "SSCC company prefix must be " + COMPANY_PREFIX_LENGTH +
                    " digits, got: " + company.length() + " in " + epcUrn);
        }
        if (serialRef.length() != 10) {
            throw new DigitalLinkParseException(
                    "SSCC serialRef must be 10 digits (extension+serial), got: " +
                    serialRef.length() + " in " + epcUrn);
        }

        String extension = serialRef.substring(0, 1);   // extension digit
        String serial    = serialRef.substring(1);       // 9-digit serial number

        // SSCC-18 body (17 digits): extension + company + serial
        String ssccBody = extension + company + serial;
        String check    = String.valueOf(gs1CheckDigit(ssccBody));
        String sscc18   = ssccBody + check;             // 18 digits

        return base + "/00/" + sscc18;
    }

    /**
     * urn:epc:id:sgln:{company(7)}.{location(5)}.{extension}
     *   → {base}/414/{company(7)+location(5)+check(1)}[/254/{extension}]
     */
    private String sglnToDigitalLink(String epcUrn, String base) {
        String body = epcUrn.substring("urn:epc:id:sgln:".length());
        String[] parts = body.split("\\.");
        if (parts.length != 3) {
            throw new DigitalLinkParseException("Invalid SGLN URN format: " + epcUrn);
        }
        String company  = parts[0];
        String location = parts[1];
        String ext      = parts[2];

        if (company.length() != COMPANY_PREFIX_LENGTH) {
            throw new DigitalLinkParseException(
                    "SGLN company prefix must be " + COMPANY_PREFIX_LENGTH +
                    " digits, got: " + company.length() + " in " + epcUrn);
        }

        String glnBody = company + location;             // 12 digits
        String check   = String.valueOf(gs1CheckDigit(glnBody));
        String gln13   = glnBody + check;               // 13 digits

        String url = base + "/414/" + gln13;
        if (!"0".equals(ext)) {
            url += "/254/" + ext;
        }
        return url;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GS1 Modulo-10 Check Digit
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the GS1 Modulo-10 check digit for a numeric string.
     *
     * Algorithm (GS1 General Specifications, section 7.9):
     *   Starting from the rightmost digit, multiply alternating digits by 3 and 1
     *   (rightmost digit × 3, next × 1, next × 3, ...).
     *   Sum all products. Check digit = (10 - (sum % 10)) % 10.
     *
     * @param digits numeric string without check digit
     * @return single check digit (0–9)
     * @throws DigitalLinkParseException if input contains non-numeric characters
     */
    public int gs1CheckDigit(String digits) {
        if (digits == null || digits.isEmpty()) {
            throw new DigitalLinkParseException("Cannot compute check digit on empty string.");
        }
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new DigitalLinkParseException(
                        "Non-numeric character '" + c + "' in GS1 identifier: " + digits);
            }
            int digit = c - '0';
            // Position from right (1-based): rightmost = position 1
            int posFromRight = digits.length() - i;
            // Odd positions from right → multiply by 3
            int multiplier = (posFromRight % 2 == 1) ? 3 : 1;
            sum += digit * multiplier;
        }
        return (10 - (sum % 10)) % 10;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a path segment to a numeric AI string.
     * Accepts both numeric AIs ("01") and short names ("gtin").
     * Throws {@link DigitalLinkParseException} if not recognised.
     */
    private String resolveToAi(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new DigitalLinkParseException("Empty AI segment in Digital Link path.");
        }
        // Already numeric AI?
        if (AI_TO_KEY_NAME.containsKey(segment)) return segment;
        if (QUALIFIER_AI_TO_NAME.containsKey(segment)) return segment;
        // Short name?
        String fromName = KEY_NAME_TO_AI.get(segment.toLowerCase());
        if (fromName != null) return fromName;
        String fromQualName = QUALIFIER_NAME_TO_AI.get(segment.toLowerCase());
        if (fromQualName != null) return fromQualName;
        // Unknown — pass through (non-standard AI)
        return segment;
    }

    /** Same as resolveToAi but returns null instead of throwing for isDigitalLinkUri check. */
    private String resolveToAiSafe(String segment) {
        try { return resolveToAi(segment); } catch (DigitalLinkParseException e) { return null; }
    }

    /** Parses a query string into a key-value map. Handles null and empty gracefully. */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return result;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else if (!pair.isBlank()) {
                result.put(pair, "");
            }
        }
        return result;
    }
}
