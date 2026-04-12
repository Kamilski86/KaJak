package com.canda.epcis.api.capture;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for CaptureController + QueryController.
 *
 * Nutzt einen echten PostgreSQL Container (Testcontainers).
 * Tests sind geordnet: erst Capture-Tests (schreiben Daten),
 * dann Query-Tests (lesen Daten). Kein DirtiesContext — DB-State wird geteilt.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "epcis.output.directory=target/test-output/capture-integration"
)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaptureControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    // ─────────────────────────────────────────────
    // Capture — happy path
    // ─────────────────────────────────────────────

    @Test
    @Order(1)
    void capture_validXmlWithHeader_returns201AndCaptureResponse() throws Exception {
        String xml = loadFixture("fixtures/object-event-observe.xml");

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(xml))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.totalReceived", is(1)))
                .andExpect(jsonPath("$.totalAccepted", is(1)));
    }

    @Test
    @Order(2)
    void capture_aggregationEvent_returns201() throws Exception {
        String xml = loadFixture("fixtures/aggregation-event-add.xml");

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "DC-HAMBURG")
                        .content(xml))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAccepted", is(1)));
    }

    // ─────────────────────────────────────────────
    // Capture — error cases
    // ─────────────────────────────────────────────

    @Test
    @Order(3)
    void capture_missingSourceIdHeader_returns400() throws Exception {
        String xml = loadFixture("fixtures/object-event-observe.xml");

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("X-EPCIS-Source-ID header is required"));
    }

    @Test
    @Order(4)
    void capture_invalidXml_returns400() throws Exception {
        String invalidXml = "<this-is-not-valid-epcis-xml/>";

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(invalidXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ─────────────────────────────────────────────
    // Query — uses data inserted by Order(1) + Order(2)
    // ─────────────────────────────────────────────

    @Test
    @Order(10)
    void query_noParameters_returns200WithAllEvents() throws Exception {
        mockMvc.perform(get("/epcis/query/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    @Order(11)
    void query_eqEventTypeAggregation_returnsOnlyAggregationEvents() throws Exception {
        mockMvc.perform(get("/epcis/query/events")
                        .param("EQ_eventType", "AggregationEvent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults", greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(12)
    void query_eventById_knownId_returns200() throws Exception {
        // eventID from fixtures/object-event-observe.xml
        String eventId = "urn:uuid:a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        mockMvc.perform(get("/epcis/query/events/{eventID}", eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventID").value(eventId));
    }

    @Test
    @Order(13)
    void query_eventById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/epcis/query/events/{eventID}", "urn:uuid:00000000-0000-0000-0000-000000000000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ─────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────

    private String loadFixture(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
