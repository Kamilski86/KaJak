package com.canda.epcis.domain.model.digitallink;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

/**
 * Represents a parsed GS1 Digital Link URI (GS1 Digital Link Standard 1.1.2, Nov 2022).
 * Immutable Value Object.
 *
 * Examples:
 *   https://id.example.com/01/04012345999990/21/ABC123
 *     → primaryAi=01, primaryKey=gtin, primaryValue=04012345999990
 *       qualifiers={21=ABC123}
 *
 *   https://id.example.com/00/340123451111111111
 *     → primaryAi=00, primaryKey=sscc, primaryValue=340123451111111111
 *
 *   https://id.example.com/414/9521141111116
 *     → primaryAi=414, primaryKey=gln, primaryValue=9521141111116
 *
 * Supported primary Application Identifiers (AIs):
 *   01  GTIN, 00  SSCC, 414  GLN, 253  GDTI, 255  GCN,
 *   401  GINC, 402  GSIN, 8003  GRAI, 8004  GIAI
 *
 * Supported key qualifiers:
 *   21  ser (Serial Number), 10  lot (Batch/Lot),
 *   22  cpv (Consumer Product Variant), 254  glnx (GLN Extension)
 *
 * EPC URN conversion note:
 *   Converting GTIN-14 back to SGTIN URN requires knowing the GS1 Company Prefix
 *   length, which is not encoded in the GTIN itself. This implementation uses
 *   {@code companyPrefixLength} (default 7) for that split. When the Digital Link
 *   was produced by {@code DigitalLinkParser.toDigitalLink(epcUrn)}, the split is
 *   lossless because companyPrefixLength was set during construction. For externally
 *   sourced Digital Links, set companyPrefixLength explicitly on the builder.
 */
@Getter
@Builder
public class DigitalLinkUri {

    private final String originalUri;

    /** URI scheme, e.g. "https". */
    private final String scheme;

    /** Host (and optional port), e.g. "id.example.com". */
    private final String host;

    /** Primary AI numeric string, e.g. "01", "00", "414". */
    private final String primaryAi;

    /** Human-readable primary key name, e.g. "gtin", "sscc", "gln". */
    private final String primaryKey;

    /** Primary key value as-is from the URI path, e.g. "04012345999990". */
    private final String primaryValue;

    /**
     * Key qualifiers extracted from the URI path.
     * Key = AI string (e.g. "21"), Value = qualifier value (e.g. "ABC123").
     */
    private final Map<String, String> qualifiers;

    /**
     * Data attributes from the URI query string.
     * Key = parameter name, Value = parameter value.
     */
    private final Map<String, String> dataAttributes;

    /**
     * GS1 Company Prefix length used for EPC URN conversion.
     * Default is 7 digits (most common in consumer goods / retail).
     * Set explicitly on the builder when a different prefix length is known.
     */
    @Builder.Default
    private final int companyPrefixLength = 7;

