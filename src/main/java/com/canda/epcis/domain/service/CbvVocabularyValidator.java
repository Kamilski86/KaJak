package com.canda.epcis.domain.service;

import com.canda.epcis.domain.model.Destination;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ErrorDeclaration;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.model.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Stage 5 validation: checks that vocabulary fields contain only officially
 * permitted GS1 CBV URIs (EPCIS 2.0 / CBV 2.0).
 *
 * Null/absent values are not validated here — mandatory-field presence is
 * enforced earlier in Stage 2 (parser) and Stage 3 (canonical domain).
 *
 * Reference: GS1 Core Business Vocabulary Standard, Release 2.0
 */
@Component
public class CbvVocabularyValidator {

    private static final Logger log = LoggerFactory.getLogger(CbvVocabularyValidator.class);

    // ─────────────────────────────────────────────
    // CBV Allowlists (GS1 CBV 2.0)
    // ─────────────────────────────────────────────

    private static final Set<String> ALLOWED_BIZ_STEPS = Set.of(
            "urn:epcglobal:cbv:bizstep:accepting",
            "urn:epcglobal:cbv:bizstep:arriving",
            "urn:epcglobal:cbv:bizstep:assembling",
            "urn:epcglobal:cbv:bizstep:collecting",
            "urn:epcglobal:cbv:bizstep:commissioning",
            "urn:epcglobal:cbv:bizstep:consigning",
            "urn:epcglobal:cbv:bizstep:creating_class_instance",
            "urn:epcglobal:cbv:bizstep:cycle_counting",
            "urn:epcglobal:cbv:bizstep:decommissioning",
            "urn:epcglobal:cbv:bizstep:departing",
            "urn:epcglobal:cbv:bizstep:destroying",
            "urn:epcglobal:cbv:bizstep:disassembling",
            "urn:epcglobal:cbv:bizstep:dispensing",
            "urn:epcglobal:cbv:bizstep:encoding",
            "urn:epcglobal:cbv:bizstep:entering_exiting",
            "urn:epcglobal:cbv:bizstep:holding",
            "urn:epcglobal:cbv:bizstep:informing",
            "urn:epcglobal:cbv:bizstep:inspecting",
            "urn:epcglobal:cbv:bizstep:installing",
            "urn:epcglobal:cbv:bizstep:killing",
            "urn:epcglobal:cbv:bizstep:loading",
            "urn:epcglobal:cbv:bizstep:maintaining",
            "urn:epcglobal:cbv:bizstep:manufacturing",
            "urn:epcglobal:cbv:bizstep:mating",
            "urn:epcglobal:cbv:bizstep:other",
            "urn:epcglobal:cbv:bizstep:packing",
            "urn:epcglobal:cbv:bizstep:picking",
            "urn:epcglobal:cbv:bizstep:receiving",
            "urn:epcglobal:cbv:bizstep:removing",
            "urn:epcglobal:cbv:bizstep:repackaging",
            "urn:epcglobal:cbv:bizstep:repairing",
            "urn:epcglobal:cbv:bizstep:replacing",
            "urn:epcglobal:cbv:bizstep:reserving",
            "urn:epcglobal:cbv:bizstep:retail_selling",
            "urn:epcglobal:cbv:bizstep:sampling",
            "urn:epcglobal:cbv:bizstep:sensor_reporting",
            "urn:epcglobal:cbv:bizstep:shipping",
            "urn:epcglobal:cbv:bizstep:staging_outbound",
            "urn:epcglobal:cbv:bizstep:stock_taking",
            "urn:epcglobal:cbv:bizstep:storing",
            "urn:epcglobal:cbv:bizstep:transporting",
            "urn:epcglobal:cbv:bizstep:unloading",
            "urn:epcglobal:cbv:bizstep:unpacking",
            "urn:epcglobal:cbv:bizstep:void_shipping",
            "urn:epcglobal:cbv:bizstep:wo_assignment"
    );

    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of(
            "urn:epcglobal:cbv:disp:active",
            "urn:epcglobal:cbv:disp:available",
            "urn:epcglobal:cbv:disp:completeness_inferred",
            "urn:epcglobal:cbv:disp:completeness_verified",
            "urn:epcglobal:cbv:disp:conformant",
            "urn:epcglobal:cbv:disp:damaged",
            "urn:epcglobal:cbv:disp:destroyed",
            "urn:epcglobal:cbv:disp:dispensed",
            "urn:epcglobal:cbv:disp:disposed",
            "urn:epcglobal:cbv:disp:encoded",
            "urn:epcglobal:cbv:disp:expired",
            "urn:epcglobal:cbv:disp:in_progress",
            "urn:epcglobal:cbv:disp:in_transit",
            "urn:epcglobal:cbv:disp:inactive",
            "urn:epcglobal:cbv:disp:mated",
            "urn:epcglobal:cbv:disp:needs_replacement",
            "urn:epcglobal:cbv:disp:non_conformant",
            "urn:epcglobal:cbv:disp:partially_dispensed",
            "urn:epcglobal:cbv:disp:recalled",
            "urn:epcglobal:cbv:disp:reserved",
            "urn:epcglobal:cbv:disp:retail_sold",
            "urn:epcglobal:cbv:disp:returned",
            "urn:epcglobal:cbv:disp:sellable_accessible",
            "urn:epcglobal:cbv:disp:sellable_not_accessible",
            "urn:epcglobal:cbv:disp:stolen",
            "urn:epcglobal:cbv:disp:unavailable",
            "urn:epcglobal:cbv:disp:unconfirmed_lot",
            "urn:epcglobal:cbv:disp:unknown",
            "urn:epcglobal:cbv:disp:unmated"
    );

