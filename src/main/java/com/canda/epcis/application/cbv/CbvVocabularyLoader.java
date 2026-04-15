package com.canda.epcis.application.cbv;

import com.canda.epcis.domain.model.cbv.CbvVocabularyType;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyEntity;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the complete GS1 CBV 2.0 vocabulary into the database on application startup.
 * Idempotent: if the table already contains entries, the load is skipped entirely.
 *
 * Source: GS1 Core Business Vocabulary Standard, Release 2.0, Jun 2022, GS1 AISBL.
 *
 * Sections:
 *   7.1.3 — Business Step (bizStep)
 *   7.2.3 — Disposition
 *   7.3.3 — Business Transaction Type (bizTransactionType)
 *   7.4.3 — Source/Destination Type (sourceDestType)
 */
@Component
public class CbvVocabularyLoader {

    private static final Logger log = LoggerFactory.getLogger(CbvVocabularyLoader.class);

    private final CbvVocabularyRepository repository;

    public CbvVocabularyLoader(CbvVocabularyRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void loadIfEmpty() {
        if (repository.count() > 0) {
            log.debug("CBV vocabulary already loaded — skipping.");
            return;
        }

        List<CbvVocabularyEntity> entries = new ArrayList<>();
        entries.addAll(buildBizSteps());
        entries.addAll(buildDispositions());
        entries.addAll(buildBizTransactionTypes());
        entries.addAll(buildSourceDestTypes());

        repository.saveAll(entries);
        log.info("CBV 2.0 vocabulary loaded: {} entries total", repository.count());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBV 2.0 Section 7.1.3 — Business Step (40 values)
    // ─────────────────────────────────────────────────────────────────────────

    private List<CbvVocabularyEntity> buildBizSteps() {
        String[] payloads = {
            "accepting",
            "arriving",
            "assembling",
            "collecting",
            "commissioning",
            "consigning",
            "creating_class_instance",
            "cycle_counting",
            "decommissioning",
            "departing",
            "destroying",
            "dispensing",
            "encoding",
            "entering_exiting",
            "holding",
            "inspecting",
            "installing",
            "killing",
            "loading",
            "other",
            "packing",
            "picking",
            "receiving",
            "removing",
            "repackaging",
            "repairing",
            "replacing",
            "reserving",
            "retail_selling",
            "sampling",
            "sensor_reporting",
            "shipping",
            "staging_outbound",
            "stock_taking",
            "stocking",
            "storing",
            "transporting",
            "unloading",
            "unpacking",
            "void_shipping"
        };
        return buildEntries(CbvVocabularyType.BIZ_STEP, payloads);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBV 2.0 Section 7.2.3 — Disposition (31 values)
    // ─────────────────────────────────────────────────────────────────────────

    private List<CbvVocabularyEntity> buildDispositions() {
        String[] payloads = {
            "active",
            "available",
            "completeness_inferred",
            "completeness_verified",
            "conformant",
            "container_closed",
            "container_open",
            "damaged",
            "destroyed",
            "dispensed",
            "disposed",
            "encoded",
            "expired",
            "in_progress",
            "in_transit",
            "inactive",
            "mismatch_class",
            "mismatch_instance",
            "mismatch_quantity",
            "needs_replacement",
            "non_sellable_other",
            "partially_dispensed",
            "recalled",
            "reserved",
            "retail_sold",
            "returned",
            "sellable_accessible",
            "sellable_not_accessible",
            "stolen",
            "unavailable",
            "unknown"
        };
        return buildEntries(CbvVocabularyType.DISPOSITION, payloads);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBV 2.0 Section 7.3.3 — Business Transaction Type (12 values)
    // ─────────────────────────────────────────────────────────────────────────

    private List<CbvVocabularyEntity> buildBizTransactionTypes() {
        String[] payloads = {
            "bol",
            "desadv",
            "inv",
            "pedigree",
            "po",
            "poc",
            "prodorder",
            "recadv",
            "rma",
            "testprd",
            "testres",
            "upevt"
        };
        return buildEntries(CbvVocabularyType.BIZ_TRANSACTION_TYPE, payloads);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBV 2.0 Section 7.4.3 — Source/Destination Type (3 values)
    // ─────────────────────────────────────────────────────────────────────────

    private List<CbvVocabularyEntity> buildSourceDestTypes() {
        String[] payloads = {
            "owning_party",
            "possessing_party",
            "location"
        };
        return buildEntries(CbvVocabularyType.SOURCE_DEST_TYPE, payloads);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared builder
    // ─────────────────────────────────────────────────────────────────────────

    private List<CbvVocabularyEntity> buildEntries(CbvVocabularyType type, String[] payloads) {
        List<CbvVocabularyEntity> result = new ArrayList<>(payloads.length);
        for (String payload : payloads) {
            result.add(CbvVocabularyEntity.builder()
                    .vocabularyType(type.name())
                    .payload(payload)
                    .urnUri(type.getUrnPrefix() + payload)
                    .httpsUri(type.getHttpsPrefix() + payload)
                    .deprecated(false)
                    .build());
        }
        return result;
    }
}
