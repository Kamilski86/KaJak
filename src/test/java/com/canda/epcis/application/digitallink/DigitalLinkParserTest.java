package com.canda.epcis.application.digitallink;

import com.canda.epcis.domain.model.digitallink.DigitalLinkParseException;
import com.canda.epcis.domain.model.digitallink.DigitalLinkUri;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DigitalLinkParser}.
 *
 * C&A constants:
 *   Company Prefix = 7 digits
 *   SGTIN: 4056019.010532.{serial}  → GTIN-14 = 4056019010532 + check
 *   SSCC:  4056019.0000000001        → SSCC-18 = 0+4056019+000000001+check
 *   SGLN:  4056019.00000.0           → GLN-13  = 405601900000  + check
 *
 * Pre-computed check digits (GS1 Modulo-10):
 *   4056019010532  → check=2  → GTIN-14 = 40560190105322
 *   04056019000000001 → check=6 → SSCC-18 = 040560190000000016
 *   405601900000   → check=1  → GLN-13  = 4056019000001
 */
public class DigitalLinkParserTest {

    // Test constants — public so DigitalLinkResolverServiceTest and integration tests can import them
    public static final String COMPANY      = "4056019";
    public static final String ITEM         = "010532";
    public static final String SERIAL       = "12345";
    public static final String SERIAL_REF   = "0000000001";   // extension(0) + serial(000000001)
    public static final String LOCATION_REF = "00000";

    public static final String GTIN14  = "40560190105322";    // 4056019+010532+check(2)
    public static final String SSCC18  = "040560190000000018"; // 0+4056019+000000001+check(8)
    public static final String GLN13   = "4056019000001";      // 4056019+00000+check(1)

    public static final String SGTIN_URN = "urn:epc:id:sgtin:" + COMPANY + "." + ITEM + "." + SERIAL;
    public static final String SSCC_URN  = "urn:epc:id:sscc:" + COMPANY + "." + SERIAL_REF;
    public static final String SGLN_URN  = "urn:epc:id:sgln:" + COMPANY + "." + LOCATION_REF + ".0";

    public static final String BASE_URL = "https://id.canda.com";

    DigitalLinkParser parser;

    @BeforeEach
    void setUp() {
        parser = new DigitalLinkParser();
    }

    // ─────────────────────────────────────────────
    // parse — GTIN + Serial (AI 01 + AI 21)
    // ─────────────────────────────────────────────

    @Test
    void parse_gtinWithSerial_correctlyParsed() {
        String uri = BASE_URL + "/01/" + GTIN14 + "/21/" + SERIAL;

        DigitalLinkUri result = parser.parse(uri);

        assertThat(result.getPrimaryAi()).isEqualTo("01");
        assertThat(result.getPrimaryKey()).isEqualTo("gtin");
        assertThat(result.getPrimaryValue()).isEqualTo(GTIN14);
        assertThat(result.getQualifiers()).containsEntry("21", SERIAL);
    }

    @Test
    void parse_gtinShortName_resolvedToNumericAi() {
        String uri = BASE_URL + "/gtin/" + GTIN14 + "/ser/" + SERIAL;

        DigitalLinkUri result = parser.parse(uri);

        assertThat(result.getPrimaryAi()).isEqualTo("01");
        assertThat(result.getPrimaryKey()).isEqualTo("gtin");
        assertThat(result.getQualifiers()).containsEntry("21", SERIAL);
    }

    // ─────────────────────────────────────────────
    // parse — SSCC (AI 00)
    // ─────────────────────────────────────────────

    @Test
    void parse_sscc_correctlyParsed() {
        String uri = BASE_URL + "/00/" + SSCC18;

        DigitalLinkUri result = parser.parse(uri);

        assertThat(result.getPrimaryAi()).isEqualTo("00");
        assertThat(result.getPrimaryKey()).isEqualTo("sscc");
        assertThat(result.getPrimaryValue()).isEqualTo(SSCC18);
        assertThat(result.getQualifiers()).isEmpty();
    }

    // ─────────────────────────────────────────────
    // parse — GLN (AI 414)
    // ─────────────────────────────────────────────

    @Test
    void parse_gln_correctlyParsed() {
        String uri = BASE_URL + "/414/" + GLN13;

        DigitalLinkUri result = parser.parse(uri);

        assertThat(result.getPrimaryAi()).isEqualTo("414");
        assertThat(result.getPrimaryKey()).isEqualTo("gln");
        assertThat(result.getPrimaryValue()).isEqualTo(GLN13);
    }

    // ─────────────────────────────────────────────
    // parse — GTIN + Lot (AI 01 + AI 10)
    // ─────────────────────────────────────────────

