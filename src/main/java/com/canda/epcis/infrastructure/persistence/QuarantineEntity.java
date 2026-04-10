package com.canda.epcis.infrastructure.persistence;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "epcis_quarantine")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reason why this event was quarantined. */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Machine-readable error code, e.g. UNSUPPORTED_EVENT_TYPE, CBV_VIOLATION. */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** The raw XML fragment or JSON payload that could not be processed. */
    @Column(name = "raw_fragment", nullable = false, columnDefinition = "text")
    private String rawFragment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
