package com.canda.epcis.application.capture;

import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.EpcFormat;
import com.canda.epcis.domain.model.FilterResult;
import com.canda.epcis.domain.model.ObjectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtert ungültige EPCs aus EPCIS Events.
 *
 * Implementiert REQ101.1 und REQ216.
 *
 * Logging-Pflicht (REQ216 + REQ603) — für jeden gefilterten EPC wird geloggt:
 *   WARN "EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}"
 *
 * Prüfreihenfolge:
 * 1. isForbidden? → FORBIDDEN_GID / FORBIDDEN_USDOD / FORBIDDEN_ADI / FORBIDDEN_BIC
 * 2. isAllowedInEpcList? → UNSUPPORTED_FORMAT
 */
@Service
public class EpcFilterService {

    private static final Logger log = LoggerFactory.getLogger(EpcFilterService.class);

    /**
     * Filtert ungültige EPCs aus einem ObjectEvent.
     * Nach Filterung: wenn acceptedEpcs leer → eventShouldBeDropped = true.
     */
    public FilterResult filterObjectEvent(ObjectEvent event) {
        List<String> epcs = event.getEpcList() != null ? event.getEpcList() : List.of();
        FilterOutcome outcome = filterEpcList(epcs, event.getEventId(), "ObjectEvent");

        boolean drop = !epcs.isEmpty() && outcome.accepted().isEmpty();
        return FilterResult.builder()
                .acceptedEpcs(outcome.accepted())
                .filteredEpcs(outcome.filtered())
                .eventShouldBeDropped(drop)
                .dropReason(drop ? "All EPCs were filtered out — no accepted EPCs remain" : null)
                .build();
    }

    /**
     * Filtert ungültige EPCs aus einem AggregationEvent.
     * Zusätzlich: parentID muss SSCC sein — sonst eventShouldBeDropped = true (REQ216 Punkt 2).
     */
    public FilterResult filterAggregationEvent(AggregationEvent event) {
        String parentId = event.getParentId();
        List<FilterResult.FilteredEpc> filteredEpcs = new ArrayList<>();

        // REQ216 Punkt 2: parentID muss SSCC sein
        if (parentId != null && !parentId.isBlank()) {
            if (!EpcFormat.isValidParentId(parentId)) {
                log.warn("EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}",
                        parentId, event.getEventId(), "AggregationEvent", "INVALID_PARENT_ID_NOT_SSCC", 1);
                filteredEpcs.add(FilterResult.FilteredEpc.builder()
                        .epc(parentId)
                        .reason("INVALID_PARENT_ID_NOT_SSCC")
                        .build());
                return FilterResult.builder()
                        .acceptedEpcs(List.of())
                        .filteredEpcs(filteredEpcs)
                        .eventShouldBeDropped(true)
                        .dropReason("parentID is not a valid SSCC: " + parentId)
                        .build();
            }
            // GS1 TDS 2.0 §6.3.2: SSCC SerialReference muss rein numerisch sein, 17 Stellen gesamt
            if (!EpcFormat.isValidSsccStructure(parentId)) {
                log.warn("EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}",
                        parentId, event.getEventId(), "AggregationEvent", "INVALID_SSCC_STRUCTURE", 1);
                filteredEpcs.add(FilterResult.FilteredEpc.builder()
                        .epc(parentId)
                        .reason("INVALID_SSCC_STRUCTURE")
                        .build());
                return FilterResult.builder()
                        .acceptedEpcs(List.of())
                        .filteredEpcs(filteredEpcs)
                        .eventShouldBeDropped(true)
                        .dropReason("parentID has invalid SSCC structure (non-numeric or wrong length): " + parentId)
                        .build();
            }
        }

        List<String> childEpcs = event.getChildEpcs() != null ? event.getChildEpcs() : List.of();
        FilterOutcome outcome = filterEpcList(childEpcs, event.getEventId(), "AggregationEvent");
        filteredEpcs.addAll(outcome.filtered());

        boolean drop = outcome.accepted().isEmpty() && !childEpcs.isEmpty();
        return FilterResult.builder()
                .acceptedEpcs(outcome.accepted())
                .filteredEpcs(filteredEpcs)
                .eventShouldBeDropped(drop)
                .dropReason(drop ? "All child EPCs were filtered out — no accepted EPCs remain" : null)
                .build();
    }

