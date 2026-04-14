package com.canda.epcis.application.inventory;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateEntity;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryEntity;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryProcessorServiceTest {

    @Mock EpcStateRepository        epcStateRepository;
    @Mock SsccContentRepository     ssccContentRepository;
    @Mock MovementHistoryRepository movementHistoryRepository;

    private InventoryProcessorService service;

    private static final String EPC1  = "urn:epc:id:sgtin:4056019.010532.1001";
    private static final String EPC2  = "urn:epc:id:sgtin:4056019.010532.1002";
    private static final String SSCC  = "urn:epc:id:sscc:4056019.0111111122";
    private static final String GLN   = "urn:epc:id:sgln:4056019.00334.0";
    private static final String EVENT_ID = "urn:uuid:aaaaaaaa-0000-0000-0000-000000000001";

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2024-01-15T10:00:00+01:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2024-01-15T12:00:00+01:00");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2024-01-15T08:00:00+01:00");

    @BeforeEach
    void setUp() {
        service = new InventoryProcessorService(
                epcStateRepository, ssccContentRepository, movementHistoryRepository);
    }

    // ─────────────────────────────────────────────
    // ObjectEvent ADD — CREATE
    // ─────────────────────────────────────────────

    @Test
    void objectEventAdd_newEpc_createsEpcStateAndHistory() {
        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, EVENT_ID)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(objectEvent(EVENT_ID, Action.ADD, List.of(EPC1),
                "urn:epcglobal:cbv:bizstep:encoding",
                "urn:epcglobal:cbv:disp:encoded", GLN, T1));

        ArgumentCaptor<EpcStateEntity> stateCaptor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository).save(stateCaptor.capture());
        EpcStateEntity saved = stateCaptor.getValue();

        assertThat(saved.getEpcUrn()).isEqualTo(EPC1);
        assertThat(saved.getCurrentStatus()).isEqualTo("encoded");
        assertThat(saved.getBizLocation()).isEqualTo(GLN);
        assertThat(saved.getLastEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getEncodedAt()).isEqualTo(T1);
        assertThat(saved.getCreateDate()).isNotNull();

        verify(movementHistoryRepository).save(any(MovementHistoryEntity.class));
    }

    // ─────────────────────────────────────────────
    // Movement History — Eintrag korrekt
    // ─────────────────────────────────────────────

    @Test
    void objectEventAdd_movementHistoryContainsCorrectFields() {
        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, EVENT_ID)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(objectEvent(EVENT_ID, Action.ADD, List.of(EPC1),
                "urn:epcglobal:cbv:bizstep:receiving",
                "urn:epcglobal:cbv:disp:in_progress", GLN, T1));

        ArgumentCaptor<MovementHistoryEntity> histCaptor =
                ArgumentCaptor.forClass(MovementHistoryEntity.class);
        verify(movementHistoryRepository).save(histCaptor.capture());
        MovementHistoryEntity hist = histCaptor.getValue();

        assertThat(hist.getEpcUrn()).isEqualTo(EPC1);
        assertThat(hist.getEventId()).isEqualTo(EVENT_ID);
        assertThat(hist.getEventType()).isEqualTo("ObjectEvent");
        assertThat(hist.getAction()).isEqualTo("ADD");
        assertThat(hist.getBizLocation()).isEqualTo(GLN);
        assertThat(hist.getEventTime()).isEqualTo(T1);
    }

    // ─────────────────────────────────────────────
    // Idempotenz
    // ─────────────────────────────────────────────

    @Test
    void objectEventAdd_sameEventIdTwice_idempotent() {
        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, EVENT_ID)).thenReturn(true);

        service.process(objectEvent(EVENT_ID, Action.ADD, List.of(EPC1), null, null, GLN, T1));
        service.process(objectEvent(EVENT_ID, Action.ADD, List.of(EPC1), null, null, GLN, T1));

        verify(epcStateRepository, never()).save(any());
        verify(movementHistoryRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // OBSERVE — aktualisiert EpcState
    // ─────────────────────────────────────────────

    @Test
    void objectEventObserve_existingEpc_updatesStatus() {
        String observeId = "urn:uuid:observe-001";
        EpcStateEntity existing = existingState(EPC1, "encoded", T1);

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, observeId)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.of(existing));
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(objectEvent(observeId, Action.OBSERVE, List.of(EPC1),
                "urn:epcglobal:cbv:bizstep:shipping",
                "urn:epcglobal:cbv:disp:in_transit", GLN, T2));

        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("in_transit");
        assertThat(captor.getValue().getInTransitAt()).isEqualTo(T2);
    }

    // ─────────────────────────────────────────────
    // Out-of-Order (REQ217)
    // ─────────────────────────────────────────────

    @Test
    void objectEvent_outOfOrder_doesNotUpdateBizLocation() {
        String lateId = "urn:uuid:late-001";
        EpcStateEntity existing = existingState(EPC1, "in_transit", T2);
        existing.setBizLocation("urn:epc:id:sgln:4056019.00334.NEWLOCATION");

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, lateId)).thenReturn(false);
        // isOutOfOrder check — T0 < T2, so it IS out of order
        when(epcStateRepository.findByEpcUrn(EPC1))
                .thenReturn(Optional.of(existing))  // first call in isOutOfOrder
                .thenReturn(Optional.of(existing)); // second call in updateEpcState
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(objectEvent(lateId, Action.OBSERVE, List.of(EPC1),
                null, "urn:epcglobal:cbv:disp:encoded",
                "urn:epc:id:sgln:4056019.00334.OLDLOCATION", T0));

        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository).save(captor.capture());

        // bizLocation muss unverändert bleiben
        assertThat(captor.getValue().getBizLocation())
                .isEqualTo("urn:epc:id:sgln:4056019.00334.NEWLOCATION");
        // History wird trotzdem gespeichert
        verify(movementHistoryRepository).save(any());
    }

    // ─────────────────────────────────────────────
    // Disposition-Timestamp: out-of-order NICHT überschreiben
    // ─────────────────────────────────────────────

    @Test
    void dispositionTimestamp_outOfOrder_notOverwritten() {
        String lateId = "urn:uuid:late-002";
        EpcStateEntity existing = existingState(EPC1, "in_transit", T2);
        existing.setInTransitAt(T2); // bereits gesetzt mit T2

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, lateId)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // out-of-order event mit T0 < T2 und gleicher Disposition
        service.process(objectEvent(lateId, Action.OBSERVE, List.of(EPC1),
                null, "urn:epcglobal:cbv:disp:in_transit", GLN, T0));

        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository).save(captor.capture());
        // Timestamp bleibt T2 — T0 ist älter
        assertThat(captor.getValue().getInTransitAt()).isEqualTo(T2);
    }

    // ─────────────────────────────────────────────
    // AggregationEvent ADD — SSCC Content
    // ─────────────────────────────────────────────

    @Test
    void aggregationEventAdd_createsSSCCContentAndSetsEpcStateSscc() {
        String aggId = "urn:uuid:agg-add-001";

        when(movementHistoryRepository.existsByEpcUrnAndEventId(anyString(), anyString())).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(anyString())).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ssccContentRepository.findByChildEpc(anyString())).thenReturn(List.of());
        when(ssccContentRepository.existsBySsccUrnAndChildEpc(anyString(), anyString())).thenReturn(false);
        when(ssccContentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(aggId, Action.ADD, SSCC,
                List.of(EPC1, EPC2), "urn:epcglobal:cbv:disp:in_progress", GLN, T1));

        verify(ssccContentRepository, times(2)).save(any(SsccContentEntity.class));
        verify(movementHistoryRepository, times(2)).save(any(MovementHistoryEntity.class));
    }

    @Test
    void aggregationEventAdd_setsSSCCOnEpcState_onlyFirstTime() {
        String aggId = "urn:uuid:agg-add-002";

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, aggId)).thenReturn(false);
        when(ssccContentRepository.findByChildEpc(EPC1)).thenReturn(List.of());
        when(ssccContentRepository.existsBySsccUrnAndChildEpc(SSCC, EPC1)).thenReturn(false);
        when(ssccContentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // EpcState bereits vorhanden, aber noch kein SSCC gesetzt
        EpcStateEntity existing = existingState(EPC1, "encoded", T1);
        existing.setSscc(null);
        when(epcStateRepository.findByEpcUrn(EPC1))
                .thenReturn(Optional.empty())  // isOutOfOrder
                .thenReturn(Optional.of(existing)); // sscc-Feld setzen
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(aggId, Action.ADD, SSCC,
                List.of(EPC1), "urn:epcglobal:cbv:disp:in_progress", GLN, T1));

        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository, times(2)).save(captor.capture());
        // Letzter save-Aufruf setzt SSCC
        EpcStateEntity lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getSscc()).isEqualTo(SSCC);
    }

    @Test
    void aggregationEventAdd_ssccAlreadySet_notOverwritten() {
        String aggId = "urn:uuid:agg-add-003";
        String otherSscc = "urn:epc:id:sscc:4056019.0999999999";

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, aggId)).thenReturn(false);
        when(ssccContentRepository.findByChildEpc(EPC1)).thenReturn(List.of());
        when(ssccContentRepository.existsBySsccUrnAndChildEpc(SSCC, EPC1)).thenReturn(false);
        when(ssccContentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // EpcState mit bereits gesetztem SSCC
        EpcStateEntity existing = existingState(EPC1, "encoded", T1);
        existing.setSscc(otherSscc);
        when(epcStateRepository.findByEpcUrn(EPC1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(aggId, Action.ADD, SSCC,
                List.of(EPC1), null, GLN, T1));

        // EpcState wird genau einmal gespeichert (updateEpcState).
        // Der SSCC-Setz-Zweig wird übersprungen, da entity.getSscc() != null → kein zweiter save.
        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository, times(1)).save(captor.capture());
        EpcStateEntity saved = captor.getValue();
        // SSCC bleibt der ursprüngliche Wert — nicht überschrieben
        assertThat(saved.getSscc()).isEqualTo(otherSscc);
    }

    // ─────────────────────────────────────────────
    // AggregationEvent DELETE — REQ215 + partiell
    // ─────────────────────────────────────────────

    @Test
    void aggregationEventDelete_withChildEpcs_onlyThoseRemoved() {
        String delId = "urn:uuid:agg-del-001";

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, delId)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(delId, Action.DELETE, SSCC,
                List.of(EPC1), null, GLN, T1));

        verify(ssccContentRepository).deleteBySsccUrnAndChildEpcIn(SSCC, List.of(EPC1));
        verify(ssccContentRepository, never()).deleteBySsccUrn(anyString());
    }

    @Test
    void aggregationEventDelete_emptyChildEpcs_deletesAll_REQ215() {
        String delId = "urn:uuid:agg-del-002";
        SsccContentEntity row = SsccContentEntity.builder()
                .ssccUrn(SSCC).childEpc(EPC1).bizLocation(GLN)
                .addedAt(T1).build();

        when(ssccContentRepository.findBySsccUrn(SSCC)).thenReturn(List.of(row));
        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, delId)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(delId, Action.DELETE, SSCC,
                List.of(), null, GLN, T1));

        verify(ssccContentRepository).deleteBySsccUrn(SSCC);
        verify(movementHistoryRepository).save(any());
    }

    // ─────────────────────────────────────────────
    // REQ210 — SGTIN nur in einer SSCC
    // ─────────────────────────────────────────────

    @Test
    void aggregationEventAdd_sgtinInOtherSscc_removedFromOld_REQ210() {
        String aggId = "urn:uuid:agg-req210";
        String oldSscc = "urn:epc:id:sscc:4056019.0000000001";
        SsccContentEntity oldAssignment = SsccContentEntity.builder()
                .ssccUrn(oldSscc).childEpc(EPC1).build();

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, aggId)).thenReturn(false);
        when(ssccContentRepository.findByChildEpc(EPC1)).thenReturn(List.of(oldAssignment));
        when(ssccContentRepository.existsBySsccUrnAndChildEpc(SSCC, EPC1)).thenReturn(false);
        when(ssccContentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(epcStateRepository.findByEpcUrn(anyString())).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(aggregationEvent(aggId, Action.ADD, SSCC,
                List.of(EPC1), null, GLN, T1));

        verify(ssccContentRepository).deleteBySsccUrnAndChildEpcIn(oldSscc, List.of(EPC1));
        verify(ssccContentRepository).save(any(SsccContentEntity.class));
    }

    // ─────────────────────────────────────────────
    // BUG1-FIX: Deterministischer Event-ID-Fallback — keine Duplikate
    // ─────────────────────────────────────────────

    @Test
    void objectEventAdd_nullEventId_sameEventProcessedTwice_isIdempotent() {
        // Erstes process() → not in history → speichern
        // Zweites process() → same deterministic hash → already in history → skip
        when(movementHistoryRepository.existsByEpcUrnAndEventId(eq(EPC1), anyString()))
                .thenReturn(false)   // 1. Aufruf
                .thenReturn(true);   // 2. Aufruf (same hash)
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectEvent event = objectEvent(null, Action.ADD, List.of(EPC1), null, null, GLN, T1);
        service.process(event);
        service.process(event);

        // Nur 1× gespeichert, obwohl 2× aufgerufen
        verify(movementHistoryRepository, times(1)).save(any(MovementHistoryEntity.class));
        verify(epcStateRepository, times(1)).save(any(EpcStateEntity.class));
    }

    // ─────────────────────────────────────────────
    // BUG2-FIX: bizLocation null → readPoint als Fallback
    // ─────────────────────────────────────────────

    @Test
    void aggregationEventAdd_bizLocationNull_usesReadPointAsFallback() {
        String aggId   = "urn:uuid:agg-readpoint-001";
        String readPt  = "urn:epc:id:sgln:4056019.00334.1";

        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, aggId)).thenReturn(false);
        when(ssccContentRepository.findByChildEpc(EPC1)).thenReturn(List.of());
        when(ssccContentRepository.existsBySsccUrnAndChildEpc(SSCC, EPC1)).thenReturn(false);
        when(ssccContentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AggregationEvent event = AggregationEvent.builder()
                .eventId(aggId).eventTime(T1).action(Action.ADD)
                .parentId(SSCC).childEpcs(List.of(EPC1))
                .bizLocation(null)
                .readPoint(readPt)
                .build();
        service.process(event);

        // epc_state.biz_location = readPoint
        ArgumentCaptor<EpcStateEntity> stateCaptor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository, times(1)).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getBizLocation()).isEqualTo(readPt);

        // sscc_content.biz_location = readPoint
        ArgumentCaptor<SsccContentEntity> ssccCaptor = ArgumentCaptor.forClass(SsccContentEntity.class);
        verify(ssccContentRepository).save(ssccCaptor.capture());
        assertThat(ssccCaptor.getValue().getBizLocation()).isEqualTo(readPt);
    }

    // ─────────────────────────────────────────────
    // Disposition-Timestamp gesetzt
    // ─────────────────────────────────────────────

    @Test
    void dispositionTimestamp_inTransit_setCorrectly() {
        String evId = "urn:uuid:disp-ts-001";
        when(movementHistoryRepository.existsByEpcUrnAndEventId(EPC1, evId)).thenReturn(false);
        when(epcStateRepository.findByEpcUrn(EPC1)).thenReturn(Optional.empty());
        when(epcStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(objectEvent(evId, Action.OBSERVE, List.of(EPC1),
                null, "urn:epcglobal:cbv:disp:in_transit", GLN, T1));

        ArgumentCaptor<EpcStateEntity> captor = ArgumentCaptor.forClass(EpcStateEntity.class);
        verify(epcStateRepository).save(captor.capture());
        assertThat(captor.getValue().getInTransitAt()).isEqualTo(T1);
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo("in_transit");
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private ObjectEvent objectEvent(String eventId, Action action, List<String> epcs,
                                    String bizStep, String disposition,
                                    String bizLocation, OffsetDateTime time) {
        return ObjectEvent.builder()
                .eventId(eventId).eventTime(time).action(action)
                .bizStep(bizStep).disposition(disposition)
                .bizLocation(bizLocation).epcList(epcs)
                .build();
    }

    private AggregationEvent aggregationEvent(String eventId, Action action, String parentId,
                                              List<String> childEpcs, String disposition,
                                              String bizLocation, OffsetDateTime time) {
        return AggregationEvent.builder()
                .eventId(eventId).eventTime(time).action(action)
                .disposition(disposition).bizLocation(bizLocation)
                .parentId(parentId).childEpcs(childEpcs)
                .build();
    }

    private EpcStateEntity existingState(String epcUrn, String status, OffsetDateTime lastEventTime) {
        EpcStateEntity e = new EpcStateEntity();
        e.setEpcUrn(epcUrn);
        e.setCurrentStatus(status);
        e.setBizLocation(GLN);
        e.setLastEventTime(lastEventTime);
        e.setCreateDate(lastEventTime);
        e.setUpdateDate(lastEventTime);
        return e;
    }
}
