package com.canda.epcis.infrastructure.persistence.quarantine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Persistence entity for a CBV-quarantined EPCIS event.
 *
 * An event lands here when CBV validation fails and strict-mode is enabled.
 * In lenient mode (strict-mode=false) the event is only logged, not quarantined.
 *
 * Distinct from {@link com.canda.epcis.infrastructure.persistence.QuarantineEntity}
 * (table: epcis_quarantine) which handles structural/parsing failures.
 * This table (quarantine_event) handles semantic CBV violations specifically.
 */
@Entity
@Table(
    name = "quarantine_event",
    indexes = {
        @Index(name = "idx_quarantine_resolved",    columnList = "resolved"),
        @Index(name = "idx_quarantine_reason_code", columnList = "reason_code"),
        @Index(name = "idx_quarantine_quarantined",  columnList = "quarantined_at")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbvQuarantineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** EPCIS eventID from the event payload, if present. */
    @Column(name = "event_id", length = 255)
    private String eventId;

    /** Event type, e.g. "ObjectEvent", "AggregationEvent". */
    @Column(name = "event_type", length = 50)
    private String eventType;

    /** Value of the X-EPCIS-Source-ID request header, if present. */
    @Column(name = "source_id", length = 100)
    private String sourceId;

    /** Machine-readable primary reason code, e.g. "CBV_UNKNOWN_BIZ_STEP". */
    @Column(name = "reason_code", nullable = false, length = 100)
    private String reasonCode;

    /** Full detail: which fields violated which rules. */
    @Column(name = "reason_detail", columnDefinition = "text")
    private String reasonDetail;

    /** Original raw XML payload for manual review and reprocessing. */
    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "quarantined_at", nullable = false, updatable = false)
    private OffsetDateTime quarantinedAt;

    /** False until an operator manually resolves this quarantine item. */
    @Setter
    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    /** Set when an operator marks this item as resolved. */
    @Setter
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
