package com.canda.epcis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Konfiguration für den Inventory Service (Phase 2).
 * Bindet epcis.inventory.* Properties aus application.yml.
 *
 * Abweichung von Spec-YAML: availableMerchandiseDispositions verwendet Short-Names
 * (z.B. "sellable_accessible") statt Full-URNs, konsistent mit EpcState.currentStatus.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.inventory")
public class InventoryConfig {

    private boolean enabled = true;
    private int maxHistoryLimit = 1000;
    private int defaultHistoryLimit = 100;
    private boolean rebuildEnabled = true;

    /**
     * REQ301: Konfigurierbare Liste der "Available Merchandise" Dispositionen.
     * Muss mit den Short-Names in EpcState.currentStatus übereinstimmen.
     * Kann ohne Code-Änderung in application.yml überschrieben werden.
     */
    private List<String> availableMerchandiseDispositions = List.of(
            "sellable_accessible",
            "sellable_not_accessible"
    );
}
