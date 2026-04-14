package com.canda.epcis.application.capture;

import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.CaptureResult;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.json.Epcis2JsonRenderer;
import com.canda.epcis.infrastructure.persistence.JsonDatabaseWriter;
import com.canda.epcis.infrastructure.persistence.JsonFileWriter;
import com.canda.epcis.infrastructure.persistence.audit.CaptureAuditEntity;
import com.canda.epcis.infrastructure.persistence.audit.CaptureAuditRepository;
import com.canda.epcis.infrastructure.xml.EpcisValidationException;
import com.canda.epcis.infrastructure.xml.EpcisXmlParser;
import com.canda.epcis.infrastructure.xml.EpcisXmlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptureEventUseCaseTest {

    @Mock EpcisXmlValidator    xmlValidator;
    @Mock EpcisXmlParser       xmlParser;
    @Mock Epcis2JsonRenderer   jsonRenderer;
    @Mock JsonDatabaseWriter   databaseWriter;
    @Mock JsonFileWriter       fileWriter;
    @Mock CaptureAuditRepository auditRepository;

    // Real EpcFilterService — pure domain logic, no mocking needed
    private EpcFilterService epcFilterService;
    private CaptureEventUseCase useCase;

    private static final String SOURCE_ID = "STORE-DE-001";
    private static final String DUMMY_XML = "<xml/>";

    @BeforeEach
    void setUp() {
        epcFilterService = new EpcFilterService();
        useCase = new CaptureEventUseCase(
                xmlValidator, xmlParser, epcFilterService,
                jsonRenderer, databaseWriter, fileWriter, auditRepository);

        lenient().when(auditRepository.save(any(CaptureAuditEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jsonRenderer.renderEvent(any())).thenReturn("{\"type\":\"ObjectEvent\"}");
    }

    // ─────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────

    @Test
    void capture_validXml_acceptsAllEvents() {
        ObjectEvent event = validObjectEvent("urn:uuid:aaa");
        when(xmlParser.parse(DUMMY_XML)).thenReturn(List.of(event));

        CaptureResult result = useCase.capture(DUMMY_XML, SOURCE_ID);

        assertThat(result.getTotalReceived()).isEqualTo(1);
        assertThat(result.getTotalAccepted()).isEqualTo(1);
        assertThat(result.getTotalDropped()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
        verify(databaseWriter).save(any(), anyString());
        verify(fileWriter).write(anyString());
        verify(auditRepository).save(any());
    }

    @Test
    void capture_xmlWithForbiddenEpc_filteredAndEventKept() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId("urn:uuid:bbb")
                .epcList(List.of(
                        "urn:epc:id:sgtin:0614141.107346.0001",
                        "urn:epc:id:gid:0614141.000000.9999"
                ))
                .build();
        when(xmlParser.parse(DUMMY_XML)).thenReturn(List.of(event));

        CaptureResult result = useCase.capture(DUMMY_XML, SOURCE_ID);

        assertThat(result.getTotalAccepted()).isEqualTo(1);
        assertThat(result.getTotalFiltered()).isEqualTo(1);
        assertThat(result.getTotalDropped()).isEqualTo(0);
    }

    @Test
    void capture_mixedEvents_objectAndAggregation_bothProcessed() {
        ObjectEvent objectEvent = validObjectEvent("urn:uuid:ccc");
        AggregationEvent aggregationEvent = validAggregationEvent("urn:uuid:ddd");
        when(xmlParser.parse(DUMMY_XML)).thenReturn(List.of(objectEvent, aggregationEvent));

        CaptureResult result = useCase.capture(DUMMY_XML, SOURCE_ID);

        assertThat(result.getTotalReceived()).isEqualTo(2);
        assertThat(result.getTotalAccepted()).isEqualTo(2);
        assertThat(result.getTotalDropped()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────
    // Validation failures
    // ─────────────────────────────────────────────

    @Test
    void capture_invalidXml_throwsEpcisValidationException() {
        doThrow(new EpcisValidationException("XSD error")).when(xmlValidator).validate(any());

        assertThatThrownBy(() -> useCase.capture(DUMMY_XML, SOURCE_ID))
                .isInstanceOf(EpcisValidationException.class)
                .hasMessageContaining("XSD error");
    }

    @Test
    void capture_nullSourceId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.capture(DUMMY_XML, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capture_blankSourceId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.capture(DUMMY_XML, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────

    private ObjectEvent validObjectEvent(String eventId) {
        return ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId(eventId)
                .epcList(List.of("urn:epc:id:sgtin:0614141.107346.0001"))
                .build();
    }

    private AggregationEvent validAggregationEvent(String eventId) {
        return AggregationEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId(eventId)
                .parentId("urn:epc:id:sscc:0614141.1234567890")
                .childEpcs(List.of("urn:epc:id:sgtin:0614141.107346.0001"))
                .build();
    }
}
