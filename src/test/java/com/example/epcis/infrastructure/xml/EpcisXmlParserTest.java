package com.example.epcis.infrastructure.xml;

import com.example.epcis.domain.model.Action;
import com.example.epcis.domain.model.AggregationEvent;
import com.example.epcis.domain.model.EpcisEvent;
import com.example.epcis.domain.model.ObjectEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpcisXmlParserTest {

    private EpcisXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new EpcisXmlParser();
    }

    @Test
    void parseObjectEvent_returnsCorrectFields() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
              <EPCISBody>
                <EventList>
                  <ObjectEvent>
                    <eventTime>2024-01-15T10:30:00+02:00</eventTime>
                    <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                    <epcList>
                      <epc>urn:epc:id:sgtin:0614141.107346.2017</epc>
                      <epc>urn:epc:id:sgtin:0614141.107346.2018</epc>
                    </epcList>
                    <action>OBSERVE</action>
                    <bizStep>urn:epcglobal:cbv:bizstep:shipping</bizStep>
                    <disposition>urn:epcglobal:cbv:disp:in_transit</disposition>
                    <readPoint><id>urn:epc:id:sgln:0614141.07346.1234</id></readPoint>
                    <bizLocation><id>urn:epc:id:sgln:0614141.07346.0000</id></bizLocation>
                  </ObjectEvent>
                </EventList>
              </EPCISBody>
            </epcis:EPCISDocument>
            """;

        List<EpcisEvent> events = parser.parse(xml);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ObjectEvent.class);

        ObjectEvent event = (ObjectEvent) events.get(0);
        assertThat(event.getEventTime()).isEqualTo(OffsetDateTime.parse("2024-01-15T10:30:00+02:00"));
        assertThat(event.getEventTimeZoneOffset()).isEqualTo("+02:00");
        assertThat(event.getAction()).isEqualTo(Action.OBSERVE);
        assertThat(event.getBizStep()).isEqualTo("urn:epcglobal:cbv:bizstep:shipping");
        assertThat(event.getDisposition()).isEqualTo("urn:epcglobal:cbv:disp:in_transit");
        assertThat(event.getReadPoint()).isEqualTo("urn:epc:id:sgln:0614141.07346.1234");
        assertThat(event.getBizLocation()).isEqualTo("urn:epc:id:sgln:0614141.07346.0000");
        assertThat(event.getEpcList()).containsExactly(
                "urn:epc:id:sgtin:0614141.107346.2017",
                "urn:epc:id:sgtin:0614141.107346.2018");
    }

    @Test
    void parseAggregationEvent_returnsCorrectFields() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
              <EPCISBody>
                <EventList>
                  <AggregationEvent>
                    <eventTime>2024-01-15T12:00:00+01:00</eventTime>
                    <eventTimeZoneOffset>+01:00</eventTimeZoneOffset>
                    <parentID>urn:epc:id:sscc:0614141.1234567890</parentID>
                    <childEPCs>
                      <epc>urn:epc:id:sgtin:0614141.107346.2017</epc>
                    </childEPCs>
                    <action>ADD</action>
                  </AggregationEvent>
                </EventList>
              </EPCISBody>
            </epcis:EPCISDocument>
            """;

        List<EpcisEvent> events = parser.parse(xml);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AggregationEvent.class);

        AggregationEvent event = (AggregationEvent) events.get(0);
        assertThat(event.getAction()).isEqualTo(Action.ADD);
        assertThat(event.getParentId()).isEqualTo("urn:epc:id:sscc:0614141.1234567890");
        assertThat(event.getChildEpcs()).containsExactly("urn:epc:id:sgtin:0614141.107346.2017");
    }

    @Test
    void parseMixedEventList_returnsBothTypes() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
              <EPCISBody>
                <EventList>
                  <ObjectEvent>
                    <eventTime>2024-01-15T10:00:00+00:00</eventTime>
                    <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                    <epcList><epc>urn:epc:id:sgtin:0614141.107346.1</epc></epcList>
                    <action>ADD</action>
                  </ObjectEvent>
                  <AggregationEvent>
                    <eventTime>2024-01-15T11:00:00+00:00</eventTime>
                    <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                    <parentID>urn:epc:id:sscc:0614141.0000000001</parentID>
                    <childEPCs><epc>urn:epc:id:sgtin:0614141.107346.1</epc></childEPCs>
                    <action>ADD</action>
                  </AggregationEvent>
                </EventList>
              </EPCISBody>
            </epcis:EPCISDocument>
            """;

        List<EpcisEvent> events = parser.parse(xml);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ObjectEvent.class);
        assertThat(events.get(1)).isInstanceOf(AggregationEvent.class);
    }

    @Test
    void parse_emptyEventList_returnsEmptyList() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
              <EPCISBody>
                <EventList/>
              </EPCISBody>
            </epcis:EPCISDocument>
            """;

        assertThat(parser.parse(xml)).isEmpty();
    }

    @Test
    void parse_malformedXml_throwsEpcisParsingException() {
        assertThatThrownBy(() -> parser.parse("<not valid xml<<"))
                .isInstanceOf(EpcisParsingException.class);
    }

    @Test
    void parse_invalidAction_throwsEpcisValidationException() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T00:00:00Z">
              <EPCISBody>
                <EventList>
                  <ObjectEvent>
                    <eventTime>2024-01-15T10:00:00+00:00</eventTime>
                    <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                    <epcList><epc>urn:epc:id:sgtin:0614141.107346.1</epc></epcList>
                    <action>INVALID_VALUE</action>
                  </ObjectEvent>
                </EventList>
              </EPCISBody>
            </epcis:EPCISDocument>
            """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(EpcisValidationException.class)
                .hasMessageContaining("INVALID_VALUE");
    }
}
