package com.canda.epcis.infrastructure;

import com.canda.epcis.config.DownstreamConfig;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxEntity;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxRepository;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionEntity;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Liest PENDING Outbox-Einträge und sendet sie an die Zielsysteme.
 *
 * Läuft als Scheduled Job:
 * - Normal (GSPM/ERP/DWH/SUBSCRIPTION): alle 30 Sekunden (konfigurierbar)
 * - EWM (Trolley): alle 1 Sekunde (wegen 10s SLA für REQ309)
 *
 * Retry-Logik:
 * - Max 3 Retries (konfigurierbar)
 * - Bei Fehler: retry_count erhöhen, last_error setzen
 * - Nach max Retries: status=FAILED, Alert loggen, manueller Eingriff nötig
 *
 * Disabled-Targets werden übersprungen (kein Retry, kein Fehler) —
 * default enabled=false verhindert Connection-Errors beim Start.
 *
 * HTTP-Client: Spring RestClient (Spring Boot 4.x)
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT    = "SENT";
    private static final String STATUS_FAILED  = "FAILED";

    private final OutboxRepository outboxRepository;
    private final DownstreamConfig config;
    private final SubscriptionRepository subscriptionRepository;
    private final RestClient restClient;

    public OutboxProcessor(OutboxRepository outboxRepository,
                           DownstreamConfig config,
                           SubscriptionRepository subscriptionRepository) {
        this.outboxRepository = outboxRepository;
        this.config = config;
        this.subscriptionRepository = subscriptionRepository;
        this.restClient = RestClient.create();
    }

    // ─────────────────────────────────────────────
    // Scheduled Jobs
    // ─────────────────────────────────────────────

    /**
     * Verarbeitet alle PENDING Messages für GSPM/ERP/DWH/SUBSCRIPTION.
     * EWM wird hier bewusst ausgeschlossen — separater Job mit 1s-Takt.
     */
    @Scheduled(fixedRateString = "${epcis.downstream.outbox.poll-interval-ms:30000}")
    public void processOutbox() {
        List<OutboxEntity> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING)
                .stream()
                .filter(m -> !"EWM".equals(m.getTargetSystem()))
                .toList();

        if (!pending.isEmpty()) {
            log.debug("OUTBOX_POLL count={}", pending.size());
        }

        for (OutboxEntity message : pending) {
            processMessage(message);
        }
    }

    /**
     * Verarbeitet PENDING EWM-Messages alle 1 Sekunde.
     * Dedizierter Job für das 10-Sekunden SLA (REQ309 Trolley → SAP EWM).
     */
    @Scheduled(fixedRate = 1000)
    public void processEwmOutbox() {
        List<OutboxEntity> pending = outboxRepository
                .findByStatusAndTargetSystemOrderByCreatedAtAsc(STATUS_PENDING, "EWM");

        for (OutboxEntity message : pending) {
            processMessage(message);
        }
    }

    // ─────────────────────────────────────────────
    // Core Processing
    // ─────────────────────────────────────────────

    @Transactional
    public void processMessage(OutboxEntity message) {
        if (!isEnabled(message.getTargetSystem())) {
            log.debug("OUTBOX_SKIP_DISABLED messageId={} targetSystem={}",
                    message.getMessageId(), message.getTargetSystem());
            return;
        }

        String url = resolveUrl(message);
        if (url == null) {
            log.warn("OUTBOX_NO_URL messageId={} targetSystem={} messageType={}",
                    message.getMessageId(), message.getTargetSystem(), message.getMessageType());
            return;
        }

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);

            spec = applyAuth(spec, message);

            spec.body(message.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            message.setStatus(STATUS_SENT);
            message.setSentAt(OffsetDateTime.now());
            outboxRepository.save(message);
            log.info("OUTBOX_SENT messageId={} targetSystem={} messageType={}",
                    message.getMessageId(), message.getTargetSystem(), message.getMessageType());

        } catch (Exception e) {
            int retries = message.getRetryCount() + 1;
            message.setRetryCount(retries);
            message.setLastError(e.getMessage());

            if (retries >= config.getOutbox().getMaxRetries()) {
                message.setStatus(STATUS_FAILED);
                log.error("OUTBOX_FAILED messageId={} targetSystem={} retries={} error={}",
                        message.getMessageId(), message.getTargetSystem(), retries, e.getMessage());
            } else {
                log.warn("OUTBOX_RETRY messageId={} targetSystem={} attempt={}/{} error={}",
                        message.getMessageId(), message.getTargetSystem(),
                        retries, config.getOutbox().getMaxRetries(), e.getMessage());
            }
            outboxRepository.save(message);
        }
    }

    // ─────────────────────────────────────────────
    // URL Resolution
    // ─────────────────────────────────────────────

    /**
     * Ermittelt die Ziel-URL anhand von targetSystem + messageType.
     * SUBSCRIPTION: URL aus SubscriptionRepository via correlationId (= subscriptionId).
     */
    String resolveUrl(OutboxEntity message) {
        return switch (message.getTargetSystem()) {
            case "GSPM" -> switch (message.getMessageType()) {
                case "RFID_INVENTORY" -> config.getGspm().getBaseUrl() + config.getGspm().getInventoryPath();
                case "RFID_SHIPPING"  -> config.getGspm().getBaseUrl() + config.getGspm().getShippingPath();
                case "RFID_ARRIVAL"   -> config.getGspm().getBaseUrl() + config.getGspm().getArrivalPath();
                default -> null;
            };
            case "ERP" -> config.getErp().getBaseUrl() + config.getErp().getGoodsReceiptPath();
            case "DWH" -> switch (message.getMessageType()) {
                case "RFID_SALES"   -> config.getDwh().getBaseUrl() + config.getDwh().getSalesPath();
                case "RFID_RETURNS" -> config.getDwh().getBaseUrl() + config.getDwh().getReturnsPath();
                default -> null;
            };
            case "EWM" -> config.getEwm().getBaseUrl() + config.getEwm().getTrolleyPath();
            case "SUBSCRIPTION" -> resolveSubscriptionUrl(message.getCorrelationId());
            default -> null;
        };
    }

    private String resolveSubscriptionUrl(String subscriptionId) {
        if (subscriptionId == null) return null;
        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .map(SubscriptionEntity::getCallbackUrl)
                .orElse(null);
    }

    /**
     * Fügt Auth-Header hinzu falls die Subscription einen Bearer-Token hat.
     * Für Nicht-Subscription-Nachrichten: unverändert zurückgeben.
     */
    private RestClient.RequestBodySpec applyAuth(RestClient.RequestBodySpec spec, OutboxEntity message) {
        if (!"SUBSCRIPTION".equals(message.getTargetSystem()) || message.getCorrelationId() == null) {
            return spec;
        }
        Optional<SubscriptionEntity> sub = subscriptionRepository.findBySubscriptionId(message.getCorrelationId());
        if (sub.isEmpty()) return spec;

        SubscriptionEntity subscription = sub.get();
        if ("BEARER".equals(subscription.getAuthType()) && subscription.getAuthToken() != null) {
            return (RestClient.RequestBodySpec) spec.header("Authorization", "Bearer " + subscription.getAuthToken());
        }
        if ("BASIC".equals(subscription.getAuthType()) && subscription.getAuthToken() != null) {
            return (RestClient.RequestBodySpec) spec.header("Authorization", "Basic " + subscription.getAuthToken());
        }
        return spec;
    }

    private boolean isEnabled(String targetSystem) {
        return switch (targetSystem) {
            case "GSPM" -> config.getGspm().isEnabled();
            case "ERP"  -> config.getErp().isEnabled();
            case "DWH"  -> config.getDwh().isEnabled();
            case "EWM"  -> config.getEwm().isEnabled();
            case "SUBSCRIPTION" -> true; // Gesteuert durch subscription.active beim Dispatch
            default     -> false;
        };
    }
}
