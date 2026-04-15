# /fixture — Neues EPCIS Test-Fixture erstellen

Erstelle ein neues EPCIS 1.2 XML Test-Fixture und den dazugehörigen Test-Case.

## Was du vom Nutzer brauchst

Wenn noch nicht angegeben, frage nach:
1. **Event-Typ**: ObjectEvent oder AggregationEvent?
2. **Action**: ADD, OBSERVE oder DELETE?
3. **Zweck**: Was soll dieses Fixture testen? (Happy Path, Negativ-Case, spezifisches Feld, C&A Business-Szenario)
4. **Fixture-Name**: Wie soll die Datei heißen? (z.B. `object-event-add-with-quantity.xml`)

## Schritt 1 — Existierende Fixtures analysieren

Lies ein passendes bestehendes Fixture als Vorlage:
- Für ObjectEvent ADD/OBSERVE/DELETE: lies `src/test/resources/fixtures/object-event-add-with-ilmd.xml`
- Für AggregationEvent: lies `src/test/resources/fixtures/aggregation-event-add.xml`
- Für DM-Event (C&A Business-Szenario): lies das passendste Fixture aus `src/test/resources/fixtures/dm-events/`

## Schritt 2 — XML Fixture erstellen

Erstelle das Fixture in `src/test/resources/fixtures/<name>.xml`.

**Pflichtfelder für jedes Event:**
- `eventTime` mit gültigem ISO-8601 Timestamp + Timezone
- `eventTimeZoneOffset`
- `action`
- Mindestens ein EPC in `epcList` (Format: `urn:epc:id:sgtin:...`) oder `parentID`/`childEPCs` für Aggregation
- `bizStep` aus GS1 CBV 2.0 (z.B. `urn:epcglobal:cbv:bizstep:shipping`)
- `disposition` aus GS1 CBV 2.0 (z.B. `urn:epcglobal:cbv:disp:in_transit`)
- `readPoint` und `bizLocation` mit GS1 GLN URN

**EPCIS 1.2 Envelope:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<epcis:EPCISDocument
    xmlns:epcis="urn:epcglobal:epcis:xsd:1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    schemaVersion="1.2"
    creationDate="2026-04-15T10:00:00.000Z">
  <EPCISBody>
    <EventList>
      <!-- event hier -->
    </EventList>
  </EPCISBody>
</epcis:EPCISDocument>
```

## Schritt 3 — Test-Case ergänzen

Prüfe ob `ConversionFixtureTest.java` oder `EpcisXmlParserTest.java` der richtige Ort ist.

Lies die Datei und füge einen Test-Method hinzu:

```java
@Test
void <beschreibenderName>() throws Exception {
    // Arrange
    String xml = readFixture("<name>.xml");

    // Act
    List<EpcisEvent> events = parser.parse(xml);

    // Assert
    assertThat(events).hasSize(1);
    // ... spezifische Assertions für dieses Fixture
}
```

Für Negativ-Cases:
```java
@Test
void <beschreibenderName>_shouldFail() {
    assertThatThrownBy(() -> parser.parse(xml))
        .isInstanceOf(EpcisParsingException.class)
        .hasMessageContaining("...");
}
```

## Schritt 4 — Test laufen lassen

```
./mvnw test -pl . -Dtest=EpcisXmlParserTest,ConversionFixtureTest -q --no-transfer-progress
```

Bestätige dass der neue Test grün ist.
