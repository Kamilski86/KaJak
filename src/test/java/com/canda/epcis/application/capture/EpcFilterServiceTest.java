package com.canda.epcis.application.capture;

import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.EpcFormat;
import com.canda.epcis.domain.model.FilterResult;
import com.canda.epcis.domain.model.ObjectEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpcFilterServiceTest {

    private EpcFilterService service;

    @BeforeEach
    void setUp() {
        service = new EpcFilterService();
    }

    // ─────────────────────────────────────────────
    // EpcFormat static methods
    // ─────────────────────────────────────────────

    @Test
    void epcFormat_isForbidden_gid_returnsTrue() {
        assertThat(EpcFormat.isForbidden("urn:epc:id:gid:0614141.107346.1")).isTrue();
    }

    @Test
    void epcFormat_isForbidden_usdod_returnsTrue() {
        assertThat(EpcFormat.isForbidden("urn:epc:id:usdod:28000000000000001")).isTrue();
    }

    @Test
    void epcFormat_isForbidden_adi_returnsTrue() {
        assertThat(EpcFormat.isForbidden("urn:epc:id:adi:D-U-N-S.123456789")).isTrue();
    }

    @Test
    void epcFormat_isForbidden_bic_returnsTrue() {
        assertThat(EpcFormat.isForbidden("urn:epc:id:bic:CSQUK422")).isTrue();
    }

    @Test
    void epcFormat_isAllowedInEpcList_sgtin_returnsTrue() {
        assertThat(EpcFormat.isAllowedInEpcList("urn:epc:id:sgtin:0614141.107346.2017")).isTrue();
    }

    @Test
    void epcFormat_isAllowedInEpcList_sscc_returnsTrue() {
        assertThat(EpcFormat.isAllowedInEpcList("urn:epc:id:sscc:0614141.1234567890")).isTrue();
    }

    @Test
    void epcFormat_isAllowedInEpcList_sgln_returnsFalse() {
        assertThat(EpcFormat.isAllowedInEpcList("urn:epc:id:sgln:0614141.07346.0")).isFalse();
    }

    @Test
    void epcFormat_isValidParentId_sscc_returnsTrue() {
        assertThat(EpcFormat.isValidParentId("urn:epc:id:sscc:0614141.1234567890")).isTrue();
    }

    @Test
    void epcFormat_isValidParentId_sgtin_returnsFalse() {
        assertThat(EpcFormat.isValidParentId("urn:epc:id:sgtin:0614141.107346.2017")).isFalse();
    }

    // ─────────────────────────────────────────────
    // EpcFormat.isValidSsccStructure — GS1 TDS 2.0 §6.3.2
    // ─────────────────────────────────────────────

    @Test
    void ssccStructure_validNumeric17digits_returnsTrue() {
        // company(7) + serialRef(10) = 17
        assertThat(EpcFormat.isValidSsccStructure("urn:epc:id:sscc:4056019.0000000001")).isTrue();
    }

    @Test
    void ssccStructure_serialRefWithLetters_returnsFalse() {
        assertThat(EpcFormat.isValidSsccStructure("urn:epc:id:sscc:4056019.ABC1234567")).isFalse();
    }

    @Test
    void ssccStructure_wrongTotalLength_returnsFalse() {
        // company(7) + serialRef(5) = 12 — zu kurz
        assertThat(EpcFormat.isValidSsccStructure("urn:epc:id:sscc:4056019.00001")).isFalse();
    }

    @Test
    void ssccStructure_missingDot_returnsFalse() {
        assertThat(EpcFormat.isValidSsccStructure("urn:epc:id:sscc:40560190000000001")).isFalse();
    }

    @Test
    void ssccStructure_notSsccPrefix_returnsFalse() {
        assertThat(EpcFormat.isValidSsccStructure("urn:epc:id:sgtin:4056019.010532.1")).isFalse();
    }

    // ─────────────────────────────────────────────
    // filterObjectEvent
    // ─────────────────────────────────────────────

    @Test
    void filterObjectEvent_allValidSgtins_allAccepted() {
        ObjectEvent event = objectEvent(List.of(
                "urn:epc:id:sgtin:0614141.107346.0001",
                "urn:epc:id:sgtin:0614141.107346.0002"
        ));

        FilterResult result = service.filterObjectEvent(event);

        assertThat(result.getAcceptedEpcs()).hasSize(2);
        assertThat(result.getFilteredEpcs()).isEmpty();
        assertThat(result.isEventShouldBeDropped()).isFalse();
    }

    @Test
    void filterObjectEvent_gidEpc_filteredEventKept() {
        ObjectEvent event = objectEvent(List.of(
                "urn:epc:id:sgtin:0614141.107346.0001",
                "urn:epc:id:gid:0614141.107346.9999"
        ));

        FilterResult result = service.filterObjectEvent(event);

        assertThat(result.getAcceptedEpcs()).containsExactly("urn:epc:id:sgtin:0614141.107346.0001");
        assertThat(result.getFilteredEpcs()).hasSize(1);
        assertThat(result.getFilteredEpcs().get(0).getReason()).isEqualTo("FORBIDDEN_GID");
        assertThat(result.isEventShouldBeDropped()).isFalse();
    }

    @Test
    void filterObjectEvent_allForbidden_eventDropped() {
        ObjectEvent event = objectEvent(List.of(
                "urn:epc:id:gid:0614141.107346.0001",
                "urn:epc:id:usdod:28000000000000001"
        ));

        FilterResult result = service.filterObjectEvent(event);

        assertThat(result.getAcceptedEpcs()).isEmpty();
        assertThat(result.isEventShouldBeDropped()).isTrue();
        assertThat(result.getDropReason()).isNotBlank();
    }

    @Test
    void filterObjectEvent_emptyEpcList_noError() {
        ObjectEvent event = objectEvent(List.of());

        FilterResult result = service.filterObjectEvent(event);

        assertThat(result.getAcceptedEpcs()).isEmpty();
        assertThat(result.getFilteredEpcs()).isEmpty();
        assertThat(result.isEventShouldBeDropped()).isFalse();
    }

    // ─────────────────────────────────────────────
    // filterAggregationEvent
    // ─────────────────────────────────────────────

    @Test
    void filterAggregationEvent_parentIdNotSscc_eventDropped() {
        AggregationEvent event = aggregationEvent(
                "urn:epc:id:sgtin:0614141.107346.9999",
                List.of("urn:epc:id:sgtin:0614141.107346.0001")
        );

        FilterResult result = service.filterAggregationEvent(event);

        assertThat(result.isEventShouldBeDropped()).isTrue();
        assertThat(result.getDropReason()).contains("SSCC");
        assertThat(result.getFilteredEpcs()).extracting(FilterResult.FilteredEpc::getReason)
                .containsExactly("INVALID_PARENT_ID_NOT_SSCC");
    }

    @Test
    void filterAggregationEvent_validChildEpcs_allAccepted() {
        AggregationEvent event = aggregationEvent(
                "urn:epc:id:sscc:0614141.1234567890",
                List.of(
                        "urn:epc:id:sgtin:0614141.107346.0001",
                        "urn:epc:id:sgtin:0614141.107346.0002"
                )
        );

        FilterResult result = service.filterAggregationEvent(event);

        assertThat(result.getAcceptedEpcs()).hasSize(2);
        assertThat(result.getFilteredEpcs()).isEmpty();
        assertThat(result.isEventShouldBeDropped()).isFalse();
    }

    @Test
    void filterObjectEvent_ssccWithLettersInSerial_filtered() {
        ObjectEvent event = objectEvent(List.of(
                "urn:epc:id:sscc:4056019.ABC1234567"
        ));

        FilterResult result = service.filterObjectEvent(event);

        assertThat(result.getAcceptedEpcs()).isEmpty();
        assertThat(result.getFilteredEpcs()).hasSize(1);
        assertThat(result.getFilteredEpcs().get(0).getReason()).isEqualTo("INVALID_SSCC_STRUCTURE");
        assertThat(result.isEventShouldBeDropped()).isTrue();
    }

    @Test
    void filterAggregationEvent_parentIdSsccWithLetters_eventDropped() {
        AggregationEvent event = aggregationEvent(
                "urn:epc:id:sscc:4056019.ABC1234567",
                List.of("urn:epc:id:sgtin:0614141.107346.0001")
        );

        FilterResult result = service.filterAggregationEvent(event);

        assertThat(result.isEventShouldBeDropped()).isTrue();
        assertThat(result.getFilteredEpcs()).extracting(FilterResult.FilteredEpc::getReason)
                .containsExactly("INVALID_SSCC_STRUCTURE");
    }

    @Test
    void filterAggregationEvent_mixedChildEpcs_onlyValidKept() {
        AggregationEvent event = aggregationEvent(
                "urn:epc:id:sscc:0614141.1234567890",
                List.of(
                        "urn:epc:id:sgtin:0614141.107346.0001",
                        "urn:epc:id:gid:0614141.107346.9999"
                )
        );

        FilterResult result = service.filterAggregationEvent(event);

        assertThat(result.getAcceptedEpcs()).containsExactly("urn:epc:id:sgtin:0614141.107346.0001");
        assertThat(result.getFilteredEpcs()).hasSize(1);
        assertThat(result.getFilteredEpcs().get(0).getReason()).isEqualTo("FORBIDDEN_GID");
        assertThat(result.isEventShouldBeDropped()).isFalse();
    }

    // ─────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────

    private ObjectEvent objectEvent(List<String> epcs) {
        return ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId("urn:uuid:test-object-event")
                .epcList(epcs)
                .build();
    }

    private AggregationEvent aggregationEvent(String parentId, List<String> childEpcs) {
        return AggregationEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId("urn:uuid:test-aggregation-event")
                .parentId(parentId)
                .childEpcs(childEpcs)
                .build();
    }
}
