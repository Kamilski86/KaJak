package com.canda.epcis.api.digitallink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.canda.epcis.application.digitallink.DigitalLinkParserTest.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link DigitalLinkController}.
 *
 * Uses a real PostgreSQL container. The CBV vocabulary is loaded by
 * CbvVocabularyLoader on startup. No EPCIS events are pre-loaded,
 * so history/location endpoints return empty results.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "epcis.output.directory=target/test-output/digitallink-integration"
)
@Testcontainers(disabledWithoutDocker = true)
class DigitalLinkControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    static final String SGTIN_DL_URI = BASE_URL + "/01/" + GTIN14 + "/21/" + SERIAL;
    static final String SSCC_DL_URI  = BASE_URL + "/00/" + SSCC18;

    // ─────────────────────────────────────────────
    // GET /digitallink/parse
    // ─────────────────────────────────────────────

    @Test
    void parse_gtinWithSerial_returns200WithParsedFields() throws Exception {
        mockMvc.perform(get("/digitallink/parse")
                        .param("uri", SGTIN_DL_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryAi", is("01")))
                .andExpect(jsonPath("$.primaryKey", is("gtin")))
                .andExpect(jsonPath("$.primaryValue", is(GTIN14)))
                .andExpect(jsonPath("$.qualifiers.21", is(SERIAL)))
                .andExpect(jsonPath("$.epcUrn", is(SGTIN_URN)));
    }

    @Test
    void parse_sscc_returns200WithParsedFields() throws Exception {
        mockMvc.perform(get("/digitallink/parse")
                        .param("uri", SSCC_DL_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryAi", is("00")))
                .andExpect(jsonPath("$.primaryKey", is("sscc")))
                .andExpect(jsonPath("$.primaryValue", is(SSCC18)))
                .andExpect(jsonPath("$.epcUrn", is(SSCC_URN)));
    }

    @Test
    void parse_invalidUri_returns400() throws Exception {
        mockMvc.perform(get("/digitallink/parse")
                        .param("uri", "this-is-not-a-digital-link"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", notNullValue()));
    }

    // ─────────────────────────────────────────────
    // GET /digitallink/convert
    // ─────────────────────────────────────────────

    @Test
    void convert_sgtinUrn_returns200WithDigitalLinkUrl() throws Exception {
        mockMvc.perform(get("/digitallink/convert")
                        .param("epc", SGTIN_URN)
                        .param("baseUrl", BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epcUrn", is(SGTIN_URN)))
                .andExpect(jsonPath("$.digitalLinkUrl", containsString("/01/" + GTIN14 + "/21/" + SERIAL)));
    }

    @Test
    void convert_ssccUrn_returns200WithDigitalLinkUrl() throws Exception {
        mockMvc.perform(get("/digitallink/convert")
                        .param("epc", SSCC_URN)
                        .param("baseUrl", BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digitalLinkUrl", containsString("/00/" + SSCC18)));
    }

    @Test
    void convert_unsupportedEpcType_returns400() throws Exception {
        mockMvc.perform(get("/digitallink/convert")
                        .param("epc", "urn:epc:id:grai:123.456.789")
                        .param("baseUrl", BASE_URL))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    // GET /digitallink/resolve
    // ─────────────────────────────────────────────

    @Test
    void resolve_sgtin_returns200WithResolverResponse() throws Exception {
        mockMvc.perform(get("/digitallink/resolve")
                        .param("uri", SGTIN_DL_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier", is(SGTIN_URN)))
                .andExpect(jsonPath("$.identifierType", is("SGTIN")))
                .andExpect(jsonPath("$.linkSets", notNullValue()));
    }

    @Test
    void resolve_invalidUri_returns400() throws Exception {
        mockMvc.perform(get("/digitallink/resolve")
                        .param("uri", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    // GET /digitallink/history
    // ─────────────────────────────────────────────

    @Test
    void history_sgtin_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/digitallink/history")
                        .param("uri", SGTIN_DL_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─────────────────────────────────────────────
    // GET /digitallink/location
    // ─────────────────────────────────────────────

    @Test
    void location_unknownEpc_returns404() throws Exception {
        mockMvc.perform(get("/digitallink/location")
                        .param("uri", SGTIN_DL_URI))
                .andExpect(status().isNotFound());
    }
}
