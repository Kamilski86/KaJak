package com.canda.epcis.infrastructure.persistence.inventory;

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
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA-Entity für die epc_state Tabelle.
 * Speichert den letzten bekannten Zustand jeder SGTIN (REQ401).
 */
@Entity
@Table(name = "epc_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpcStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "epc_urn", nullable = false, unique = true, length = 255)
    private String epcUrn;

    @Column(name = "current_status", length = 100)
    private String currentStatus;

    @Column(name = "biz_location", length = 255)
    private String bizLocation;

    @Column(name = "last_event_id", length = 255)
    private String lastEventId;

    @Column(name = "last_event_time")
    private OffsetDateTime lastEventTime;

    @Column(name = "sscc", length = 255)
    private String sscc;

    @Column(name = "create_date", nullable = false)
    private OffsetDateTime createDate;

    @Column(name = "update_date", nullable = false)
    private OffsetDateTime updateDate;

    // Disposition-spezifische Timestamps (REQ401)
    @Column(name = "encoded_at")
    private OffsetDateTime encodedAt;

    @Column(name = "in_progress_at")
    private OffsetDateTime inProgressAt;

    @Column(name = "in_transit_at")
    private OffsetDateTime inTransitAt;

    @Column(name = "accessible_for_customer_at")
    private OffsetDateTime accessibleForCustomerAt;

    @Column(name = "available_not_accessible_at")
    private OffsetDateTime availableNotAccessibleAt;

    @Column(name = "retired_at")
    private OffsetDateTime retiredAt;

    @Column(name = "sold_at")
    private OffsetDateTime soldAt;
}
