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
 * JPA-Entity für die sscc_content Tabelle.
 * Speichert die aktuelle Zuordnung von SGTINs zu SSCCs (Paletten-Inhalt).
 * Unique-Constraint: (sscc_urn, child_epc) — eine SGTIN kann nicht zweimal
 * derselben SSCC zugeordnet sein.
 */
@Entity
@Table(name = "sscc_content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsccContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sscc_urn", nullable = false, length = 255)
    private String ssccUrn;

    @Column(name = "child_epc", nullable = false, length = 255)
    private String childEpc;

    @Column(name = "biz_location", length = 255)
    private String bizLocation;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;
}
