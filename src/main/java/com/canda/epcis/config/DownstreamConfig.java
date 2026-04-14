package com.canda.epcis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

/**
 * Konfiguration für alle Downstream-Integrationen (Phase 3).
 * Bindet epcis.downstream.* Properties aus application.yml.
 *
 * Alle Services sind default disabled — werden nur aktiviert wenn
 * die URL des Zielsystems konfiguriert ist. So startet die App
 * auch ohne externe Systeme fehlerfrei.
 *
 * @EnableScheduling hier zentralisiert — aktiviert @Scheduled in
 * OutboxProcessor, InventoryNotificationService, etc.
 */
@Getter
@Setter
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "epcis.downstream")
public class DownstreamConfig {

    private Gspm gspm = new Gspm();
    private Erp erp = new Erp();
    private Dwh dwh = new Dwh();
    private Ewm ewm = new Ewm();
    private Outbox outbox = new Outbox();

    /** Vordefinierte Subscriptions — werden beim App-Start idempotent angelegt. */
    private List<SubscriptionDefinition> subscriptions = new ArrayList<>();

    @Getter
    @Setter
    public static class Gspm {
        private String baseUrl = "http://localhost:9010";
        private String inventoryPath = "/api/inventory";
        private String shippingPath = "/api/shipping";
        private String arrivalPath = "/api/arrival";
        private long inventoryIntervalMs = 60000;
        private long shippingIntervalMs = 300000;
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Erp {
        private String baseUrl = "http://localhost:9011";
        private String goodsReceiptPath = "/api/goodsreceipt";
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Dwh {
        private String baseUrl = "http://localhost:9012";
        private String salesPath = "/api/sales";
        private String returnsPath = "/api/returns";
        private long intervalMs = 300000;
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Ewm {
        private String baseUrl = "http://localhost:9013";
        private String trolleyPath = "/api/trolley";
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Outbox {
        private long pollIntervalMs = 30000;
        private int maxRetries = 3;
        private int cleanupDays = 30;
    }

    /** Definition einer vordefinierten Subscription aus application.yml. */
    @Getter
    @Setter
    public static class SubscriptionDefinition {
        private String id;
        private String target;
        private String callbackUrl;
        private String authType = "NONE";
        private String authToken;
        private String eventTypes;
        private String bizSteps;
        private String dispositions;
        private String bizLocations;
        private String readPoints;
        private boolean active = true;
    }
}
