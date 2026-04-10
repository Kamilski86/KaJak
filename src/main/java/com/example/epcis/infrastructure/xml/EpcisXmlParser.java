package infrastructure.xml;

import com.example.epcis.model.Action;
import com.example.epcis.model.AggregationEvent;
import com.example.epcis.model.BusinessTransaction;
import com.example.epcis.model.EpcisEvent;
import com.example.epcis.model.ObjectEvent;
import com.example.epcis.model.QuantityElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class EpcisXmlParser {

    private static final Logger log = LoggerFactory.getLogger(EpcisXmlParser.class);

    public List<EpcisEvent> parse(String xml) {
        try {
            Document doc = buildDocument(xml);
            List<EpcisEvent> events = new ArrayList<>();

            NodeList objectEvents = doc.getElementsByTagNameNS("*", "ObjectEvent");
            for (int i = 0; i < objectEvents.getLength(); i++) {
                events.add(parseObjectEvent((Element) objectEvents.item(i)));
            }

            NodeList aggregationEvents = doc.getElementsByTagNameNS("*", "AggregationEvent");
            for (int i = 0; i < aggregationEvents.getLength(); i++) {
                events.add(parseAggregationEvent((Element) aggregationEvents.item(i)));
            }

            log.info("XML geparst: {} Event(s) gefunden", events.size());
            return events;

        } catch (EpcisValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new EpcisValidationException("XML konnte nicht geparst werden: " + e.getMessage(), e);
        }
    }

    private ObjectEvent parseObjectEvent(Element el) {
        log.debug("Parsing ObjectEvent");
        return ObjectEvent.builder()
                .eventId(getText(el, "eventID"))
                .eventTime(parseTime(getText(el, "eventTime")))
                .eventTimeZoneOffset(getText(el, "eventTimeZoneOffset"))
                .recordTime(parseTime(getText(el, "recordTime")))
                .bizStep(getText(el, "bizStep"))
                .disposition(getText(el, "disposition"))
                .readPoint(getNestedText(el, "readPoint", "id"))
                .bizLocation(getNestedText(el, "bizLocation", "id"))
                .bizTransactionList(parseBizTransactions(el))
                .action(parseAction(getText(el, "action")))
                .epcList(parseEpcList(el, "epcList", "epc"))
                .quantityList(parseQuantityList(el, "quantityList"))
                .build();
    }

    private AggregationEvent parseAggregationEvent(Element el) {
        log.debug("Parsing AggregationEvent");
        return AggregationEvent.builder()
                .eventId(getText(el, "eventID"))
                .eventTime(parseTime(getText(el, "eventTime")))
                .eventTimeZoneOffset(getText(el, "eventTimeZoneOffset"))
                .recordTime(parseTime(getText(el, "recordTime")))
                .bizStep(getText(el, "bizStep"))
                .disposition(getText(el, "disposition"))
                .readPoint(getNestedText(el, "readPoint", "id"))
                .bizLocation(getNestedText(el, "bizLocation", "id"))
                .bizTransactionList(parseBizTransactions(el))
                .action(parseAction(getText(el, "action")))
                .parentId(getText(el, "parentID"))
                .childEpcs(parseEpcList(el, "childEPCs", "epc"))
                .childQuantityList(parseQuantityList(el, "childQuantityList"))
                .build();
    }

    private Document buildDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String getText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    private String getNestedText(Element parent, String outer, String inner) {
        NodeList outerNodes = parent.getElementsByTagNameNS("*", outer);
        if (outerNodes.getLength() == 0) return null;
        NodeList innerNodes = ((Element) outerNodes.item(0)).getElementsByTagNameNS("*", inner);
        if (innerNodes.getLength() == 0) return null;
        String text = innerNodes.item(0).getTextContent();
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            log.warn("Zeitstempel konnte nicht geparst werden: {}", value);
            return null;
        }
    }

    private Action parseAction(String value) {
        if (value == null) return null;
        try {
            return Action.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EpcisValidationException("Ungültiger Action-Wert: " + value, e);
        }
    }

    private List<String> parseEpcList(Element parent, String listTag, String itemTag) {
        List<String> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", listTag);
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", itemTag);
        for (int i = 0; i < items.getLength(); i++) {
            String epc = items.item(i).getTextContent();
            if (epc != null && !epc.isBlank()) result.add(epc.trim());
        }
        return result;
    }

    private List<QuantityElement> parseQuantityList(Element parent, String listTag) {
        List<QuantityElement> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", listTag);
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", "quantityElement");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String epcClass = getText(item, "epcClass");
            String quantityStr = getText(item, "quantity");
            String uom = getText(item, "uom");
            if (epcClass != null) {
                result.add(QuantityElement.builder()
                        .epcClass(epcClass)
                        .quantity(quantityStr != null ? Double.parseDouble(quantityStr) : null)
                        .uom(uom)
                        .build());
            }
        }
        return result;
    }

    private List<BusinessTransaction> parseBizTransactions(Element parent) {
        List<BusinessTransaction> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", "bizTransactionList");
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", "bizTransaction");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String type = item.getAttribute("type");
            String value = item.getTextContent();
            if (value != null && !value.isBlank()) {
                result.add(BusinessTransaction.builder()
                        .type(type.isBlank() ? null : type.trim())
                        .value(value.trim())
                        .build());
            }
        }
        return result;
    }
}
