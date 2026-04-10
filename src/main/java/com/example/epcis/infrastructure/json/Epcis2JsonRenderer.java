package infrastructure.json;

import com.example.epcis.model.AggregationEvent;
import com.example.epcis.model.BusinessTransaction;
import com.example.epcis.model.EpcisEvent;
import com.example.epcis.model.ObjectEvent;
import com.example.epcis.model.QuantityElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rendert Domain-Objekte in EPCIS 2.0 JSON.
 *
 * Mapping-Regeln:
 * - ObjectEvent    → type: "ObjectEvent",     epcList + quantityList
 * - AggregationEvent → type: "AggregationEvent", parentID + childEPCs + childQuantityList
 * - readPoint / bizLocation werden als { "id": "..." } Objekte ausgegeben (EPCIS 2.0 Standard)
 * - bizTransactionList: type-Attribut wird zu eigenem Feld
 * - @context wird auf den offiziellen GS1 EPCIS 2.0 Context gesetzt
 */
@Component
public class Epcis2JsonRenderer {

    private static final Logger log = LoggerFactory.getLogger(Epcis2JsonRenderer.class);

    private static final String EPCIS2_CONTEXT = "https://ref.gs1.org/standards/epcis/epcis-context.jsonld";

    private final ObjectMapper objectMapper;

    public Epcis2JsonRenderer() {
        this.objectMapper = new ObjectMapper();
        // Lesbare Ausgabe mit Einrückung
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Konvertiert ein Domain-Event in einen EPCIS 2.0 JSON-String.
     *
     * @param event Domain-Event (ObjectEvent oder AggregationEvent)
     * @return EPCIS 2.0 JSON als String
     * @throws EpcisRenderingException wenn der Event-Typ nicht unterstützt wird
     */
    public String render(EpcisEvent event) {
        try {
            Epcis2EventDto dto = toDto(event);
            String json = objectMapper.writeValueAsString(dto);
            log.debug("Event gerendert: type={}, eventID={}", dto.getType(), dto.getEventID());
            return json;
        } catch (EpcisRenderingException e) {
            throw e;
        } catch (Exception e) {
            throw new EpcisRenderingException("JSON-Rendering fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // PRIVATE: Domain → DTO Mapping
    // ─────────────────────────────────────────────

    private Epcis2EventDto toDto(EpcisEvent event) {
        if (event instanceof ObjectEvent oe) {
            return mapObjectEvent(oe);
        } else if (event instanceof AggregationEvent ae) {
            return mapAggregationEvent(ae);
        } else {
            throw new EpcisRenderingException(
                "Nicht unterstützter Event-Typ: " + event.getClass().getSimpleName());
        }
    }

    private Epcis2EventDto mapObjectEvent(ObjectEvent event) {
        return Epcis2EventDto.builder()
                .context(EPCIS2_CONTEXT)
                .type("ObjectEvent")
                .eventID(event.getEventId())
                .eventTime(formatTime(event))
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime() != null ? event.getRecordTime().toString() : null)
                .action(event.getAction() != null ? event.getAction().name() : null)
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .readPoint(mapIdObject(event.getReadPoint()))
                .bizLocation(mapIdObject(event.getBizLocation()))
                .epcList(event.getEpcList())
                .quantityList(mapQuantityList(event.getQuantityList()))
                .bizTransactionList(mapBizTransactions(event.getBizTransactionList()))
                .build();
    }

    private Epcis2EventDto mapAggregationEvent(AggregationEvent event) {
        return Epcis2EventDto.builder()
                .context(EPCIS2_CONTEXT)
                .type("AggregationEvent")
                .eventID(event.getEventId())
                .eventTime(formatTime(event))
                .eventTimeZoneOffset(event.getEventTimeZoneOffset())
                .recordTime(event.getRecordTime() != null ? event.getRecordTime().toString() : null)
                .action(event.getAction() != null ? event.getAction().name() : null)
                .bizStep(event.getBizStep())
                .disposition(event.getDisposition())
                .readPoint(mapIdObject(event.getReadPoint()))
                .bizLocation(mapIdObject(event.getBizLocation()))
                .parentID(event.getParentId())
                .childEPCs(event.getChildEpcs())
                .childQuantityList(mapQuantityList(event.getChildQuantityList()))
                .bizTransactionList(mapBizTransactions(event.getBizTransactionList()))
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE: Hilfsmethoden
    // ─────────────────────────────────────────────

    private String formatTime(EpcisEvent event) {
        if (event.getEventTime() == null) return null;
        return event.getEventTime().toString();
    }

    private Epcis2EventDto.IdObject mapIdObject(String id) {
        if (id == null) return null;
        return Epcis2EventDto.IdObject.builder().id(id).build();
    }

    private List<Epcis2EventDto.QuantityElementDto> mapQuantityList(List<QuantityElement> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(q -> Epcis2EventDto.QuantityElementDto.builder()
                        .epcClass(q.getEpcClass())
                        .quantity(q.getQuantity())
                        .uom(q.getUom())
                        .build())
                .toList();
    }

    private List<Epcis2EventDto.BizTransactionDto> mapBizTransactions(List<BusinessTransaction> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(bt -> Epcis2EventDto.BizTransactionDto.builder()
                        .type(bt.getType())
                        .bizTransaction(bt.getValue())
                        .build())
                .toList();
    }
}
