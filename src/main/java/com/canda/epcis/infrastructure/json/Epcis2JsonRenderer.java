package com.canda.epcis.infrastructure.json;

import com.canda.epcis.application.port.EventRenderer;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.BusinessTransaction;
import com.canda.epcis.domain.model.Destination;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ErrorDeclaration;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.model.QuantityElement;
import com.canda.epcis.domain.model.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class Epcis2JsonRenderer implements EventRenderer {

    private static final Logger log = LoggerFactory.getLogger(Epcis2JsonRenderer.class);
    private static final String EPCIS2_CONTEXT = "https://ref.gs1.org/standards/epcis/epcis-context.jsonld";

    private final ObjectMapper objectMapper;

    public Epcis2JsonRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Renders a single event to JSON for DB/file storage. No @context — belongs on the document envelope. */
    @Override
    public String renderEvent(EpcisEvent event) {
        try {
            String json = objectMapper.writeValueAsString(toDto(event));
            log.debug("Event rendered: type={}", event.getEventTypeName());
            return json;
        } catch (EpcisRenderingException e) {
            throw e;
        } catch (Exception e) {
            throw new EpcisRenderingException("JSON rendering failed: " + e.getMessage(), e);
        }
    }

    /** Wraps events in an EPCIS 2.0 document envelope with @context. */
    @Override
    public EpcisDocumentDto renderDocument(List<EpcisEvent> events) {
        return EpcisDocumentDto.builder()
                .context(EPCIS2_CONTEXT)
                .type("EPCISDocument")
                .schemaVersion("2.0")
                .creationDate(OffsetDateTime.now())
                .epcisBody(EpcisDocumentDto.EpcisBody.builder()
                        .eventList(events.stream().map(this::toDto).toList())
                        .build())
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE: Domain → DTO Mapping
    // ─────────────────────────────────────────────

    private Epcis2EventDto toDto(EpcisEvent event) {
        if (event instanceof ObjectEvent oe)      return mapObjectEvent(oe);
        if (event instanceof AggregationEvent ae) return mapAggregationEvent(ae);
        throw new EpcisRenderingException("Unsupported event type: " + event.getClass().getSimpleName());
    }

    /** Returns a builder pre-populated with the fields shared by all event types. */
    private Epcis2EventDto.Epcis2EventDtoBuilder commonFields(EpcisEvent event) {
        return Epcis2EventDto.builder()
                .eventID(event.getEventId())
                .eventTime(event.getEventTime())
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime())
                .action(event.getAction())
                .bizStep(cbvShortForm(event.getBizStep()))
                .disposition(cbvShortForm(event.getDisposition()))
                .readPoint(mapIdObject(event.getReadPoint()))
                .bizLocation(mapIdObject(event.getBizLocation()))
                .bizTransactionList(mapBizTransactions(event.getBizTransactionList()))
                .sourceList(mapSourceList(event.getSourceList()))
                .destinationList(mapDestinationList(event.getDestinationList()))
                .errorDeclaration(mapErrorDeclaration(event.getErrorDeclaration()))
                .extension(event.getExtension() != null ? event.getExtension().getFields() : null);
    }

    private Epcis2EventDto mapObjectEvent(ObjectEvent event) {
        return commonFields(event)
                .type("ObjectEvent")
                .epcList(event.getEpcList())
                .quantityList(mapQuantityList(event.getQuantityList()))
                .ilmd(event.getIlmd() != null ? event.getIlmd().getFields() : null)
                .build();
    }

    private Epcis2EventDto mapAggregationEvent(AggregationEvent event) {
        return commonFields(event)
                .type("AggregationEvent")
                .parentID(event.getParentId())
                .childEPCs(event.getChildEpcs())
                .childQuantityList(mapQuantityList(event.getChildQuantityList()))
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE: helpers
    // ─────────────────────────────────────────────

    private Epcis2EventDto.IdObject mapIdObject(String id) {
        if (id == null) return null;
        return Epcis2EventDto.IdObject.builder().id(id).build();
    }

    private List<Epcis2EventDto.QuantityElementDto> mapQuantityList(List<QuantityElement> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(q -> Epcis2EventDto.QuantityElementDto.builder()
                        .epcClass(q.getEpcClass()).quantity(q.getQuantity()).uom(q.getUom())
                        .build())
                .toList();
    }

    private List<Epcis2EventDto.BizTransactionDto> mapBizTransactions(List<BusinessTransaction> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(bt -> Epcis2EventDto.BizTransactionDto.builder()
                        .type(cbvShortForm(bt.getType())).bizTransaction(bt.getValue())
                        .build())
                .toList();
    }

    private List<Epcis2EventDto.SourceDto> mapSourceList(List<Source> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(s -> Epcis2EventDto.SourceDto.builder()
                        .type(cbvShortForm(s.getType())).source(s.getValue())
                        .build())
                .toList();
    }

    private List<Epcis2EventDto.DestinationDto> mapDestinationList(List<Destination> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(d -> Epcis2EventDto.DestinationDto.builder()
                        .type(cbvShortForm(d.getType())).destination(d.getValue())
                        .build())
                .toList();
    }

    private Epcis2EventDto.ErrorDeclarationDto mapErrorDeclaration(ErrorDeclaration ed) {
        if (ed == null) return null;
        return Epcis2EventDto.ErrorDeclarationDto.builder()
                .declaringTime(ed.getDeclaringTime())
                .reason(cbvShortForm(ed.getReason()))
                .correctingEventIDs(ed.getCorrectingEventIds())
                .build();
    }

    /**
     * Converts a GS1 CBV 1.2 full URN to the EPCIS 2.0 short-form vocabulary value.
     *
     * Mapping rule: urn:epcglobal:cbv:<type>:<value> → <value>
     * Non-GS1 URIs (user-defined extensions) are passed through unchanged.
     * Null is returned as-is.
     */
    private String cbvShortForm(String uri) {
        if (uri == null) return null;
        if (!uri.startsWith("urn:epcglobal:cbv:")) return uri;
        int lastColon = uri.lastIndexOf(':');
        return uri.substring(lastColon + 1);
    }
}
