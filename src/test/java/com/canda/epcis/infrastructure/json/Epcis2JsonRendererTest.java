package com.canda.epcis.infrastructure.json;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.BusinessTransaction;
import com.canda.epcis.domain.model.ObjectEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Epcis2JsonRendererTest {

    private Epcis2JsonRenderer renderer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        renderer = new Epcis2JsonRenderer(objectMapper);
    }

    @Test
    void renderObjectEvent_containsExpectedFields() throws Exception {
        ObjectEvent event = ObjectEvent.builder()
                .eventId("urn:uuid:test-event-id")
                .eventTime(OffsetDateTime.parse("2024-01-15T10:30:00+02:00"))
                .eventTimeZoneOffset("+02:00")
                .action(Action.OBSERVE)
                .bizStep("urn:epcglobal:cbv:bizstep:shipping")
                .epcList(List.of("urn:epc:id:sgtin:0614141.107346.2017"))
                .build();

        String json = renderer.renderEvent(event);

        assertThat(json).contains("\"type\" : \"ObjectEvent\"");
        assertThat(json).contains("\"eventID\" : \"urn:uuid:test-event-id\"");
        assertThat(json).contains("\"action\" : \"OBSERVE\"");
        assertThat(json).contains("\"bizStep\" : \"shipping\"");
        assertThat(json).contains("urn:epc:id:sgtin:0614141.107346.2017");
        // @context must NOT appear on individual events
        assertThat(json).doesNotContain("@context");
    }

    @Test
    void renderObjectEvent_withoutEventId_eventIdIsAbsentInOutput() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.ADD)
                .build();

        String json = renderer.renderEvent(event);

        // A missing source eventID must not be silently fabricated and emitted as a business eventID.
        assertThat(json).doesNotContain("eventID");
    }

    @Test
    void renderAggregationEvent_containsParentAndChildFields() {
        AggregationEvent event = AggregationEvent.builder()
                .eventTime(OffsetDateTime.parse("2024-01-15T12:00:00+01:00"))
                .action(Action.ADD)
                .parentId("urn:epc:id:sscc:0614141.1234567890")
                .childEpcs(List.of(
                        "urn:epc:id:sgtin:0614141.107346.1",
                        "urn:epc:id:sgtin:0614141.107346.2"))
                .build();

        String json = renderer.renderEvent(event);

        assertThat(json).contains("\"type\" : \"AggregationEvent\"");
        assertThat(json).contains("\"parentID\" : \"urn:epc:id:sscc:0614141.1234567890\"");
        assertThat(json).contains("urn:epc:id:sgtin:0614141.107346.1");
    }

    @Test
    void renderDocument_wrapsEventsWithContextAndEnvelope() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.OBSERVE)
                .build();

        EpcisDocumentDto doc = renderer.renderDocument(List.of(event));

        assertThat(doc.getContext()).isEqualTo("https://ref.gs1.org/standards/epcis/epcis-context.jsonld");
        assertThat(doc.getType()).isEqualTo("EPCISDocument");
        assertThat(doc.getEpcisBody()).isNotNull();
        assertThat(doc.getEpcisBody().getEventList()).hasSize(1);
        assertThat(doc.getEpcisBody().getEventList().get(0).getType()).isEqualTo("ObjectEvent");
    }

    @Test
    void renderDocument_multipleEvents_allPresent() {
        List<ObjectEvent> events = List.of(
                ObjectEvent.builder().eventTime(OffsetDateTime.now()).action(Action.ADD).build(),
                ObjectEvent.builder().eventTime(OffsetDateTime.now()).action(Action.DELETE).build()
        );

        EpcisDocumentDto doc = renderer.renderDocument(List.copyOf(events));

        assertThat(doc.getEpcisBody().getEventList()).hasSize(2);
    }

    @Test
    void renderObjectEvent_withBizTransactions_serialisesCorrectly() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.OBSERVE)
                .bizTransactionList(List.of(
                        BusinessTransaction.builder()
                                .type("urn:epcglobal:cbv:btt:po")
                                .value("urn:epcglobal:cbv:bt:0614141073467:1152")
                                .build()))
                .build();

        String json = renderer.renderEvent(event);

        assertThat(json).contains("\"type\" : \"po\"");
        assertThat(json).contains("bizTransactionList");
    }
}
