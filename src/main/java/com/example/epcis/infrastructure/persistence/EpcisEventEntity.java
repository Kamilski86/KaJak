package com.example.epcis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * JPA Entity – repräsentiert ein konvertiertes EPCIS 2.0 Event in der Datenbank.
 *
 * Tabelle: epcis_events
 */
@Entity
@Table(name = "epcis_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpcisEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ObjectEvent oder AggregationEvent */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Zeitstempel des Events aus dem EPCIS-Dokument */
    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    /** Vollständiges EPCIS 2.0 JSON */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Zeitstempel wann der Datensatz gespeichert wurde */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
