package com.canda.epcis.domain.model.cbv;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Result of CBV 2.0 vocabulary validation for a single EPCIS event.
 * Immutable Value Object — collects all violations without early exit.
 *
 * A result with no violations is valid. A result with one or more violations
 * is invalid and the calling service decides whether to quarantine or only log.
 */
@Getter
@Builder
public class CbvValidationResult {

    private final boolean valid;

    @Singular
    private final List<CbvViolation> violations;

    /**
     * Convenience factory for a passing result.
     */
    public static CbvValidationResult valid() {
        return CbvValidationResult.builder().valid(true).build();
    }

    /**
     * Convenience factory for a failing result with collected violations.
     */
    public static CbvValidationResult invalid(List<CbvViolation> violations) {
        return CbvValidationResult.builder()
                .valid(false)
                .violations(violations)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested Value Object
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single CBV vocabulary violation.
     *
     * errorCode values:
     *   CBV_UNKNOWN_BIZ_STEP             — bizStep URI not in CBV 2.0
     *   CBV_UNKNOWN_DISPOSITION          — disposition URI not in CBV 2.0
     *   CBV_UNKNOWN_BIZ_TRANSACTION_TYPE — bizTransactionType not in CBV 2.0
     *   CBV_UNKNOWN_SOURCE_DEST_TYPE     — sourceDestType not in CBV 2.0
     *   CBV_DEPRECATED_VALUE             — value is deprecated (warn only)
     */
    @Getter
    @Builder
    public static class CbvViolation {

        /** Field name, e.g. "bizStep", "disposition", "sourceList[0].type". */
        private final String field;

        /** The offending URI value, e.g. "urn:epcglobal:cbv:bizstep:typo". */
        private final String value;

        /** Machine-readable error code for downstream processing. */
        private final String errorCode;

        /** Human-readable explanation including the field and the offending value. */
        private final String message;
    }
}
