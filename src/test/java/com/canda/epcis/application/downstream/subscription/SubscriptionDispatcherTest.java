package com.canda.epcis.application.downstream.subscription;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxEntity;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxRepository;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionEntity;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionDispatcherTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock OutboxRepository       outboxRepository;

    private SubscriptionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SubscriptionDispatcher(subscriptionRepository, outboxRepository);
    }

    // ─────────────────────────────────────────────
    // Basic dispatch
    // ─────────────────────────────────────────────

    @Test
    void dispatch_matchingSubscription_createsOutboxEntry() {
        SubscriptionEntity sub = subscription("ITEMOPTIX-GENERAL", null, null, null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(shippingEvent(), "{\"event\":true}");

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEntity outbox = captor.getValue();
        assertThat(outbox.getTargetSystem()).isEqualTo("SUBSCRIPTION");
        assertThat(outbox.getMessageType()).isEqualTo("SUBSCRIPTION");
        assertThat(outbox.getCorrelationId()).isEqualTo("ITEMOPTIX-GENERAL");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getPayload()).isEqualTo("{\"event\":true}");
    }

    @Test
    void dispatch_noActiveSubscriptions_noOutboxEntry() {
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        dispatcher.dispatch(shippingEvent(), "{\"event\":true}");

        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // eventType filter
    // ─────────────────────────────────────────────

    @Test
    void dispatch_eventTypeFilter_matchesObjectEvent() {
        SubscriptionEntity sub = subscription("S1", "ObjectEvent", null, null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(shippingEvent(), "{}");

        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void dispatch_eventTypeFilter_noMatchForWrongType() {
        SubscriptionEntity sub = subscription("S1", "AggregationEvent", null, null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        dispatcher.dispatch(shippingEvent(), "{}"); // ObjectEvent

        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // bizStep filter
    // ─────────────────────────────────────────────

    @Test
    void dispatch_bizStepFilter_matchingShortName() {
        SubscriptionEntity sub = subscription("S1", null, "shipping,receiving", null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Event has full CBV URN → must be normalized to short name
        dispatcher.dispatch(shippingEvent(), "{}");

        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void dispatch_bizStepFilter_noMatchForOtherBizStep() {
        SubscriptionEntity sub = subscription("S1", null, "receiving", null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        dispatcher.dispatch(shippingEvent(), "{}"); // bizStep=shipping

        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // bizLocation filter — SGLN prefix match
    // ─────────────────────────────────────────────

    @Test
    void dispatch_bizLocationFilter_prefixMatchSucceeds() {
        SubscriptionEntity sub = subscription("S1", null, null, null,
                "urn:epc:id:sgln:4056019.00033", null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObjectEvent event = eventWithLocation("urn:epc:id:sgln:4056019.00033.SALESFLOOR");
        dispatcher.dispatch(event, "{}");

        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void dispatch_bizLocationFilter_noMatchForDifferentLocation() {
        SubscriptionEntity sub = subscription("S1", null, null, null,
                "urn:epc:id:sgln:4056019.00033", null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        ObjectEvent event = eventWithLocation("urn:epc:id:sgln:9999999.00001.BACKROOM");
        dispatcher.dispatch(event, "{}");

        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // Multiple subscriptions
    // ─────────────────────────────────────────────

    @Test
    void dispatch_multipleMatchingSubscriptions_createsOneOutboxEntryEach() {
        SubscriptionEntity sub1 = subscription("ITEMOPTIX",  null, null, null, null, null);
        SubscriptionEntity sub2 = subscription("MULE-RSTO", null, "shipping", null, null, null);
        SubscriptionEntity sub3 = subscription("MULE-RECEIVING", null, "receiving", null, null, null);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub1, sub2, sub3));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(shippingEvent(), "{}");

        // sub1: no filter → match. sub2: shipping → match. sub3: receiving → no match.
        verify(outboxRepository, times(2)).save(any());
    }

    // ─────────────────────────────────────────────
    // shortName helper
    // ─────────────────────────────────────────────

    @Test
    void shortName_stripsKnownCbvBizStepPrefix() {
        assertThat(dispatcher.shortName("urn:epcglobal:cbv:bizstep:shipping")).isEqualTo("shipping");
    }

    @Test
    void shortName_stripsKnownCbvDispPrefix() {
        assertThat(dispatcher.shortName("urn:epcglobal:cbv:disp:in_transit")).isEqualTo("in_transit");
    }

    @Test
    void shortName_alreadyShortName_returnsUnchanged() {
        assertThat(dispatcher.shortName("shipping")).isEqualTo("shipping");
    }

    @Test
    void shortName_null_returnsNull() {
        assertThat(dispatcher.shortName(null)).isNull();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private ObjectEvent shippingEvent() {
        return ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId("urn:uuid:test-001")
                .action(Action.OBSERVE)
                .bizStep("urn:epcglobal:cbv:bizstep:shipping")
                .disposition("urn:epcglobal:cbv:disp:in_transit")
                .bizLocation("urn:epc:id:sgln:4056019.00033.SALESFLOOR")
                .epcList(List.of("urn:epc:id:sgtin:0614141.107346.0001"))
                .build();
    }

    private ObjectEvent eventWithLocation(String bizLocation) {
        return ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.OBSERVE)
                .bizStep("urn:epcglobal:cbv:bizstep:storing")
                .bizLocation(bizLocation)
                .epcList(List.of("urn:epc:id:sgtin:0614141.107346.0001"))
                .build();
    }

    private SubscriptionEntity subscription(String id, String eventTypes, String bizSteps,
                                            String dispositions, String bizLocations, String readPoints) {
        return SubscriptionEntity.builder()
                .subscriptionId(id)
                .targetSystem("TEST")
                .callbackUrl("http://localhost/callback")
                .authType("NONE")
                .eventTypes(eventTypes)
                .bizSteps(bizSteps)
                .dispositions(dispositions)
                .bizLocations(bizLocations)
                .readPoints(readPoints)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
