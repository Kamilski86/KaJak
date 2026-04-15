package com.canda.epcis.infrastructure.persistence.cbv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persistence entity for a single GS1 CBV 2.0 vocabulary entry.
 *
 * Each row represents one valid payload value for one vocabulary type,
 * in both its URN and HTTPS URI forms.
 *
 * Populated once at application startup by {@link com.canda.epcis.application.cbv.CbvVocabularyLoader}.
 * The table is never modified at runtime.
 */
@Entity
@Table(
    name = "cbv_vocabulary",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_cbv_vocabulary_type_payload",
        columnNames = {"vocabulary_type", "payload"}
    )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbvVocabularyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One of: BIZ_STEP, DISPOSITION, BIZ_TRANSACTION_TYPE, SOURCE_DEST_TYPE.
     * Stored as the enum name string.
     */
    @Column(name = "vocabulary_type", nullable = false, length = 50)
    private String vocabularyType;

    /**
     * Short payload value, e.g. "shipping", "in_transit".
     * This is the part after the URI prefix.
     */
    @Column(name = "payload", nullable = false, length = 100)
    private String payload;

    /**
     * Full URN form, e.g. "urn:epcglobal:cbv:bizstep:shipping".
     */
    @Column(name = "urn_uri", nullable = false, length = 255)
    private String urnUri;

    /**
     * Full HTTPS form, e.g. "https://ref.gs1.org/cbv/BizStep-shipping".
     */
    @Column(name = "https_uri", nullable = false, length = 255)
    private String httpsUri;

    /**
     * True if this value is deprecated in CBV 2.0.
     * Deprecated values are accepted but logged as WARN.
     */
    @Column(name = "deprecated", nullable = false)
    @Builder.Default
    private boolean deprecated = false;
}
