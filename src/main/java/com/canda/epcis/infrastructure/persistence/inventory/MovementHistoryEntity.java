package com.canda.epcis.infrastructure.persistence.inventory;

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

import java.time.OffsetDateTime;

/**
 * JPA-Entity für die movement_history Tabelle.
 * Append-only — vollständige Bewegungshistorie pro EPC.
 * Unique-Constraint (epc_urn, event_id) verhindert doppelte Einträge (Idempotenz).
 */
@Entity
@Table(
    name = "movement_history",
    indexes = {
        @Index(name = "idx_movement_history_epc_time",
               columnList = "epc_urn, event_time DESC"),
        @Index(name = "idx_movement_history_biz_location",
               columnList = "biz_location")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "epc_urn", nullable = false, length = 255)
    private String epcUrn;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "biz_step", length = 255)
    private String bizStep;

    @Column(name = "disposition", length = 255)
    private String disposition;

    @Column(name = "biz_location", length = 255)
    private String bizLocation;

    @Column(name = "read_point", length = 255)
    private String readPoint;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}
