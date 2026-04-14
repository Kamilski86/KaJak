package com.canda.epcis.application.inventory;

import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryRebuildServiceTest {

    @Mock EpcisEventRepository      epcisEventRepository;
    @Mock EpcStateRepository        epcStateRepository;
    @Mock SsccContentRepository     ssccContentRepository;
    @Mock MovementHistoryRepository movementHistoryRepository;
    @Mock InventoryProcessorService inventoryProcessorService;

    private InventoryRebuildService rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new InventoryRebuildService(
                epcisEventRepository, epcStateRepository, ssccContentRepository,
                movementHistoryRepository, inventoryProcessorService,
                new ObjectMapper());
    }

    // ─────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────

    @Test
    void rebuild_withTwoValidEvents_processesAllAndReturnsStats() {
        when(epcisEventRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(
                        objectEventEntity("evt-1"),
                        objectEventEntity("evt-2")));

        InventoryRebuildService.RebuildResult result = rebuildService.rebuild();

        assertThat(result.getEventsProcessed()).isEqualTo(2);
        assertThat(result.getEventsSkipped()).isEqualTo(0);
        assertThat(result.getErrors()).isEqualTo(0);
        verify(inventoryProcessorService, times(2)).process(any());
    }

    @Test
    void rebuild_clearsAllInventoryTablesBeforeProcessing() {
        when(epcisEventRepository.findAll(any(Sort.class))).thenReturn(List.of());

        rebuildService.rebuild();

        verify(movementHistoryRepository).deleteAll();
        verify(ssccContentRepository).deleteAll();
        verify(epcStateRepository).deleteAll();
    }

    // ─────────────────────────────────────────────
    // Resilience — skip unknown / broken payloads
    // ─────────────────────────────────────────────

    @Test
    void rebuild_withUnknownEventType_skipsAndContinues() {
        String unknownPayload = "{\"type\":\"TransformationEvent\",\"eventTime\":\"2024-01-01T10:00:00Z\"}";
        EpcisEventEntity unknownEntity = EpcisEventEntity.builder()
                .eventType("TransformationEvent")
                .eventTime(OffsetDateTime.now())
                .payload(unknownPayload)
                .build();

        when(epcisEventRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(unknownEntity, objectEventEntity("evt-good")));

        InventoryRebuildService.RebuildResult result = rebuildService.rebuild();

        assertThat(result.getEventsSkipped()).isEqualTo(1);
        assertThat(result.getEventsProcessed()).isEqualTo(1);
        verify(inventoryProcessorService, times(1)).process(any());
    }

    @Test
    void rebuild_withBrokenJsonPayload_countsAsSkipped() {
        // deserializeEvent() catches JSON parse errors internally and returns null.
        // The outer loop treats null → skipped, not errors.
        EpcisEventEntity brokenEntity = EpcisEventEntity.builder()
                .eventType("ObjectEvent")
                .eventTime(OffsetDateTime.now())
                .payload("NOT_VALID_JSON{{{")
                .build();

        when(epcisEventRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(brokenEntity));

        InventoryRebuildService.RebuildResult result = rebuildService.rebuild();

        assertThat(result.getEventsSkipped()).isEqualTo(1);
        assertThat(result.getEventsProcessed()).isEqualTo(0);
        assertThat(result.getErrors()).isEqualTo(0);
        verify(inventoryProcessorService, never()).process(any());
    }

    @Test
    void rebuild_emptyEventStore_returnsZeroStats() {
        when(epcisEventRepository.findAll(any(Sort.class))).thenReturn(List.of());

        InventoryRebuildService.RebuildResult result = rebuildService.rebuild();

        assertThat(result.getEventsProcessed()).isEqualTo(0);
        assertThat(result.getEventsSkipped()).isEqualTo(0);
        assertThat(result.getErrors()).isEqualTo(0);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────

    private EpcisEventEntity objectEventEntity(String eventId) {
        String payload = """
                {
                  "type": "ObjectEvent",
                  "eventID": "%s",
                  "eventTime": "2024-06-01T10:00:00Z",
                  "eventTimeZoneOffset": "+02:00",
                  "action": "OBSERVE",
                  "bizStep": "shipping",
                  "disposition": "in_transit",
                  "bizLocation": {"id": "urn:epc:id:sgln:4056019.00000.0"},
                  "epcList": ["urn:epc:id:sgtin:4056019.010532.0000001"]
                }
                """.formatted(eventId);

        return EpcisEventEntity.builder()
                .eventType("ObjectEvent")
                .eventTime(OffsetDateTime.parse("2024-06-01T10:00:00Z"))
                .payload(payload)
                .build();
    }
}
