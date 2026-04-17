package com.canda.epcis.domain.service;

import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.Destination;
import com.canda.epcis.domain.model.ErrorDeclaration;
import com.canda.epcis.domain.model.IlmdPayload;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.model.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbvVocabularyValidatorTest {

    private CbvVocabularyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CbvVocabularyValidator();
    }

    // ── bizStep ───────────────────────────────────────────────────────────────

    @Test
    void validBizStep_passes() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(minimalObjectEvent()
                        .bizStep("urn:epcglobal:cbv:bizstep:shipping")
                        .build()));
    }

    @Test
    void invalidBizStep_throws() {
        assertThatThrownBy(() ->
                validator.validate(minimalObjectEvent()
                        .bizStep("urn:epcglobal:cbv:bizstep:MADE_UP_VALUE")
                        .build()))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("bizStep");
    }

    @Test
    void nullBizStep_passes() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(minimalObjectEvent().build()));
    }

    @Test
    void userDefinedBizStepExtension_passesWithWarning() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(minimalObjectEvent()
                        .bizStep("http://example.com/bizstep:custom_step")
                        .build()));
    }

    // ── disposition ───────────────────────────────────────────────────────────

    @Test
    void validDisposition_passes() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(minimalObjectEvent()
                        .disposition("urn:epcglobal:cbv:disp:in_transit")
                        .build()));
    }

    @Test
    void invalidDisposition_throws() {
        assertThatThrownBy(() ->
                validator.validate(minimalObjectEvent()
                        .disposition("urn:epcglobal:cbv:disp:INVALID")
                        .build()))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("disposition");
    }

    // ── errorDeclaration.reason ───────────────────────────────────────────────

    @Test
    void validErrorReason_passes() {
        ErrorDeclaration ed = ErrorDeclaration.builder()
                .declaringTime(OffsetDateTime.now())
                .reason("urn:epcglobal:cbv:er:did_not_occur")
                .build();
        assertThatNoException().isThrownBy(() ->
                validator.validate(minimalObjectEvent().errorDeclaration(ed).build()));
    }

    @Test
    void invalidErrorReason_throws() {
        ErrorDeclaration ed = ErrorDeclaration.builder()
                .declaringTime(OffsetDateTime.now())
                .reason("urn:epcglobal:cbv:er:unknown_reason")
                .build();
        assertThatThrownBy(() ->
                validator.validate(minimalObjectEvent().errorDeclaration(ed).build()))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("errorDeclaration reason");
    }

    // ── sourceList / destinationList ──────────────────────────────────────────

    @Test
    void validSourceType_passes() {
        // Use ObjectEvent.builder() directly to avoid @SuperBuilder raw-type inference
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now()).action(Action.OBSERVE)
                .sourceList(List.of(Source.builder()
                        .type("urn:epcglobal:cbv:sdt:owning_party")
                        .value("urn:epc:id:sgln:0614141.00001.0")
                        .build()))
                .build();
        assertThatNoException().isThrownBy(() -> validator.validate(event));
    }

    @Test
    void invalidSourceType_throws() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now()).action(Action.OBSERVE)
                .sourceList(List.of(Source.builder()
                        .type("urn:epcglobal:cbv:sdt:INVALID_TYPE")
                        .value("urn:epc:id:sgln:0614141.00001.0")
                        .build()))
                .build();
        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("source type");
    }

    @Test
    void invalidDestinationType_throws() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now()).action(Action.OBSERVE)
                .destinationList(List.of(Destination.builder()
                        .type("urn:epcglobal:cbv:sdt:INVALID_TYPE")
                        .value("urn:epc:id:sgln:0614141.00002.0")
                        .build()))
                .build();
        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("destination type");
    }

    @Test
    void sdtLocation_isValidDestinationType() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now()).action(Action.OBSERVE)
                .destinationList(List.of(Destination.builder()
                        .type("urn:epcglobal:cbv:sdt:location")
                        .value("urn:epc:id:sgln:4056019.00334.BACKROOM")
                        .build()))
                .build();
        assertThatNoException().isThrownBy(() -> validator.validate(event));
    }

    @Test
    void sdtLocation_isValidSourceType() {
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now()).action(Action.OBSERVE)
                .sourceList(List.of(Source.builder()
                        .type("urn:epcglobal:cbv:sdt:location")
                        .value("urn:epc:id:sgln:4056019.00334.BACKROOM")
                        .build()))
                .build();
        assertThatNoException().isThrownBy(() -> validator.validate(event));
    }

    // ── ILMD on DELETE ────────────────────────────────────────────────────────

    @Test
    void ilmdOnDeleteAction_throws() {
        IlmdPayload ilmd = IlmdPayload.builder().fields(Map.of("lotNumber", "ABC123")).build();
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.DELETE)
                .ilmd(ilmd)
                .build();
        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(CbvValidationException.class)
                .hasMessageContaining("ILMD");
    }

    @Test
    void ilmdOnAddAction_passes() {
        IlmdPayload ilmd = IlmdPayload.builder().fields(Map.of("lotNumber", "ABC123")).build();
        ObjectEvent event = ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.ADD)
                .ilmd(ilmd)
                .build();
        assertThatNoException().isThrownBy(() -> validator.validate(event));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ObjectEvent.ObjectEventBuilder minimalObjectEvent() {
        return ObjectEvent.builder()
                .eventTime(OffsetDateTime.now())
                .action(Action.OBSERVE);
    }
}
