package com.canda.epcis.api;

import com.canda.epcis.application.downstream.subscription.SubscriptionRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "epcis.output.directory=target/test-output/subscription-integration"
)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SubscriptionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // POST — Register
    // ─────────────────────────────────────────────

    @Test
    void post_validSubscription_returns201() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-SUB-001", "ITEMOPTIX",
                "http://localhost:9001/callback",
                "BEARER", "test-token",
                "ObjectEvent", "shipping", null, null, null,
                true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId", is("TEST-SUB-001")))
                .andExpect(jsonPath("$.targetSystem", is("ITEMOPTIX")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void post_duplicateSubscriptionId_returns400() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-DUP-001", "ITEMOPTIX", "http://localhost/cb",
                "NONE", null, null, null, null, null, null, true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second registration with same ID
        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    // GET — List all
    // ─────────────────────────────────────────────

    @Test
    void get_allSubscriptions_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/epcis/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void get_afterCreation_returnsCreatedSubscription() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-LIST-001", "MULE", "http://localhost/mule",
                "NONE", null, "ObjectEvent", "receiving", null, null, null, true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/epcis/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subscriptionId", is("TEST-LIST-001")));
    }

    // ─────────────────────────────────────────────
    // GET /{id}
    // ─────────────────────────────────────────────

    @Test
    void getById_existingSubscription_returns200() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-GET-001", "ITEMOPTIX", "http://localhost/io",
                "BEARER", "token-xyz", null, null, null, null, null, true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/epcis/subscriptions/TEST-GET-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId", is("TEST-GET-001")))
                .andExpect(jsonPath("$.targetSystem", is("ITEMOPTIX")));
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/epcis/subscriptions/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────
    // PUT /{id}/active — Toggle active
    // ─────────────────────────────────────────────

    @Test
    void putActive_deactivatesSubscription() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-ACTIVE-001", "ITEMOPTIX", "http://localhost/itemoptix",
                "NONE", null, null, null, null, null, null, true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/epcis/subscriptions/TEST-ACTIVE-001/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void putActive_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/epcis/subscriptions/UNKNOWN/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────
    // DELETE /{id}
    // ─────────────────────────────────────────────

    @Test
    void delete_existingSubscription_returns204() throws Exception {
        SubscriptionRegistration req = new SubscriptionRegistration(
                "TEST-DEL-001", "MULE", "http://localhost/del",
                "NONE", null, null, null, null, null, null, true);

        mockMvc.perform(post("/epcis/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/epcis/subscriptions/TEST-DEL-001"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/epcis/subscriptions/TEST-DEL-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/epcis/subscriptions/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }
}
