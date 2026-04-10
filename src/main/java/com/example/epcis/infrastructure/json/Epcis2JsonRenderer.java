package com.example.epcis.infrastructure.json;

import com.example.epcis.domain.model.AggregationEvent;
import com.example.epcis.domain.model.BusinessTransaction;
import com.example.epcis.domain.model.EpcisEvent;
import com.example.epcis.domain.model.ObjectEvent;
import com.example.epcis.domain.model.QuantityElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class Epcis2JsonRenderer {

    private static final Logger log = LoggerFactory.getLogger(Epcis2JsonRenderer.class);
    private static final String EPCIS2_CONTEXT = "https://ref.gs1.org/standards/epcis/epcis-context.jsonld";

    private final ObjectMapper objectMapper;

    public Epcis2JsonRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Renders a single event to JSON for DB/file storage. No @context — belongs on the document envelope. */
    public String render(EpcisEvent event) {
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
    public EpcisDocumentDto renderDocument(List<EpcisEvent> events) {
        return EpcisDocumentDto.builder()
                .context(EPCIS2_CONTEXT)
                .type("EPCISDocument")
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
                .eventID(event.getEventId() != null ? event.getEventId() : "urn:uuid:" + UUID.randomUUID())
                .eventTime(event.getEventTime())
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime())
                .action(event.getAction())
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .readPoint(mapIdObject(event.getReadPoint()))
                .bizLocation(mapIdObject(event.getBizLocation()))
                .bizTransactionList(mapBizTransactions(event.getBizTransactionList()));
    }

    private Epcis2EventDto mapObjectEvent(ObjectEvent event) {
        return commonFields(event)
                .type("ObjectEvent")
                .epcList(event.getEpcList())
                .quantityList(mapQuantityList(event.getQuantityList()))
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
                        .type(bt.getType()).bizTransaction(bt.getValue())
                        .build())
                .toList();
    }
}