    // ─────────────────────────────────────────────────────────────────────────
    // EPC URN conversion
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts this Digital Link back to a GS1 EPC Pure Identity URN.
     *
     * Supported conversions:
     *   AI 01 (GTIN) + AI 21 (serial) → urn:epc:id:sgtin:{company}.{item}.{serial}
     *   AI 00 (SSCC)                  → urn:epc:id:sscc:{company}.{serialRef}
     *   AI 414 (GLN)                  → urn:epc:id:sgln:{company}.{location}.{ext}
     *
     * Returns empty when:
     *   - The AI is not one of the above
     *   - GTIN without serial qualifier (AI 21) — class-level, no instance URN
     *   - The numeric value does not match expected length
     */
    public Optional<String> toEpcUrn() {
        return switch (primaryAi) {
            case "01" -> toSgtinUrn();
            case "00" -> toSsccUrn();
            case "414" -> toSglnUrn();
            default   -> Optional.empty();
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private converters
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GTIN-14 (14 digits) + serial (AI 21) → urn:epc:id:sgtin:{company}.{item}.{serial}
     *
     * C&A convention (companyPrefixLength = 7):
     *   GTIN-14 = companyPrefix(7) + itemRef(6) + checkDigit(1) = 14 digits
     *   No leading-zero padding — the 7+6 combination fills 13 digits directly.
     *
     * Algorithm:
     *   1. GTIN must be exactly 14 digits
     *   2. Strip check digit (last digit) → 13 digits
     *   3. Split: company = first companyPrefixLength digits
     *             item    = remaining (13 - companyPrefixLength) digits
     *   4. Append serial from qualifier AI 21
     */
    private Optional<String> toSgtinUrn() {
        String serial = qualifiers != null ? qualifiers.get("21") : null;
        if (serial == null) return Optional.empty();
        if (primaryValue == null || primaryValue.length() != 14) return Optional.empty();

        // Strip check digit (last digit) → 13 data digits
        String digits13 = primaryValue.substring(0, 13);

        if (digits13.length() < companyPrefixLength) return Optional.empty();

        String company = digits13.substring(0, companyPrefixLength);
        String item    = digits13.substring(companyPrefixLength);

        return Optional.of("urn:epc:id:sgtin:" + company + "." + item + "." + serial);
    }

    /**
     * SSCC-18 (18 digits) → urn:epc:id:sscc:{company}.{serialRef}
     *
     * GS1 SSCC-18 structure:
     *   extensionDigit(1) + companyPrefix(N) + serialNumber(16-N) + checkDigit(1) = 18
     *
     * C&A convention (companyPrefixLength = 7):
     *   extensionDigit(1) + companyPrefix(7) + serialNumber(9) + checkDigit(1) = 18
     *
     * EPC SSCC URN serialRef = extensionDigit(1) + serialNumber(9) = 10 digits.
     * The extension digit is the FIRST digit of the 18-digit SSCC value.
     *
     * Algorithm:
     *   1. SSCC must be exactly 18 digits
     *   2. extension = primaryValue[0]
     *   3. company   = primaryValue[1 .. 1+companyPrefixLength]
     *   4. serial    = primaryValue[1+companyPrefixLength .. 17]  (strip check digit at pos 17)
     *   5. serialRef = extension + serial  (10 digits)
     */
    private Optional<String> toSsccUrn() {
        if (primaryValue == null || primaryValue.length() != 18) return Optional.empty();

        // SSCC-18: ext(1) + company(7) + serial(9) + check(1) = 18
        String extension  = String.valueOf(primaryValue.charAt(0));
        int    companyEnd = 1 + companyPrefixLength;               // = 8 for 7-digit prefix
        if (companyEnd + 9 > 17) return Optional.empty();          // guard

        String company    = primaryValue.substring(1, companyEnd);
        String serial     = primaryValue.substring(companyEnd, 17); // 9 digits, excludes check
        String serialRef  = extension + serial;                     // 10 digits

        return Optional.of("urn:epc:id:sscc:" + company + "." + serialRef);
    }

    /**
     * GLN-13 (13 digits) → urn:epc:id:sgln:{company}.{location}.{extension}
     *
     * Algorithm:
     *   1. GLN must be exactly 13 digits
     *   2. Remove last digit (check digit) → 12 digits
     *   3. Split: company  = first companyPrefixLength digits
     *             location = remaining digits
     *   4. Extension from qualifier AI 254; default is "0" (no extension)
     */
    private Optional<String> toSglnUrn() {
        if (primaryValue == null || primaryValue.length() != 13) return Optional.empty();

        String digits12 = primaryValue.substring(0, 12);

        if (digits12.length() < companyPrefixLength) return Optional.empty();

        String company  = digits12.substring(0, companyPrefixLength);
        String location = digits12.substring(companyPrefixLength);
        String ext      = (qualifiers != null && qualifiers.containsKey("254"))
                          ? qualifiers.get("254")
                          : "0";

        return Optional.of("urn:epc:id:sgln:" + company + "." + location + "." + ext);
    }
}
