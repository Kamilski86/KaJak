package com.canda.epcis.api.inventory;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for InventoryController.
 *
 * Order: capture events first (Order 1-2), then query inventory (Order 10+).
 * Relies on a real PostgreSQL container via Testcontainers.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "epcis.output.directory=target/test-output/inventory-integration"
)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    // Known identifiers from fixtures
    private static final String OBJECT_EVENT_EPC  = "urn:epc:id:sgtin:0614141.107346.2018";
    private static final String AGGREGATION_SSCC  = "urn:epc:id:sscc:0614141.1234567890";
    private static final String AGGREGATION_CHILD = "urn:epc:id:sgtin:0614141.107346.2018";

    // ─────────────────────────────────────────────
    // Setup: capture events to populate inventory
    // ─────────────────────────────────────────────

    @Test
    @Order(1)
    void setup_captureObjectEvent() throws Exception {
        String xml = loadFixture("fixtures/object-event-observe.xml");
        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(xml))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void setup_captureAggregationEvent() throws Exception {
        String xml = loadFixture("fixtures/aggregation-event-add.xml");
        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "DC-HAMBURG")
                        .content(xml))
                .andExpect(status().isCreated());
    }

    // ─────────────────────────────────────────────
    // GET /inventory/epc
    // ─────────────────────────────────────────────

    @Test
    @Order(10)
    void getEpc_knownEpc_returns200WithEpcState() throws Exception {
        mockMvc.perform(get("/inventory/epc")
                        .param("epc", OBJECT_EVENT_EPC)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epcUrn", is(OBJECT_EVENT_EPC)))
                .andExpect(jsonPath("$.currentStatus", notNullValue()));
    }

    @Test
    @Order(11)
    void getEpc_unknownEpc_returns404() throws Exception {
        mockMvc.perform(get("/inventory/epc")
                        .param("epc", "urn:epc:id:sgtin:0000000.000000.9999999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", notNullValue()));
    }

    // ─────────────────────────────────────────────
    // GET /inventory/pallet
    // ─────────────────────────────────────────────

    @Test
    @Order(20)
    void getPallet_knownSscc_returns200WithChildEpcs() throws Exception {
        mockMvc.perform(get("/inventory/pallet")
                        .param("sscc", AGGREGATION_SSCC)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssccUrn", is(AGGREGATION_SSCC)))
                .andExpect(jsonPath("$.childCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.childEpcs").isArray());
    }

    @Test
    @Order(21)
    void getPallet_unknownSscc_returns404() throws Exception {
        mockMvc.perform(get("/inventory/pallet")
                        .param("sscc", "urn:epc:id:sscc:0000000.0000000000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", notNullValue()));
    }

    // ─────────────────────────────────────────────
    // GET /inventory/history
    // ─────────────────────────────────────────────

    @Test
    @Order(30)
    void getHistory_knownEpc_returns200WithMovements() throws Exception {
        mockMvc.perform(get("/inventory/history")
                        .param("epc", OBJECT_EVENT_EPC)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epcUrn", is(OBJECT_EVENT_EPC)))
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.movements").isArray());
    }

    // ─────────────────────────────────────────────
    // GET /inventory/available-quantity
    // ─────────────────────────────────────────────

    @Test
    @Order(40)
    void getAvailableQuantity_noFilter_returns200WithLocationsArray() throws Exception {
        mockMvc.perform(get("/inventory/available-quantity")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations").isArray())
                .andExpect(jsonPath("$.messageId", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    // ─────────────────────────────────────────────
    // POST /inventory/rebuild
    // ─────────────────────────────────────────────

    @Test
    @Order(50)
    void rebuild_returns200WithStats() throws Exception {
        mockMvc.perform(post("/inventory/rebuild")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsProcessed", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.completedAt", notNullValue()));
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