    /**
     * Returns a new ObjectEvent with the filtered EPC list applied. Never mutates the original.
     */
    public ObjectEvent applyFilter(ObjectEvent event, List<String> acceptedEpcs) {
        return ObjectEvent.builder()
                .eventId(event.getEventId())
                .eventTime(event.getEventTime())
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime())
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .readPoint(event.getReadPoint())
                .bizLocation(event.getBizLocation())
                .bizTransactionList(event.getBizTransactionList())
                .sourceList(event.getSourceList())
                .destinationList(event.getDestinationList())
                .errorDeclaration(event.getErrorDeclaration())
                .extension(event.getExtension())
                .action(event.getAction())
                .epcList(acceptedEpcs)
                .quantityList(event.getQuantityList())
                .ilmd(event.getIlmd())
                .build();
    }

    /**
     * Returns a new AggregationEvent with the filtered child EPC list applied. Never mutates the original.
     */
    public AggregationEvent applyFilter(AggregationEvent event, List<String> acceptedEpcs) {
        return AggregationEvent.builder()
                .eventId(event.getEventId())
                .eventTime(event.getEventTime())
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime())
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .readPoint(event.getReadPoint())
                .bizLocation(event.getBizLocation())
                .bizTransactionList(event.getBizTransactionList())
                .sourceList(event.getSourceList())
                .destinationList(event.getDestinationList())
                .errorDeclaration(event.getErrorDeclaration())
                .extension(event.getExtension())
                .action(event.getAction())
                .parentId(event.getParentId())
                .childEpcs(acceptedEpcs)
                .childQuantityList(event.getChildQuantityList())
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────

    private FilterOutcome filterEpcList(List<String> epcs, String eventId, String eventType) {
        List<String> accepted = new ArrayList<>();
        List<FilterResult.FilteredEpc> filtered = new ArrayList<>();

        for (String epc : epcs) {
            if (EpcFormat.isForbidden(epc)) {
                String reason = forbiddenReason(epc);
                log.warn("EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}",
                        epc, eventId, eventType, reason, 1);
                filtered.add(FilterResult.FilteredEpc.builder().epc(epc).reason(reason).build());
            } else if (!EpcFormat.isAllowedInEpcList(epc)) {
                log.warn("EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}",
                        epc, eventId, eventType, "UNSUPPORTED_FORMAT", 1);
                filtered.add(FilterResult.FilteredEpc.builder().epc(epc).reason("UNSUPPORTED_FORMAT").build());
            } else if (epc.startsWith("urn:epc:id:sscc:") && !EpcFormat.isValidSsccStructure(epc)) {
                // GS1 TDS 2.0 §6.3.2: SSCC SerialReference muss rein numerisch sein, 17 Stellen gesamt
                log.warn("EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}",
                        epc, eventId, eventType, "INVALID_SSCC_STRUCTURE", 1);
                filtered.add(FilterResult.FilteredEpc.builder().epc(epc).reason("INVALID_SSCC_STRUCTURE").build());
            } else {
                accepted.add(epc);
            }
        }

        return new FilterOutcome(accepted, filtered);
    }

    private String forbiddenReason(String epc) {
        if (epc.startsWith("urn:epc:id:gid:"))   return "FORBIDDEN_GID";
        if (epc.startsWith("urn:epc:id:usdod:")) return "FORBIDDEN_USDOD";
        if (epc.startsWith("urn:epc:id:adi:"))   return "FORBIDDEN_ADI";
        if (epc.startsWith("urn:epc:id:bic:"))   return "FORBIDDEN_BIC";
        return "FORBIDDEN";
    }

    private record FilterOutcome(List<String> accepted, List<FilterResult.FilteredEpc> filtered) {}
}
