package com.canda.epcis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Declares that an event is a correction of previously reported erroneous data.
 * Maps to EPCIS 1.2 errorDeclaration element and EPCIS 2.0 errorDeclaration object.
 *
 * Business invariant: declaringTime must not be null; reason should be a valid CBV
 * error reason URI (urn:epcglobal:cbv:er:*). Validated by CbvVocabularyValidator.
 */
@Getter
@Builder
public class ErrorDeclaration {
    private final OffsetDateTime declaringTime;
    private final String reason;
    private final List<String> correctingEventIds;
}