    private static final Set<String> ALLOWED_ERROR_REASONS = Set.of(
            "urn:epcglobal:cbv:er:did_not_occur",
            "urn:epcglobal:cbv:er:incorrect_data"
    );

    private static final Set<String> ALLOWED_SDT = Set.of(
            "urn:epcglobal:cbv:sdt:owning_party",
            "urn:epcglobal:cbv:sdt:processing_party",
            "urn:epcglobal:cbv:sdt:possessing_party"
    );

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * Validates all CBV vocabulary fields on an event.
     * Throws {@link CbvValidationException} on the first violation found.
     */
    public void validate(EpcisEvent event) {
        validateBizStep(event.getBizStep());
        validateDisposition(event.getDisposition());
        validateErrorDeclaration(event.getErrorDeclaration());
        validateSourceList(event.getSourceList());
        validateDestinationList(event.getDestinationList());
        if (event instanceof ObjectEvent oe) {
            validateIlmdPresence(oe);
        }
        log.debug("CBV validation passed: type={}", event.getEventTypeName());
    }

    // ─────────────────────────────────────────────
    // Private validators
    // ─────────────────────────────────────────────

    private void validateBizStep(String bizStep) {
        if (bizStep == null) return;
        if (!ALLOWED_BIZ_STEPS.contains(bizStep) && !isUserDefinedExtension(bizStep)) {
            throw new CbvValidationException("Invalid bizStep URI: '" + bizStep +
                    "'. Must be a GS1 CBV bizstep or a user-defined extension URI.");
        }
    }

    private void validateDisposition(String disposition) {
        if (disposition == null) return;
        if (!ALLOWED_DISPOSITIONS.contains(disposition) && !isUserDefinedExtension(disposition)) {
            throw new CbvValidationException("Invalid disposition URI: '" + disposition +
                    "'. Must be a GS1 CBV disposition or a user-defined extension URI.");
        }
    }

    private void validateErrorDeclaration(ErrorDeclaration ed) {
        if (ed == null) return;
        if (ed.getReason() != null
                && !ALLOWED_ERROR_REASONS.contains(ed.getReason())
                && !isUserDefinedExtension(ed.getReason())) {
            throw new CbvValidationException("Invalid errorDeclaration reason URI: '" + ed.getReason() +
                    "'. Must be urn:epcglobal:cbv:er:did_not_occur or urn:epcglobal:cbv:er:incorrect_data.");
        }
    }

    private void validateSourceList(List<Source> sources) {
        if (sources == null) return;
        for (Source s : sources) {
            if (s.getType() != null && !ALLOWED_SDT.contains(s.getType()) && !isUserDefinedExtension(s.getType())) {
                throw new CbvValidationException("Invalid source type URI: '" + s.getType() +
                        "'. Must be a GS1 CBV sdt: type.");
            }
        }
    }

    private void validateDestinationList(List<Destination> destinations) {
        if (destinations == null) return;
        for (Destination d : destinations) {
            if (d.getType() != null && !ALLOWED_SDT.contains(d.getType()) && !isUserDefinedExtension(d.getType())) {
                throw new CbvValidationException("Invalid destination type URI: '" + d.getType() +
                        "'. Must be a GS1 CBV sdt: type.");
            }
        }
    }

    private void validateIlmdPresence(ObjectEvent event) {
        // ILMD is only valid on ObjectEvent with action ADD or OBSERVE.
        // DELETE semantics with ILMD attached is a semantic contradiction.
        if (event.getIlmd() != null
                && event.getAction() != null
                && event.getAction().name().equals("DELETE")) {
            throw new CbvValidationException(
                    "ILMD must not be present on ObjectEvent with action DELETE.");
        }
    }

    /**
     * GS1 standards permit user-defined extension URIs that do not start with
     * the GS1 namespace prefix. These pass vocabulary validation with a warning.
     */
    private boolean isUserDefinedExtension(String uri) {
        if (uri == null) return false;
        boolean isExtension = !uri.startsWith("urn:epcglobal:cbv:");
        if (isExtension) {
            log.warn("Non-standard CBV URI accepted as user-defined extension: '{}'", uri);
        }
        return isExtension;
    }
}
