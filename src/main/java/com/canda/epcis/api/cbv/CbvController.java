package com.canda.epcis.api.cbv;

import com.canda.epcis.application.cbv.CbvValidationService;
import com.canda.epcis.domain.model.cbv.CbvValidationResult;
import com.canda.epcis.domain.model.cbv.CbvVocabularyType;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyEntity;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyRepository;
import com.canda.epcis.infrastructure.persistence.quarantine.CbvQuarantineEntity;
import com.canda.epcis.infrastructure.persistence.quarantine.CbvQuarantineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for CBV 2.0 vocabulary inspection and quarantine management.
 *
 * GET /cbv/vocabulary/{type}           — list valid values for a vocabulary type
 * GET /cbv/validate                    — validate a single URI
 * GET /cbv/quarantine                  — list quarantined events
 * PUT /cbv/quarantine/{id}/resolve     — mark a quarantine item as resolved
 */
@RestController
@RequestMapping("/cbv")
public class CbvController {

    private static final Logger log = LoggerFactory.getLogger(CbvController.class);

    private final CbvVocabularyRepository vocabularyRepository;
    private final CbvQuarantineRepository quarantineRepository;
    private final CbvValidationService validationService;

    public CbvController(CbvVocabularyRepository vocabularyRepository,
                         CbvQuarantineRepository quarantineRepository,
                         CbvValidationService validationService) {
        this.vocabularyRepository = vocabularyRepository;
        this.quarantineRepository = quarantineRepository;
        this.validationService = validationService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /cbv/vocabulary/{type}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all valid CBV values for the given vocabulary type.
     *
     * @param type       one of: BIZ_STEP, DISPOSITION, BIZ_TRANSACTION_TYPE, SOURCE_DEST_TYPE
     * @param deprecated if false (default), excludes deprecated values
     */
    @GetMapping("/vocabulary/{type}")
    public ResponseEntity<List<VocabularyEntryResponse>> getVocabulary(
            @PathVariable String type,
            @RequestParam(defaultValue = "false") boolean deprecated) {

        CbvVocabularyType vocabularyType = parseVocabularyType(type);

        List<CbvVocabularyEntity> entries = deprecated
                ? vocabularyRepository.findByVocabularyType(vocabularyType.name())
                : vocabularyRepository.findByVocabularyTypeAndDeprecatedFalse(vocabularyType.name());

        List<VocabularyEntryResponse> response = entries.stream()
                .map(e -> new VocabularyEntryResponse(
                        e.getPayload(), e.getUrnUri(), e.getHttpsUri(), e.isDeprecated()))
                .toList();

        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /cbv/validate
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates a single URI against a CBV vocabulary type.
     *
     * @param uri  the URI to validate, e.g. "urn:epcglobal:cbv:bizstep:shipping"
     * @param type the vocabulary type, e.g. "BIZ_STEP"
     */
    @GetMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(
            @RequestParam String uri,
            @RequestParam String type) {

        CbvVocabularyType vocabularyType = parseVocabularyType(type);

        var violation = validationService.validateSingleUri(uri, vocabularyType, "uri");

        if (violation.isEmpty()) {
            return ResponseEntity.ok(new ValidateResponse(true, uri, type, null, null));
        }

        CbvValidationResult.CbvViolation v = violation.get();
        return ResponseEntity.ok(new ValidateResponse(false, uri, type, v.getErrorCode(), v.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /cbv/quarantine
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists quarantined events.
     *
     * @param resolved if false (default), returns only unresolved items
     * @param from     optional — only items quarantined after this timestamp
     */
    @GetMapping("/quarantine")
    public ResponseEntity<List<CbvQuarantineEntity>> getQuarantine(
            @RequestParam(defaultValue = "false") boolean resolved,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from) {

        List<CbvQuarantineEntity> items = (from != null)
                ? quarantineRepository.findByResolvedAndQuarantinedAtAfter(resolved, from)
                : quarantineRepository.findByResolved(resolved);

        return ResponseEntity.ok(items);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /cbv/quarantine/{id}/resolve
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks a quarantine item as manually resolved.
     *
     * @param id the quarantine record id
     */
    @PutMapping("/quarantine/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveQuarantine(@PathVariable Long id) {
        CbvQuarantineEntity entity = quarantineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Quarantine item not found: " + id));

        entity.setResolved(true);
        entity.setResolvedAt(OffsetDateTime.now());
        quarantineRepository.save(entity);

        log.info("CBV quarantine item resolved: id={}", id);
        return ResponseEntity.ok(Map.of("id", id, "resolved", true, "resolvedAt", entity.getResolvedAt()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CbvVocabularyType parseVocabularyType(String type) {
        try {
            return CbvVocabularyType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown vocabulary type: '" + type +
                    "'. Allowed values: BIZ_STEP, DISPOSITION, BIZ_TRANSACTION_TYPE, SOURCE_DEST_TYPE");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response records
    // ─────────────────────────────────────────────────────────────────────────

    public record VocabularyEntryResponse(
            String payload,
            String urnUri,
            String httpsUri,
            boolean deprecated) {}

    public record ValidateResponse(
            boolean valid,
            String uri,
            String type,
            String errorCode,
            String message) {}
}
