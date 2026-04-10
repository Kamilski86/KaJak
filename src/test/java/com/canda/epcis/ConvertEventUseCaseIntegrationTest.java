package com.canda.epcis;

import com.canda.epcis.application.ConvertEventUseCase;
import com.canda.epcis.domain.service.CbvValidationException;
import com.canda.epcis.infrastructure.json.EpcisDocumentDto;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import com.canda.epcis.infrastructure.persistence.QuarantineRepository;
import com.canda.epcis.infrastructure.xml.EpcisParsingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full-stack integration tests for {@link ConvertEventUseCase}.
 *
 * Spins up a real PostgreSQL container via Testcontainers. Flyway migrations
 * (V1 + V2) run automatically on startup, giving us a production-equivalent schema.
 * Each test that modifies DB state uses {@link DirtiesContext} or relies on
 * repository counts relative to an expected baseline.
 *
 * These tests verify the complete pipeline:
 *   parse → CBV validate → render → persist (DB) → audit (file) → JSON schema validate
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "epcis.output.directory=target/test-output/integration"
)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConvertEventUseCaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ConvertEventUseCase convertEventUseCase;

    @Autowired
    EpcisEventRepository eventRepository;

    @Autowired
    QuarantineRepository quarantineRepository;

    // ─────────────────────────────────────────────
    // Happy path: ObjectEvent OBSERVE → stored in DB
    // ─────────────────────────────────────────────

    @Test
    void convert_objectEventObserve_persistsToDatabase() throws Exception {
        String xml = loadFixture("fixtures/object-event-observe.xml");

        EpcisDocumentDto result = convertEventUseCase.convert(xml);

        assertThat(result).isNotNull();
        assertThat(result.getEpcisBody().getEventList()).hasSize(1);
        assertThat(result.getEpcisBody().getEventList().get(0).getType()).isEqualTo("ObjectEvent");

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(quarantineRepository.count()).isZero();
    }

    // ─────────────────────────────────────────────
    // Happy path: AggregationEvent ADD → stored in DB
    // ─────────────────────────────────────────────

    @Test
    void convert_aggregationEventAdd_persistsToDatabase() throws Exception {
        String xml = loadFixture("fixtures/aggregation-event-add.xml");

        EpcisDocumentDto result = convertEventUseCase.convert(xml);

        assertThat(result.getEpcisBody().getEventList()).hasSize(1);
        assertThat(result.getEpcisBody().getEventList().get(0).getType()).isEqualTo("AggregationEvent");

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(quarantineRepository.count()).isZero();
    }

    // ─────────────────────────────────────────────
    // Happy path: ObjectEvent ADD with ILMD → stored with payload
    // ─────────────────────────────────────────────

    @Test
    void convert_objectEventWithIlmd_payloadStoredCorrectly() throws Exception {
        String xml = loadFixture("fixtures/object-event-add-with-ilmd.xml");

        convertEventUseCase.convert(xml);

        assertThat(eventRepository.count()).isEqualTo(1);
        String payload = eventRepository.findAll().get(0).getPayload();
        assertThat(payload).contains("LOT-2024-001");
        assertThat(payload).contains("commissioning");
    }

    // ─────────────────────────────────────────────
    // Quarantine: unsupported event type → quarantined, not stored
    // ─────────────────────────────────────────────

    @Test
    void convert_unsupportedEventType_routesToQuarantine() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1"
                                     schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
                  <EPCISBody>
                    <EventList>
                      <TransactionEvent>
                        <eventTime>2024-01-15T10:00:00+00:00</eventTime>
                        <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                        <action>ADD</action>
                      </TransactionEvent>
                    </EventList>
                  </EPCISBody>
                </epcis:EPCISDocument>
                """;

        assertThatThrownBy(() -> convertEventUseCase.convert(xml))
                .isInstanceOf(EpcisParsingException.class)
                .hasMessageContaining("TransactionEvent");

        assertThat(eventRepository.count()).isZero();
        assertThat(quarantineRepository.count()).isEqualTo(1);
        assertThat(quarantineRepository.findAll().get(0).getErrorCode())
                .isEqualTo("UNSUPPORTED_EVENT_TYPE");
    }

    // ─────────────────────────────────────────────
    // Quarantine: CBV violation → quarantined, not stored
    // ─────────────────────────────────────────────

    @Test
    void convert_invalidBizStep_routesToQuarantine() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1"
                                     schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
                  <EPCISBody>
                    <EventList>
                      <ObjectEvent>
                        <eventTime>2024-01-15T10:00:00+00:00</eventTime>
                        <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                        <epcList><epc>urn:epc:id:sgtin:0614141.107346.1</epc></epcList>
                        <action>OBSERVE</action>
                        <bizStep>urn:epcglobal:cbv:bizstep:INVALID_STEP</bizStep>
                      </ObjectEvent>
                    </EventList>
                  </EPCISBody>
                </epcis:EPCISDocument>
                """;

        assertThatThrownBy(() -> convertEventUseCase.convert(xml))
                .isInstanceOf(CbvValidationException.class);

        assertThat(eventRepository.count()).isZero();
        assertThat(quarantineRepository.count()).isEqualTo(1);
        assertThat(quarantineRepository.findAll().get(0).getErrorCode())
                .isEqualTo("CBV_VIOLATION");
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