    @Test
    void parse_gtinWithLot_correctlyParsed() {
        String uri = BASE_URL + "/01/" + GTIN14 + "/10/LOT-ABC";

        DigitalLinkUri result = parser.parse(uri);

        assertThat(result.getPrimaryAi()).isEqualTo("01");
        assertThat(result.getQualifiers()).containsEntry("10", "LOT-ABC");
    }

    // ─────────────────────────────────────────────
    // parse — error cases
    // ─────────────────────────────────────────────

    @Test
    void parse_invalidUri_throwsParseException() {
        assertThatThrownBy(() -> parser.parse("not a uri at all !!"))
                .isInstanceOf(DigitalLinkParseException.class);
    }

    @Test
    void parse_blankUri_throwsParseException() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(DigitalLinkParseException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void parse_nullUri_throwsParseException() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(DigitalLinkParseException.class);
    }

    // ─────────────────────────────────────────────
    // toDigitalLink — EPC URN → Digital Link
    // ─────────────────────────────────────────────

    @Test
    void toDigitalLink_sgtin_producesCorrectGtin14() {
        String result = parser.toDigitalLink(SGTIN_URN, BASE_URL);

        assertThat(result).isEqualTo(BASE_URL + "/01/" + GTIN14 + "/21/" + SERIAL);
    }

    @Test
    void toDigitalLink_sscc_producesCorrectSscc18() {
        String result = parser.toDigitalLink(SSCC_URN, BASE_URL);

        assertThat(result).isEqualTo(BASE_URL + "/00/" + SSCC18);
    }

    @Test
    void toDigitalLink_sgln_producesCorrectGln13() {
        String result = parser.toDigitalLink(SGLN_URN, BASE_URL);

        assertThat(result).isEqualTo(BASE_URL + "/414/" + GLN13);
    }

    @Test
    void toDigitalLink_sglnWithExtension_appendsGlnxSegment() {
        String sglnWithExt = "urn:epc:id:sgln:" + COMPANY + "." + LOCATION_REF + ".EXT01";
        String result = parser.toDigitalLink(sglnWithExt, BASE_URL);

        assertThat(result).isEqualTo(BASE_URL + "/414/" + GLN13 + "/254/EXT01");
    }

    @Test
    void toDigitalLink_unsupportedEpcType_throwsParseException() {
        assertThatThrownBy(() -> parser.toDigitalLink("urn:epc:id:grai:123.456.789", BASE_URL))
                .isInstanceOf(DigitalLinkParseException.class)
                .hasMessageContaining("Unsupported");
    }

    // ─────────────────────────────────────────────
    // Round-trip: EPC URN → Digital Link → EPC URN
    // ─────────────────────────────────────────────

    @Test
    void roundTrip_sgtin_digitalLinkToEpcUrn() {
        String dlUrl = parser.toDigitalLink(SGTIN_URN, BASE_URL);
        DigitalLinkUri parsed = parser.parse(dlUrl);

        Optional<String> recovered = parsed.toEpcUrn();

        assertThat(recovered).isPresent().contains(SGTIN_URN);
    }

    @Test
    void roundTrip_sscc_digitalLinkToEpcUrn() {
        String dlUrl = parser.toDigitalLink(SSCC_URN, BASE_URL);
        DigitalLinkUri parsed = parser.parse(dlUrl);

        Optional<String> recovered = parsed.toEpcUrn();

        assertThat(recovered).isPresent().contains(SSCC_URN);
    }

    // ─────────────────────────────────────────────
    // gs1CheckDigit
    // ─────────────────────────────────────────────

    @Test
    void gs1CheckDigit_sgtinPayload_correctCheckDigit() {
        // 4056019010532 → check = 2
        assertThat(parser.gs1CheckDigit("4056019010532")).isEqualTo(2);
    }

    @Test
    void gs1CheckDigit_ssccBody_correctCheckDigit() {
        // 04056019000000001 → check = 8
        assertThat(parser.gs1CheckDigit("04056019000000001")).isEqualTo(8);
    }

    @Test
    void gs1CheckDigit_glnPayload_correctCheckDigit() {
        // 405601900000 → check = 1
        assertThat(parser.gs1CheckDigit("405601900000")).isEqualTo(1);
    }

    // ─────────────────────────────────────────────
    // isDigitalLinkUri
    // ─────────────────────────────────────────────

    @Test
    void isDigitalLinkUri_validUri_returnsTrue() {
        assertThat(parser.isDigitalLinkUri(BASE_URL + "/01/" + GTIN14)).isTrue();
    }

    @Test
    void isDigitalLinkUri_epcUrn_returnsFalse() {
        assertThat(parser.isDigitalLinkUri(SGTIN_URN)).isFalse();
    }

    @Test
    void isDigitalLinkUri_null_returnsFalse() {
        assertThat(parser.isDigitalLinkUri(null)).isFalse();
    }
}
