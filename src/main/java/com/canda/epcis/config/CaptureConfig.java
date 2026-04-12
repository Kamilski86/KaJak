package com.canda.epcis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration für den EPCIS Capture Service.
 * Werte werden aus application.yml unter dem Prefix "epcis.capture" geladen.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.capture")
public class CaptureConfig {

    /** Maximale Anzahl Events pro XML-Dokument. */
    private int maxEventsPerRequest = 1000;

    /** Maximale XML-Größe in KB pro Request (default: 10 MB). */
    private int maxXmlSizeKb = 10240;

    /** Capture Audit in capture_audit Tabelle schreiben. */
    private boolean auditEnabled = true;

    /** Events zusätzlich als JSON-Dateien schreiben (JsonFileWriter). */
    private boolean fileWriterEnabled = true;
}
