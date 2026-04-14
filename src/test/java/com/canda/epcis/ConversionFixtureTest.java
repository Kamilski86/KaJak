package com.canda.epcis;

import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.service.CbvVocabularyValidator;
import com.canda.epcis.infrastructure.json.Epcis2JsonRenderer;
import com.canda.epcis.infrastructure.xml.EpcisXmlParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-master fixture tests.
 *
 * Each test loads a representative EPCIS 1.2 XML fixture, runs the full
 * parse → CBV-validate → render pipeline (no Spring context, no DB), and
 * asserts that the rendered EPCIS 2.0 JSON contains the expected fields and
 * values. Tests are kept deterministic by using fixed eventIDs and timestamps
 * in the fixture files.
 */
class ConversionFixtureTest {

    private EpcisXmlParser parser;
    private CbvVocabularyValidator cbvValidator;
    private Epcis2JsonRenderer renderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        parser = new EpcisXmlParser();
        cbvValidator = new CbvVocabularyValidator();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        renderer = new Epcis2JsonRenderer(objectMapper);
    }

    // ─────────────────────────────────────────────
    // Scenario 1: ObjectEvent OBSERVE — full field set
    // ─────────────────────────────────────────────

    @Test
    void objectEvent_observe_fullFields_convertsCorrectly() throws Exception {
        String xml = loadFixture("fixtures/object-event-observe.xml");
        List<EpcisEvent> events = parser.parse(xml);
        events.forEach(cbvValidator::validate);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ObjectEvent.class);

        String json = renderer.renderEvent(events.get(0));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("ObjectEvent");
        assertThat(node.path("action").asText()).isEqualTo("OBSERVE");
        assertThat(node.path("eventID").asText()).isEqualTo("urn:uuid:a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(node.path("bizStep").asText()).isEqualTo("shipping");
        assertThat(node.path("disposition").asText()).isEqualTo("in_transit");
        assertThat(node.path("readPoint").path("id").asText())
                .isEqualTo("urn:epc:id:sgln:0614141.07346.1234");
        assertThat(node.path("bizLocation").path("id").asText())
                .isEqualTo("urn:epc:id:sgln:0614141.07346.0000");

        // epcList must contain both items
        JsonNode epcList = node.path("epcList");
        assertThat(epcList.isArray()).isTrue();
        assertThat(epcList).hasSize(2);
        assertThat(epcList.get(0).asText()).isEqualTo("urn:epc:id:sgtin:0614141.107346.2017");
        assertThat(epcList.get(1).asText()).isEqualTo("urn:epc:id:sgtin:0614141.107346.2018");

        // bizTransactionList
        JsonNode btList = node.path("bizTransactionList");
        assertThat(btList.isArray()).isTrue();
        assertThat(btList).hasSize(2);

        // sourceList and destinationList
        assertThat(node.path("sourceList").isArray()).isTrue();
        assertThat(node.path("sourceList").get(0).path("type").asText())
                .isEqualTo("owning_party");
        assertThat(node.path("destinationList").isArray()).isTrue();

        // No @context on individual events
        assertThat(node.has("@context")).isFalse();
    }

    // ─────────────────────────────────────────────
    // Scenario 2: ObjectEvent ADD — quantityList + ILMD
    // ─────────────────────────────────────────────

    @Test
    void objectEvent_add_withQuantityListAndIlmd_convertsCorrectly() throws Exception {
        String xml = loadFixture("fixtures/object-event-add-with-ilmd.xml");
        List<EpcisEvent> events = parser.parse(xml);
        events.forEach(cbvValidator::validate);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ObjectEvent.class);

        String json = renderer.renderEvent(events.get(0));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("ObjectEvent");
        assertThat(node.path("action").asText()).isEqualTo("ADD");
        assertThat(node.path("bizStep").asText()).isEqualTo("commissioning");

        // quantityList
        JsonNode qList = node.path("quantityList");
        assertThat(qList.isArray()).isTrue();
        assertThat(qList).hasSize(1);
        assertThat(qList.get(0).path("epcClass").asText())
                .isEqualTo("urn:epc:class:lgtin:0614141.107346.LOT-2024-001");
        assertThat(qList.get(0).path("quantity").asDouble()).isEqualTo(150.0);
        assertThat(qList.get(0).path("uom").asText()).isEqualTo("EA");

        // ILMD fields preserved
        JsonNode ilmd = node.path("ilmd");
        assertThat(ilmd.isMissingNode()).isFalse();
        assertThat(ilmd.path("lotNumber").asText()).isEqualTo("LOT-2024-001");
        assertThat(ilmd.path("itemExpirationDate").asText()).isEqualTo("2025-12-31");
    }

    // ─────────────────────────────────────────────
    // Scenario 3: ObjectEvent DELETE — errorDeclaration
    // ─────────────────────────────────────────────

    @Test
    void objectEvent_delete_withErrorDeclaration_convertsCorrectly() throws Exception {
        String xml = loadFixture("fixtures/object-event-delete-with-errordeclaration.xml");
        List<EpcisEvent> events = parser.parse(xml);
        events.forEach(cbvValidator::validate);

        assertThat(events).hasSize(1);
        String json = renderer.renderEvent(events.get(0));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("action").asText()).isEqualTo("DELETE");

        JsonNode ed = node.path("errorDeclaration");
        assertThat(ed.isMissingNode()).isFalse();
        assertThat(ed.path("reason").asText()).isEqualTo("did_not_occur");

        JsonNode correctingIds = ed.path("correctingEventIDs");
        assertThat(correctingIds.isArray()).isTrue();
        assertThat(correctingIds.get(0).asText())
                .isEqualTo("urn:uuid:deadbeef-0000-0000-0000-000000000001");
    }

    // ─────────────────────────────────────────────
    // Scenario 4: AggregationEvent ADD
    // ─────────────────────────────────────────────

    @Test
    void aggregationEvent_add_convertsCorrectly() throws Exception {
        String xml = loadFixture("fixtures/aggregation-event-add.xml");
        List<EpcisEvent> events = parser.parse(xml);
        events.forEach(cbvValidator::validate);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AggregationEvent.class);

        String json = renderer.renderEvent(events.get(0));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("AggregationEvent");
        assertThat(node.path("action").asText()).isEqualTo("ADD");
        assertThat(node.path("parentID").asText()).isEqualTo("urn:epc:id:sscc:0614141.1234567890");
        assertThat(node.path("bizStep").asText()).isEqualTo("packing");

        JsonNode childEpcs = node.path("childEPCs");
        assertThat(childEpcs.isArray()).isTrue();
        assertThat(childEpcs).hasSize(3);
    }

    // ─────────────────────────────────────────────
    // Scenario 5: AggregationEvent DELETE — empty childEPCs
    // ─────────────────────────────────────────────

    @Test
    void aggregationEvent_delete_emptyChildren_convertsCorrectly() throws Exception {
        String xml = loadFixture("fixtures/aggregation-event-delete.xml");
        List<EpcisEvent> events = parser.parse(xml);
        events.forEach(cbvValidator::validate);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AggregationEvent.class);

        String json = renderer.renderEvent(events.get(0));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("type").asText()).isEqualTo("AggregationEvent");
        assertThat(node.path("action").asText()).isEqualTo("DELETE");
        assertThat(node.path("parentID").asText()).isEqualTo("urn:epc:id:sscc:0614141.1234567890");
        assertThat(node.path("bizStep").asText()).isEqualTo("unpacking");

        // Empty childEPCs on DELETE is semantically valid (full disaggregation)
        JsonNode childEpcs = node.path("childEPCs");
        assertThat(childEpcs.isMissingNode() || (childEpcs.isArray() && childEpcs.isEmpty())).isTrue();
    }

    // ─────────────────────────────────────────────
    // PRIVATE: helper
    // ─────────────────────────────────────────────

    private String loadFixture(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("Fixture not found: " + path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
