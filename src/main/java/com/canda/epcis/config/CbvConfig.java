package com.canda.epcis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the CBV 2.0 validator (Phase 4 — Block A).
 * Values are loaded from application.yml under the prefix "epcis.cbv".
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.cbv")
public class CbvConfig {

    /**
     * false (default) — log CBV violations as WARN, continue processing.
     * true            — quarantine violating events, do not persist them.
     */
    private boolean strictMode = false;

    /**
     * true (default) — load the full CBV 2.0 vocabulary into DB on startup.
     * false           — skip loading (e.g. when vocabulary is managed externally).
     */
    private boolean loadVocabulary = true;

    /**
     * true (default) — deprecated CBV values are accepted (WARN logged).
     * false           — deprecated values are treated as violations.
     */
    private boolean allowDeprecated = true;
}
