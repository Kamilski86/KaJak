package com.canda.epcis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the GS1 Digital Link Resolver (Phase 4 — Block B).
 * Values are loaded from application.yml under the prefix "epcis.digitallink".
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.digitallink")
public class DigitalLinkConfig {

    /**
     * Base URL used to construct Digital Link and resolver URLs.
     * Default: https://id.canda.com (overridable via DIGITAL_LINK_BASE_URL env var).
     */
    private String baseUrl = "https://id.canda.com";

    /**
     * Whether the Digital Link resolver endpoints are active.
     */
    private boolean enabled = true;
}
