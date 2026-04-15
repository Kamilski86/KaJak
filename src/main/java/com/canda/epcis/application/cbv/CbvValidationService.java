package com.canda.epcis.application.cbv;

import com.canda.epcis.domain.model.BusinessTransaction;
import com.canda.epcis.domain.model.Destination;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.Source;
import com.canda.epcis.domain.model.cbv.CbvValidationResult;
import com.canda.epcis.domain.model.cbv.CbvValidationResult.CbvViolation;
import com.canda.epcis.domain.model.cbv.CbvVocabularyType;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyEntity;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyRepository;
import com.canda.epcis.infrastructure.persistence.quarantine.CbvQuarantineEntity;
import com.canda.epcis.infrastructure.persistence.quarantine.CbvQuarantineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates EPCIS events against the GS1 CBV 2.0 vocabulary stored in the database.
 *
 * Validation rules:
 *   1. bizStep     — if set and starts with a CBV prefix, must be a known CBV 2.0 bizStep URI
 *   2. disposition — if set and starts with a CBV prefix, must be a known CBV 2.0 disposition URI
 *   3. bizTransactionList[*].type — each type that starts with a CBV prefix must be known
 *   4. sourceList[*].type / destinationList[*].type — same rule for sdt: URIs
 *
 * Non-CBV URIs (custom / partner vocabulary):
 *   URIs that do NOT start with "urn:epcglobal:cbv:" or "https://ref.gs1.org/cbv/"
 *   are treated as valid user-defined extensions and pass without a violation.
 *
 * Deprecated values:
 *   Accepted but logged as WARN. No violation is raised.
 *
 * All violations are collected before returning — no early exit.
 */
@Service
public class CbvValidationService {

    private static final Logger log = LoggerFactory.getLogger(CbvValidationService.class);

    private final CbvVocabularyRepository vocabularyRepository;
    private final CbvQuarantineRepository quarantineRepository;

    public CbvValidationService(CbvVocabularyRepository vocabularyRepository,
                                CbvQuarantineRepository quarantineRepository) {
        this.vocabularyRepository = vocabularyRepository;
        this.quarantineRepository = quarantineRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates all CBV vocabulary fields of an event.
     * Returns a result with all violations found; never throws.
     *
     * @param event the domain event to validate
     * @return CbvValidationResult — valid=true if no violations, false otherwise
     */
    public CbvValidationResult validate(EpcisEvent event) {
        List<CbvViolation> violations = new ArrayList<>();

        validateSingleUri(event.getBizStep(), CbvVocabularyType.BIZ_STEP, "bizStep")
                .ifPresent(violations::add);

        validateSingleUri(event.getDisposition(), CbvVocabularyType.DISPOSITION, "disposition")
                .ifPresent(violations::add);

        if (event.getBizTransactionList() != null) {
            int idx = 0;
            for (BusinessTransaction bt : event.getBizTransactionList()) {
                validateSingleUri(bt.getType(), CbvVocabularyType.BIZ_TRANSACTION_TYPE,
                        "bizTransactionList[" + idx + "].type")
                        .ifPresent(violations::add);
                idx++;
            }
        }

        if (event.getSourceList() != null) {
            int idx = 0;
            for (Source s : event.getSourceList()) {
                validateSingleUri(s.getType(), CbvVocabularyType.SOURCE_DEST_TYPE,
                        "sourceList[" + idx + "].type")
                        .ifPresent(violations::add);
                idx++;
            }
        }

        if (event.getDestinationList() != null) {
            int idx = 0;
            for (Destination d : event.getDestinationList()) {
                validateSingleUri(d.getType(), CbvVocabularyType.SOURCE_DEST_TYPE,
                        "destinationList[" + idx + "].type")
                        .ifPresent(violations::add);
                idx++;
            }
        }

        if (violations.isEmpty()) {
            return CbvValidationResult.valid();
        }
        return CbvValidationResult.invalid(violations);
    }

    /**
     * Validates a single URI value against a specific CBV vocabulary type.
     * Returns empty if the URI is valid or not subject to CBV validation.
     * Returns a violation if the URI starts with a CBV prefix but is unknown.
     *
     * @param uri       the URI to validate (may be null)
     * @param type      the CBV vocabulary type to check against
     * @param fieldName the field name for the violation message
     */
    public Optional<CbvViolation> validateSingleUri(String uri, CbvVocabularyType type,
                                                    String fieldName) {
        if (uri == null || uri.isBlank()) return Optional.empty();

        // Non-CBV URIs are allowed (custom/partner vocabulary)
        if (!type.isCbvUri(uri)) return Optional.empty();

        Optional<String> payloadOpt = type.extractPayload(uri);
        if (payloadOpt.isEmpty()) return Optional.empty();

        String payload = payloadOpt.get();
        Optional<CbvVocabularyEntity> entry =
                vocabularyRepository.findByVocabularyTypeAndPayload(type.name(), payload);

        if (entry.isEmpty()) {
            String errorCode = errorCodeFor(type);
            return Optional.of(CbvViolation.builder()
                    .field(fieldName)
                    .value(uri)
                    .errorCode(errorCode)
                    .message("Unknown CBV 2.0 value for " + fieldName + ": '" + uri + "'. " +
                             "Not found in vocabulary type " + type.name() + ".")
                    .build());
        }

        if (entry.get().isDeprecated()) {
            log.warn("CBV_DEPRECATED_VALUE field={} uri={} type={}", fieldName, uri, type.name());
        }

        return Optional.empty();
    }

    /**
     * Returns true if the given URI is a known CBV value for the specified type.
     * Accepts both URN and HTTPS forms.
     */
    public boolean isKnownCbvValue(String uri, CbvVocabularyType type) {
        if (uri == null || uri.isBlank()) return false;
        if (!type.isCbvUri(uri)) return false;
        return type.extractPayload(uri)
                .map(payload -> vocabularyRepository
                        .findByVocabularyTypeAndPayload(type.name(), payload)
                        .isPresent())
                .orElse(false);
    }

    /**
     * Persists a CBV quarantine record.
     * Called by ConvertEventUseCase when strict-mode=true and a violation is found.
     *
     * @param event      the violating event
     * @param rawXml     original XML payload for reprocessing
     * @param sourceId   X-EPCIS-Source-ID header value (may be null)
     * @param result     the validation result with all violations
     */
    public void quarantine(EpcisEvent event, String rawXml, String sourceId,
                           CbvValidationResult result) {
        String reasonCode = result.getViolations().stream()
                .map(CbvViolation::getErrorCode)
                .findFirst()
                .orElse("CBV_VIOLATION");

        String reasonDetail = result.getViolations().stream()
                .map(v -> v.getField() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        CbvQuarantineEntity entity = CbvQuarantineEntity.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventTypeName())
                .sourceId(sourceId)
                .reasonCode(reasonCode)
                .reasonDetail(reasonDetail)
                .rawPayload(rawXml)
                .build();

        quarantineRepository.save(entity);
        log.warn("CBV quarantine saved: eventId={} reasonCode={} violations={}",
                event.getEventId(), reasonCode, result.getViolations().size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String errorCodeFor(CbvVocabularyType type) {
        return switch (type) {
            case BIZ_STEP            -> "CBV_UNKNOWN_BIZ_STEP";
            case DISPOSITION         -> "CBV_UNKNOWN_DISPOSITION";
            case BIZ_TRANSACTION_TYPE -> "CBV_UNKNOWN_BIZ_TRANSACTION_TYPE";
            case SOURCE_DEST_TYPE    -> "CBV_UNKNOWN_SOURCE_DEST_TYPE";
        };
    }
}
