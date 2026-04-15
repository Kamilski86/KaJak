package com.canda.epcis.application.cbv;

import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.model.cbv.CbvValidationResult;
import com.canda.epcis.domain.model.cbv.CbvVocabularyType;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyEntity;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyRepository;
import com.canda.epcis.infrastructure.persistence.quarantine.CbvQuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbvValidationServiceTest {

    @Mock CbvVocabularyRepository vocabularyRepository;
    @Mock CbvQuarantineRepository quarantineRepository;

    CbvValidationService service;

    private static final String SHIPPING_URN  = "urn:epcglobal:cbv:bizstep:shipping";
    private static final String SHIPPING_HTTPS = "https://ref.gs1.org/cbv/BizStep-shipping";
    private static final String IN_TRANSIT_URN = "urn:epcglobal:cbv:disp:in_transit";
    private static final String UNKNOWN_BIZ_STEP = "urn:epcglobal:cbv:bizstep:does_not_exist";
    private static final String CUSTOM_URI = "https://custom.partner.com/vocab/myStep";

    @BeforeEach
    void setUp() {
        service = new CbvValidationService(vocabularyRepository, quarantineRepository);
    }

    // ─────────────────────────────────────────────
    // validateSingleUri — bizStep
    // ─────────────────────────────────────────────

    @Test
    void validateSingleUri_knownBizStepUrn_returnsEmpty() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "shipping"))
                .thenReturn(Optional.of(entity("BIZ_STEP", "shipping", false)));

        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(SHIPPING_URN, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isEmpty();
    }

    @Test
    void validateSingleUri_knownBizStepHttps_returnsEmpty() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "shipping"))
                .thenReturn(Optional.of(entity("BIZ_STEP", "shipping", false)));

        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(SHIPPING_HTTPS, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isEmpty();
    }

    @Test
    void validateSingleUri_unknownBizStep_returnsViolation() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "does_not_exist"))
                .thenReturn(Optional.empty());

        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(UNKNOWN_BIZ_STEP, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isPresent();
        assertThat(result.get().getErrorCode()).isEqualTo("CBV_UNKNOWN_BIZ_STEP");
        assertThat(result.get().getField()).isEqualTo("bizStep");
        assertThat(result.get().getValue()).isEqualTo(UNKNOWN_BIZ_STEP);
    }

    @Test
    void validateSingleUri_customUri_returnsEmpty() {
        // Custom/partner URIs that do not start with a CBV prefix are always valid
        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(CUSTOM_URI, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isEmpty();
    }

    @Test
    void validateSingleUri_nullUri_returnsEmpty() {
        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(null, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isEmpty();
    }

    @Test
    void validateSingleUri_deprecatedValue_returnsEmpty() {
        // Deprecated values are accepted (WARN logged) — no violation raised
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "shipping"))
                .thenReturn(Optional.of(entity("BIZ_STEP", "shipping", true)));

        Optional<CbvValidationResult.CbvViolation> result =
                service.validateSingleUri(SHIPPING_URN, CbvVocabularyType.BIZ_STEP, "bizStep");

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────
    // validate — full event
    // ─────────────────────────────────────────────

    @Test
    void validate_validEvent_returnsValidResult() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "shipping"))
                .thenReturn(Optional.of(entity("BIZ_STEP", "shipping", false)));
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.DISPOSITION.name(), "in_transit"))
                .thenReturn(Optional.of(entity("DISPOSITION", "in_transit", false)));

        EpcisEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .bizStep(SHIPPING_URN)
                .disposition(IN_TRANSIT_URN)
                .build();

        CbvValidationResult result = service.validate(event);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    void validate_eventWithNullBizStep_returnsValid() {
        EpcisEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .bizStep(null)
                .build();

        CbvValidationResult result = service.validate(event);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_eventWithMultipleViolations_collectsAll() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "bad_step"))
                .thenReturn(Optional.empty());
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.DISPOSITION.name(), "bad_disp"))
                .thenReturn(Optional.empty());

        EpcisEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .bizStep("urn:epcglobal:cbv:bizstep:bad_step")
                .disposition("urn:epcglobal:cbv:disp:bad_disp")
                .build();

        CbvValidationResult result = service.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(2);
        assertThat(result.getViolations())
                .extracting(CbvValidationResult.CbvViolation::getErrorCode)
                .containsExactlyInAnyOrder("CBV_UNKNOWN_BIZ_STEP", "CBV_UNKNOWN_DISPOSITION");
    }

    // ─────────────────────────────────────────────
    // isKnownCbvValue
    // ─────────────────────────────────────────────

    @Test
    void isKnownCbvValue_knownUrn_returnsTrue() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                CbvVocabularyType.BIZ_STEP.name(), "shipping"))
                .thenReturn(Optional.of(entity("BIZ_STEP", "shipping", false)));

        assertThat(service.isKnownCbvValue(SHIPPING_URN, CbvVocabularyType.BIZ_STEP)).isTrue();
    }

    @Test
    void isKnownCbvValue_unknownUri_returnsFalse() {
        when(vocabularyRepository.findByVocabularyTypeAndPayload(
                eq(CbvVocabularyType.BIZ_STEP.name()), eq("ghost")))
                .thenReturn(Optional.empty());

        assertThat(service.isKnownCbvValue(
                "urn:epcglobal:cbv:bizstep:ghost", CbvVocabularyType.BIZ_STEP))
                .isFalse();
    }

    // ─────────────────────────────────────────────
    // quarantine
    // ─────────────────────────────────────────────

    @Test
    void quarantine_savesEntityWithCorrectFields() {
        EpcisEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .eventId("test-event-id")
                .bizStep("urn:epcglobal:cbv:bizstep:bad_step")
                .build();

        CbvValidationResult result = CbvValidationResult.invalid(List.of(
                CbvValidationResult.CbvViolation.builder()
                        .field("bizStep")
                        .value("urn:epcglobal:cbv:bizstep:bad_step")
                        .errorCode("CBV_UNKNOWN_BIZ_STEP")
                        .message("Unknown bizStep")
                        .build()
        ));

        service.quarantine(event, "<raw/>", "SOURCE-001", result);

        verify(quarantineRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                "CBV_UNKNOWN_BIZ_STEP".equals(entity.getReasonCode())
                && "test-event-id".equals(entity.getEventId())
                && "SOURCE-001".equals(entity.getSourceId())
                && !entity.isResolved()
        ));
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private CbvVocabularyEntity entity(String type, String payload, boolean deprecated) {
        return CbvVocabularyEntity.builder()
                .vocabularyType(type)
                .payload(payload)
                .urnUri("urn:epcglobal:cbv:" + type.toLowerCase() + ":" + payload)
                .httpsUri("https://ref.gs1.org/cbv/" + type + "-" + payload)
                .deprecated(deprecated)
                .build();
    }
}
