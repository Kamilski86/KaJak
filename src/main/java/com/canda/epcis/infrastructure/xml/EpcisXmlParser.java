package com.canda.epcis.infrastructure.xml;

import com.canda.epcis.application.port.EventParser;
import com.canda.epcis.domain.model.Action;
import com.canda.epcis.domain.model.AggregationEvent;
import com.canda.epcis.domain.model.BusinessTransaction;
import com.canda.epcis.domain.model.Destination;
import com.canda.epcis.domain.model.EpcisEvent;
import com.canda.epcis.domain.model.ErrorDeclaration;
import com.canda.epcis.domain.model.ExtensionPayload;
import com.canda.epcis.domain.model.IlmdPayload;
import com.canda.epcis.domain.model.ObjectEvent;
import com.canda.epcis.domain.model.QuantityElement;
import com.canda.epcis.domain.model.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EpcisXmlParser implements EventParser {

    private static final Logger log = LoggerFactory.getLogger(EpcisXmlParser.class);

    // DocumentBuilderFactory is thread-safe for creating builders; cache it.
    // DocumentBuilder itself is NOT thread-safe — a new one is created per parse call.
    private final DocumentBuilderFactory documentBuilderFactory;

    public EpcisXmlParser() {
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Failed to configure XML parser factory", e);
        }
    }

    @Override
    public List<EpcisEvent> parse(String xml) {
        try {
            Document doc = buildDocument(xml);
            List<EpcisEvent> events = new ArrayList<>();

            NodeList eventLists = doc.getElementsByTagNameNS("*", "EventList");
            if (eventLists.getLength() == 0) {
                log.warn("No EventList found in document");
                return events;
            }

            Element eventList = (Element) eventLists.item(0);
            NodeList children = eventList.getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;
                switch (el.getLocalName()) {
                    case "ObjectEvent"      -> events.add(parseObjectEvent(el));
                    case "AggregationEvent" -> events.add(parseAggregationEvent(el));
                    default -> throw new EpcisParsingException(
                            "Unsupported event type encountered: '" + el.getLocalName() +
                            "'. Route this document to quarantine or handle the type explicitly.");
                }
            }

            log.info("XML parsed: {} event(s) found", events.size());
            return events;

        } catch (EpcisValidationException | EpcisParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new EpcisParsingException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // PRIVATE: event-specific parsers
    // ─────────────────────────────────────────────

    private ObjectEvent parseObjectEvent(Element el) {
        log.debug("Parsing ObjectEvent");
        CommonEventFields c = parseCommonFields(el);
        return ObjectEvent.builder()
                .eventId(c.eventId()).eventTime(c.eventTime())
                .eventTimeZoneOffset(c.eventTimeZoneOffset()).recordTime(c.recordTime())
                .bizStep(c.bizStep()).disposition(c.disposition())
                .readPoint(c.readPoint()).bizLocation(c.bizLocation())
                .bizTransactionList(c.bizTransactionList())
                .sourceList(c.sourceList()).destinationList(c.destinationList())
                .errorDeclaration(c.errorDeclaration()).extension(c.extension())
                .action(c.action())
                .epcList(parseEpcList(el, "epcList", "epc"))
                .quantityList(parseQuantityList(el, "quantityList"))
                .ilmd(parseIlmd(el))
                .build();
    }

    private AggregationEvent parseAggregationEvent(Element el) {
        log.debug("Parsing AggregationEvent");
        CommonEventFields c = parseCommonFields(el);
        return AggregationEvent.builder()
                .eventId(c.eventId()).eventTime(c.eventTime())
                .eventTimeZoneOffset(c.eventTimeZoneOffset()).recordTime(c.recordTime())
                .bizStep(c.bizStep()).disposition(c.disposition())
                .readPoint(c.readPoint()).bizLocation(c.bizLocation())
                .bizTransactionList(c.bizTransactionList())
                .sourceList(c.sourceList()).destinationList(c.destinationList())
                .errorDeclaration(c.errorDeclaration()).extension(c.extension())
                .action(c.action())
                .parentId(getDirectChildText(el, "parentID"))
                .childEpcs(parseEpcList(el, "childEPCs", "epc"))
                .childQuantityList(parseQuantityList(el, "childQuantityList"))
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE: common field extraction
    // ─────────────────────────────────────────────

    private record CommonEventFields(
            String eventId, OffsetDateTime eventTime, String eventTimeZoneOffset,
            OffsetDateTime recordTime, String bizStep, String disposition,
            String readPoint, String bizLocation,
            List<BusinessTransaction> bizTransactionList,
            List<Source> sourceList, List<Destination> destinationList,
            ErrorDeclaration errorDeclaration, ExtensionPayload extension,
            Action action) {}

    private CommonEventFields parseCommonFields(Element el) {
        return new CommonEventFields(
                getDirectChildText(el, "eventID"),
                parseTime(getDirectChildText(el, "eventTime"), "eventTime"),
                getDirectChildText(el, "eventTimeZoneOffset"),
                parseOptionalTime(getDirectChildText(el, "recordTime")),
                getDirectChildText(el, "bizStep"),
                getDirectChildText(el, "disposition"),
                getNestedText(el, "readPoint", "id"),
                getNestedText(el, "bizLocation", "id"),
                parseBizTransactions(el),
                parseSourceList(el),
                parseDestinationList(el),
                parseErrorDeclaration(el),
                parseExtension(el),
                parseAction(getDirectChildText(el, "action")));
    }

    // ─────────────────────────────────────────────
    // PRIVATE: DOM helpers
    // ─────────────────────────────────────────────

    private Document buildDocument(String xml) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Returns text of the first direct child element matching localName. */
    private String getDirectChildText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return trimmedText(child.getTextContent());
            }
        }
        return null;
    }

    /** Deep descendant search — for list containers and nested structures. */
    private String getText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() == 0 ? null : trimmedText(nodes.item(0).getTextContent());
    }

    private String getNestedText(Element parent, String outer, String inner) {
        NodeList outerNodes = parent.getElementsByTagNameNS("*", outer);
        if (outerNodes.getLength() == 0) return null;
        NodeList innerNodes = ((Element) outerNodes.item(0)).getElementsByTagNameNS("*", inner);
        return innerNodes.getLength() == 0 ? null : trimmedText(innerNodes.item(0).getTextContent());
    }

    /** Returns the first direct child Element matching localName, or null. */
    private Element getDirectChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String trimmedText(String text) {
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    // ─────────────────────────────────────────────
    // PRIVATE: value parsers
    // ─────────────────────────────────────────────

    private OffsetDateTime parseTime(String value, String fieldName) {
        if (value == null) {
            throw new EpcisValidationException("Mandatory field missing: " + fieldName);
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            throw new EpcisValidationException(
                    "Invalid timestamp for field '" + fieldName + "': " + value, e);
        }
    }

    /** Optional timestamp field — null is allowed, invalid format is not. */
    private OffsetDateTime parseOptionalTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            throw new EpcisValidationException(
                    "Invalid timestamp for optional field 'recordTime': " + value, e);
        }
    }

    private Action parseAction(String value) {
        if (value == null) return null;
        try {
            return Action.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EpcisValidationException("Invalid action value: " + value, e);
        }
    }

    // ─────────────────────────────────────────────
    // PRIVATE: list parsers
    // ─────────────────────────────────────────────

    private List<String> parseEpcList(Element parent, String listTag, String itemTag) {
        List<String> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", listTag);
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", itemTag);
        for (int i = 0; i < items.getLength(); i++) {
            String epc = trimmedText(items.item(i).getTextContent());
            if (epc != null) result.add(epc);
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
            String value = trimmedText(item.getTextContent());
            if (value != null) {
                result.add(BusinessTransaction.builder()
                        .type(type.isBlank() ? null : type.trim())
                        .value(value)
                        .build());
            }
        }
        return result;
    }

    private List<Source> parseSourceList(Element parent) {
        List<Source> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", "sourceList");
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", "source");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String type = item.getAttribute("type");
            String value = trimmedText(item.getTextContent());
            if (value != null) {
                result.add(Source.builder()
                        .type(type.isBlank() ? null : type.trim())
                        .value(value)
                        .build());
            }
        }
        return result;
    }

    private List<Destination> parseDestinationList(Element parent) {
        List<Destination> result = new ArrayList<>();
        NodeList listNodes = parent.getElementsByTagNameNS("*", "destinationList");
        if (listNodes.getLength() == 0) return result;
        NodeList items = ((Element) listNodes.item(0)).getElementsByTagNameNS("*", "destination");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String type = item.getAttribute("type");
            String value = trimmedText(item.getTextContent());
            if (value != null) {
                result.add(Destination.builder()
                        .type(type.isBlank() ? null : type.trim())
                        .value(value)
                        .build());
            }
        }
        return result;
    }

    private ErrorDeclaration parseErrorDeclaration(Element parent) {
        Element edEl = getDirectChildElement(parent, "errorDeclaration");
        if (edEl == null) return null;

        String declaringTime = getText(edEl, "declaringTime");
        String reason = getText(edEl, "reason");

        List<String> correctingIds = new ArrayList<>();
        NodeList idNodes = edEl.getElementsByTagNameNS("*", "correctingEventID");
        for (int i = 0; i < idNodes.getLength(); i++) {
            String id = trimmedText(idNodes.item(i).getTextContent());
            if (id != null) correctingIds.add(id);
        }

        return ErrorDeclaration.builder()
                .declaringTime(declaringTime != null ? parseOptionalTime(declaringTime) : null)
                .reason(reason)
                .correctingEventIds(correctingIds)
                .build();
    }

    private IlmdPayload parseIlmd(Element parent) {
        Element ilmdEl = getDirectChildElement(parent, "ilmd");
        if (ilmdEl == null) return null;
        Map<String, String> fields = parseExtensionElements(ilmdEl);
        return fields.isEmpty() ? null : IlmdPayload.builder().fields(fields).build();
    }

    private ExtensionPayload parseExtension(Element parent) {
        Element extEl = getDirectChildElement(parent, "extension");
        if (extEl == null) return null;
        Map<String, String> fields = parseExtensionElements(extEl);
        return fields.isEmpty() ? null : ExtensionPayload.builder().fields(fields).build();
    }

    /**
     * Extracts direct child elements of a container (ilmd, extension) as a flat
     * localName → textContent map. Only leaf-level text elements are captured.
     * Nested structures are represented by their immediate text content only.
     */
    private Map<String, String> parseExtensionElements(Element container) {
        Map<String, String> fields = new LinkedHashMap<>();
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String localName = child.getLocalName();
            String text = trimmedText(child.getTextContent());
            if (localName != null && text != null) {
                fields.put(localName, text);
            }
        }
        return fields;
    }
}
