package com.canda.epcis.application.downstream.subscription;

import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxEntity;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxRepository;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionEntity;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Wird nach jedem Capture aufgerufen.
 * Prüft alle aktiven Subscriptions gegen das Event und erstellt Outbox-Einträge.
 *
 * Filter-Logik (NULL = kein Filter = alle Events passieren):
 * - eventType  → exakter Match gegen Event-Klasse (ObjectEvent, AggregationEvent)
 * - bizStep    → Short-Name-Match (CBV-Prefix wird normalisiert)
 * - disposition → Short-Name-Match
 * - bizLocation → SGLN-Prefix-Match
 * - readPoint  → SGLN-Prefix-Match
 *
 * Pro passender Subscription wird ein Outbox-Eintrag angelegt.
 * targetSystem = "SUBSCRIPTION", correlationId = subscriptionId.
 * OutboxProcessor löst callback_url + auth über SubscriptionRepository auf.
 */
@Service
public class SubscriptionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionDispatcher.class);

    private static final List<String> CBV_PREFIXES = List.of(
            "urn:epcglobal:cbv:bizstep:",
            "urn:epcglobal:cbv:disp:"
    );

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxRepository outboxRepository;

    public SubscriptionDispatcher(SubscriptionRepository subscriptionRepository,
                                  OutboxRepository outboxRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void dispatch(EpcisEvent event, String eventJson) {
        List<SubscriptionEntity> activeSubscriptions = subscriptionRepository.findByActiveTrue();
        if (activeSubscriptions.isEmpty()) {
            return;
        }

        int dispatched = 0;
        for (SubscriptionEntity sub : activeSubscriptions) {
            if (matches(sub, event)) {
                String messageId = UUID.randomUUID().toString();
                outboxRepository.save(OutboxEntity.builder()
                        .messageId(messageId)
                        .messageType("SUBSCRIPTION")
                        .targetSystem("SUBSCRIPTION")
                        .payload(eventJson)
                        .status("PENDING")
                        .createdAt(OffsetDateTime.now())
                        .retryCount(0)
                        .correlationId(sub.getSubscriptionId())
                        .build());

                sub.setLastTriggeredAt(OffsetDateTime.now());
                subscriptionRepository.save(sub);
                dispatched++;

                log.debug("SUBSCRIPTION_DISPATCHED subscriptionId={} eventType={}",
                        sub.getSubscriptionId(), event.getEventTypeName());
            }
        }

        if (dispatched > 0) {
            log.info("SUBSCRIPTION_DISPATCH_COMPLETE eventType={} dispatched={}",
                    event.getEventTypeName(), dispatched);
        }
    }

    // ─────────────────────────────────────────────
    // Filter-Logik
    // ─────────────────────────────────────────────

    boolean matches(SubscriptionEntity sub, EpcisEvent event) {
        if (hasFilter(sub.getEventTypes())
                && !csvContains(sub.getEventTypes(), event.getEventTypeName())) {
            return false;
        }
        if (hasFilter(sub.getBizSteps())
                && !csvContains(sub.getBizSteps(), shortName(event.getBizStep()))) {
            return false;
        }
        if (hasFilter(sub.getDispositions())
                && !csvContains(sub.getDispositions(), shortName(event.getDisposition()))) {
            return false;
        }
        if (hasFilter(sub.getBizLocations())
                && !sglnPrefixMatch(sub.getBizLocations(), event.getBizLocation())) {
            return false;
        }
        if (hasFilter(sub.getReadPoints())
                && !sglnPrefixMatch(sub.getReadPoints(), event.getReadPoint())) {
            return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private boolean hasFilter(String csv) {
        return csv != null && !csv.isBlank();
    }

    private boolean csvContains(String csv, String value) {
        if (value == null) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value::equals);
    }

    /**
     * SGLN-Prefix-Match: das Event-Feld muss mit einem der konfigurierten SGLNs beginnen.
     * Erlaubt Wildcard-Matching auf Standort-Ebene:
     * Konfiguriert: "urn:epc:id:sgln:4056019.00033" → matcht alle Sub-Standorte.
     */
    private boolean sglnPrefixMatch(String csv, String value) {
        if (value == null) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value::startsWith);
    }

    /**
     * Gibt den Short-Name zurück (CBV-Prefix entfernen).
     * Unbekannte Formate werden unverändert zurückgegeben.
     */
    String shortName(String uri) {
        if (uri == null || uri.isBlank()) return null;
        for (String prefix : CBV_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return uri.substring(prefix.length());
            }
        }
        return uri;
    }
}
