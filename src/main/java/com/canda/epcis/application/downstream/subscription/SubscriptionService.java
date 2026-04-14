package com.canda.epcis.application.downstream.subscription;

import com.canda.epcis.config.DownstreamConfig;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionEntity;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Verwaltet EPCIS-Subscriptions: CRUD + Seeding vordefinierter Subscriptions.
 *
 * Beim App-Start werden vordefinierte Subscriptions aus application.yml
 * idempotent angelegt (nur wenn subscription_id noch nicht existiert).
 * Bestehende Einträge werden NICHT überschrieben — manuelle Anpassungen bleiben erhalten.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final DownstreamConfig config;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               DownstreamConfig config) {
        this.subscriptionRepository = subscriptionRepository;
        this.config = config;
    }

    // ─────────────────────────────────────────────
    // Startup — Predefined Subscriptions
    // ─────────────────────────────────────────────

    /**
     * Legt vordefinierte Subscriptions aus application.yml an, wenn sie noch nicht existieren.
     * Wird nach vollständigem App-Start ausgeführt (nach Flyway-Migration).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedPredefinedSubscriptions() {
        List<DownstreamConfig.SubscriptionDefinition> definitions = config.getSubscriptions();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        for (DownstreamConfig.SubscriptionDefinition def : definitions) {
            if (subscriptionRepository.existsBySubscriptionId(def.getId())) {
                log.debug("SUBSCRIPTION_SEED_SKIP already exists: {}", def.getId());
                continue;
            }

            subscriptionRepository.save(SubscriptionEntity.builder()
                    .subscriptionId(def.getId())
                    .targetSystem(def.getTarget())
                    .callbackUrl(def.getCallbackUrl())
                    .authType(def.getAuthType() != null ? def.getAuthType() : "NONE")
                    .authToken(def.getAuthToken())
                    .eventTypes(def.getEventTypes())
                    .bizSteps(def.getBizSteps())
                    .dispositions(def.getDispositions())
                    .bizLocations(def.getBizLocations())
                    .readPoints(def.getReadPoints())
                    .active(def.isActive())
                    .createdAt(OffsetDateTime.now())
                    .build());

            log.info("SUBSCRIPTION_SEED created: {} target={}", def.getId(), def.getTarget());
        }
    }

    // ─────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────

    @Transactional
    public SubscriptionEntity register(SubscriptionRegistration request) {
        if (subscriptionRepository.existsBySubscriptionId(request.subscriptionId())) {
            throw new IllegalArgumentException(
                    "Subscription already exists: " + request.subscriptionId()
                    + " — use PUT to update");
        }

        SubscriptionEntity entity = SubscriptionEntity.builder()
                .subscriptionId(request.subscriptionId())
                .targetSystem(request.targetSystem())
                .callbackUrl(request.callbackUrl())
                .authType(request.authType() != null ? request.authType() : "NONE")
                .authToken(request.authToken())
                .eventTypes(request.eventTypes())
                .bizSteps(request.bizSteps())
                .dispositions(request.dispositions())
                .bizLocations(request.bizLocations())
                .readPoints(request.readPoints())
                .active(request.active())
                .createdAt(OffsetDateTime.now())
                .build();

        SubscriptionEntity saved = subscriptionRepository.save(entity);
        log.info("SUBSCRIPTION_REGISTERED subscriptionId={} target={}", saved.getSubscriptionId(), saved.getTargetSystem());
        return saved;
    }

    @Transactional
    public SubscriptionEntity setActive(String subscriptionId, boolean active) {
        SubscriptionEntity entity = subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        entity.setActive(active);
        SubscriptionEntity saved = subscriptionRepository.save(entity);
        log.info("SUBSCRIPTION_ACTIVE_CHANGED subscriptionId={} active={}", subscriptionId, active);
        return saved;
    }

    @Transactional
    public void delete(String subscriptionId) {
        if (!subscriptionRepository.existsBySubscriptionId(subscriptionId)) {
            throw new SubscriptionNotFoundException(subscriptionId);
        }
        subscriptionRepository.deleteBySubscriptionId(subscriptionId);
        log.info("SUBSCRIPTION_DELETED subscriptionId={}", subscriptionId);
    }

    public List<SubscriptionEntity> listAll() {
        return subscriptionRepository.findAll();
    }

    public SubscriptionEntity getById(String subscriptionId) {
        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
    }
}
