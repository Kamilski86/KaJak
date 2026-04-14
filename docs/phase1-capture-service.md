# Phase 1 — EPCIS Capture Service

## Scope

Phase 1 implementiert den Grundbetrieb des neuen EPCIS Repository parallel zu EECC:

1. **Capture Service** — EPCIS 1.2 XML empfangen, validieren, filtern, speichern
2. **EPC Filter** — REQ101.1 + REQ216: ungültige EPC-Formate erkennen und entfernen
3. **Query API** — EPCIS 2.0 REST Binding konforme Abfrage für Downstream-Systeme
4. **Capture Audit** — Reconciliation-Tabelle für Parallelbetrieb
5. **Health Endpoints** — Spring Actuator

**Nicht in Phase 1:** SGTIN State Matrix (REQ201+), Subscription Query (REQ102), Downstream-Integrationen (REQ301+).

---

## Neue Endpunkte

### POST /epcis/capture/events

Nimmt ein EPCIS 1.2 XML-Dokument entgegen, verarbeitet alle Events darin und gibt eine Zusammenfassung zurück.

**Request:**
```http
POST /epcis/capture/events
Content-Type: application/xml
X-EPCIS-Source-ID: STORE-DE-001

<?xml version="1.0" encoding="UTF-8"?>
<epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" ...>
  <EPCISBody>
    <EventList>
      <ObjectEvent>...</ObjectEvent>
    </EventList>
  </EPCISBody>
</epcis:EPCISDocument>
```

**Response 201 Created:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "totalReceived": 3,
  "totalAccepted": 2,
  "totalFiltered": 1,
  "totalDropped": 0,
  "captureIds": [
    "urn:uuid:aaa...",
    "urn:uuid:bbb..."
  ]
}
```

**Response-Felder:**

| Feld | Beschreibung |
|---|---|
| `sessionId` | UUID dieser Capture-Session — für Audit und Tracing |
| `totalReceived` | Anzahl Events im XML-Dokument |
| `totalAccepted` | Erfolgreich gespeicherte Events |
| `totalFiltered` | Events bei denen EPCs gefiltert wurden (Event selbst behalten) |
| `totalDropped` | Komplett abgelehnte Events (z.B. ungültige parentID) |
| `captureIds` | EPCIS eventIDs der akzeptierten Events |
| `errors` | Nur vorhanden wenn `totalDropped > 0` oder Verarbeitungsfehler aufgetreten |

**Fehlerfälle:**

| HTTP | Ursache |
|---|---|
| 400 | Fehlender `X-EPCIS-Source-ID` Header |
| 400 | XML nicht wohlgeformt oder XSD-Validierung fehlgeschlagen |
| 415 | Content-Type ist nicht `application/xml` |
| 500 | Interner Fehler (DB nicht erreichbar etc.) — Response enthält Referenz-ID für Logs |

**Wichtig — Fehler pro Event unterbrechen nicht die Session:**
Wenn ein Event fehlschlägt oder gedroppt wird, werden alle anderen Events weiterverarbeitet. Fehler werden in `CaptureResult.errors` gesammelt und zurückgegeben.

---

### GET /epcis/query/events

EPCIS 2.0 REST Binding — SimpleEventQuery Interface. Alle Parameter optional, kombinierbar.

**Parameter:**

| Parameter | EPCIS 2.0 Prefix | Beispiel | Beschreibung |
|---|---|---|---|
| `EQ_eventType` | `EQ_` | `ObjectEvent` | Filtert nach Event-Typ |
| `EQ_action` | `EQ_` | `ADD` | Filtert nach Action |
| `EQ_bizStep` | `EQ_` | `urn:epcglobal:cbv:bizstep:shipping` | Filtert nach Business Step URI |
| `EQ_disposition` | `EQ_` | `urn:epcglobal:cbv:disp:in_transit` | Filtert nach Disposition URI |
| `EQ_readPoint` | `EQ_` | `urn:epc:id:sgln:...` | Filtert nach Read Point |
| `EQ_bizLocation` | `EQ_` | `urn:epc:id:sgln:...` | Filtert nach Business Location |
| `MATCH_epc` | `MATCH_` | `urn:epc:id:sgtin:...` | Sucht in epcList **und** childEPCs |
| `MATCH_parentID` | `MATCH_` | `urn:epc:id:sscc:...` | Filtert nach parentID |
| `GE_eventTime` | `GE_` | `2024-01-15T00:00:00+01:00` | eventTime >= Wert |
| `LT_eventTime` | `LT_` | `2024-01-15T23:59:59+01:00` | eventTime < Wert |
| `maxEventCount` | — | `100` | Max Ergebnisse (default: 1000, max: 10000) |
| `orderBy` | — | `eventTime` | Sortierfeld: `eventTime` oder `recordTime`* |
| `orderDirection` | — | `DESC` | Sortierrichtung: `ASC` oder `DESC` |

*`recordTime` fällt in Phase 1 auf `eventTime` zurück (liegt im JSON-Payload, nicht als eigene DB-Spalte).

**Beispiel-Queries:**
```
GET /epcis/query/events?EQ_eventType=ObjectEvent&EQ_action=ADD
GET /epcis/query/events?MATCH_epc=urn:epc:id:sgtin:0614141.107346.2017
GET /epcis/query/events?GE_eventTime=2024-01-01T00:00:00Z&LT_eventTime=2024-02-01T00:00:00Z
GET /epcis/query/events?MATCH_parentID=urn:epc:id:sscc:0614141.1234567890
```

**Response 200:**
```json
{
  "totalResults": 2,
  "events": [
    { "type": "ObjectEvent", "eventID": "urn:uuid:...", "eventTime": "...", ... },
    { "type": "ObjectEvent", "eventID": "urn:uuid:...", "eventTime": "...", ... }
  ]
}
```

Die `events` sind direkt die gespeicherten EPCIS 2.0 JSON-Payloads aus der Datenbank.

---

### GET /epcis/query/events/{eventID}

Einzelnes Event per EPCIS eventID abrufen.

```
GET /epcis/query/events/urn:uuid:a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Hinweis zu URL-Encoding:** EPCIS eventIDs sind URNs und enthalten Doppelpunkte. Diese müssen URL-encoded werden:
- `:` → `%3A`
- `urn:uuid:abc` → `urn%3Auuid%3Aabc`

Für `urn:uuid:`-IDs ohne Slashes ist Doppelpunkt-Encoding ausreichend. Spring MVC und Tomcat verarbeiten dies korrekt.

**Response 200:** EPCIS 2.0 JSON-Objekt des Events

**Response 404:**
```json
{ "error": "Event not found: urn:uuid:..." }
```

---

## EPC-Filter (REQ101.1 + REQ216)

### Regelwerk

#### REQ101.1 — Erlaubte Formate in epcList und childEPCs

| EPC-Format | Erlaubt |
|---|---|
| `urn:epc:id:sgtin:` | ✅ |
| `urn:epc:id:sscc:` | ✅ |
| `urn:epc:id:sgln:` | ❌ nicht erlaubt in epcList |
| `urn:epc:id:grai:` | ❌ nicht erlaubt in epcList |
| `urn:epc:id:giai:` | ❌ nicht erlaubt in epcList |

#### REQ216 — Explizit verbotene Formate

| EPC-Format | Fehlercode | Maßnahme |
|---|---|---|
| `urn:epc:id:gid:` | `FORBIDDEN_GID` | Filtern + WARN-Log |
| `urn:epc:id:usdod:` | `FORBIDDEN_USDOD` | Filtern + WARN-Log |
| `urn:epc:id:adi:` | `FORBIDDEN_ADI` | Filtern + WARN-Log |
| `urn:epc:id:bic:` | `FORBIDDEN_BIC` | Filtern + WARN-Log |

#### REQ216 Punkt 2 — parentID Validierung (AggregationEvent)

`parentID` muss ein SSCC sein (`urn:epc:id:sscc:`). Wenn nicht: **Event wird komplett gedroppt** (nicht nur der EPC).

### Filter-Logik

```
Für jeden EPC:
  1. isForbidden? → FORBIDDEN_xxx Grund + aus Liste entfernen
  2. !isAllowedInEpcList? → UNSUPPORTED_FORMAT + aus Liste entfernen
  3. Sonst → accepted

Nach Filterung:
  acceptedEpcs.isEmpty() → eventShouldBeDropped = true

Für AggregationEvent zusätzlich:
  !isValidParentId(parentID) → eventShouldBeDropped = true, Grund: INVALID_PARENT_ID_NOT_SSCC
```

### Logging-Pflicht

Für jeden gefilterten EPC wird ein WARN-Log ausgegeben:
```
WARN EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}
```

Kein EPC wird silent gedroppt.

---

## Capture Audit

### Zweck

Die `capture_audit` Tabelle dient der Reconciliation während des Parallelbetriebs mit EECC. Sie ermöglicht den Vergleich: Wie viele Events hat EECC empfangen vs. wie viele hat das neue System empfangen?

### Schema

```sql
CREATE TABLE capture_audit (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    session_id     VARCHAR(36)  NOT NULL UNIQUE,
    source_id      VARCHAR(100) NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL,
    total_received INT          NOT NULL DEFAULT 0,
    total_accepted INT          NOT NULL DEFAULT 0,
    total_filtered INT          NOT NULL DEFAULT 0,
    total_dropped  INT          NOT NULL DEFAULT 0,
    total_errors   INT          NOT NULL DEFAULT 0
);
```

### Nutzung für Reconciliation

```sql
-- Events pro Quelle heute
SELECT source_id, SUM(total_received), SUM(total_accepted), SUM(total_dropped)
FROM capture_audit
WHERE received_at >= NOW() - INTERVAL '24 hours'
GROUP BY source_id;

-- Sessions mit Drops (Untersuchungsbedarf)
SELECT * FROM capture_audit
WHERE total_dropped > 0
ORDER BY received_at DESC;
```

---

## Verarbeitungsreihenfolge im CaptureEventUseCase

```
1. sourceId Pflichtfeld-Check          → IllegalArgumentException → HTTP 400
2. XSD-Validierung (EpcisXmlValidator) → EpcisValidationException → HTTP 400
3. XML-Parsing (EpcisXmlParser)        → EpcisParsingException    → HTTP 400
4. Pro Event (Fehler sammeln, nicht abbrechen):
   a. EpcFilterService.filter*Event()
   b. eventShouldBeDropped? → WARN-Log, in errors sammeln, überspringen
   c. Epcis2JsonRenderer.renderEvent()
   d. JsonDatabaseWriter.save()
   e. JsonFileWriter.write()
5. CaptureAuditRepository.save()       → immer, auch bei Teil-Fehlern
6. CaptureResult zurückgeben           → HTTP 201
```

---

## Neue Klassen — Übersicht

### Domain

| Klasse | Verantwortung |
|---|---|
| `EpcFormat` | Enum mit EPC-Präfixen und statischen Validierungsmethoden |
| `FilterResult` | Immutable Value Object — Ergebnis einer EPC-Filterung |
| `CaptureResult` | Ergebnis einer Capture-Session (HTTP-Response + Audit-Basis) |

### Application

| Klasse | Verantwortung |
|---|---|
| `EpcFilterService` | REQ101.1 + REQ216 Filterlogik, erzeugt neue Events ohne Mutation |
| `CaptureEventUseCase` | Orchestriert die vollständige Capture-Pipeline |
| `QueryEventUseCase` | EPCIS 2.0 SimpleEventQuery — wrappt `EpcisEventRepository` |
| `EpcisQueryException` | Domain-Exception → HTTP 404 |

### Infrastructure

| Klasse | Verantwortung |
|---|---|
| `CaptureAuditEntity` | JPA-Entity für `capture_audit` |
| `CaptureAuditRepository` | Spring Data Repository mit Reconciliation-Queries |

### API

| Klasse | Verantwortung |
|---|---|
| `CaptureController` | `POST /epcis/capture/events` |
| `CaptureResponse` | Response DTO für Capture |
| `QueryController` | `GET /epcis/query/events` + `GET /epcis/query/events/{eventID}` |
| `QueryResponse` | Response DTO für Query |

### Config

| Klasse | Verantwortung |
|---|---|
| `CaptureConfig` | `@ConfigurationProperties(prefix = "epcis.capture")` |

---

## Fehlerbehandlung — Zentralisiert

Alle HTTP-Fehlerantworten werden vom `GlobalExceptionHandler` produziert:

| Exception | HTTP | Wer wirft |
|---|---|---|
| `MissingRequestHeaderException` | 400 | Spring (fehlender Header) |
| `IllegalArgumentException` | 400 | Use Cases (Pflichtfelder) |
| `EpcisValidationException` | 400 | EpcisXmlValidator |
| `EpcisParsingException` | 400 | EpcisXmlParser |
| `CbvValidationException` | 422 | CbvVocabularyValidator |
| `EpcisQueryException` | 404 | QueryEventUseCase |
| `Epcis2SchemaValidationException` | 500 | JSON Schema Validator |
| `Exception` (Fallback) | 500 | Unerwartete Fehler — Referenz-ID in Response |

---

## Tests

### Unit Tests

| Klasse | Testfälle |
|---|---|
| `EpcFilterServiceTest` | 13 Tests: EpcFormat-Methoden, ObjectEvent-Filter, AggregationEvent-Filter |
| `CaptureEventUseCaseTest` | 6 Tests: Happy Path, EPC-Filterung, gemischte Events, Validation-Fehler |

### Integration Tests

| Klasse | Testfälle |
|---|---|
| `CaptureControllerIntegrationTest` | 7 Tests: POST Capture, GET Query, Fehler-Fälle — echte PostgreSQL via Testcontainers |

### Test-Fixtures

| Fixture | Inhalt |
|---|---|
| `object-event-observe.xml` | ObjectEvent OBSERVE mit eventID, bizTransactions, source/destination |
| `aggregation-event-add.xml` | AggregationEvent ADD mit SSCC parentID und 3 SGTIN childEPCs |
| `object-event-with-forbidden-epc.xml` | ObjectEvent mit gemischten EPCs (SGTIN + GID) — für Filter-Tests |
| `mixed-events.xml` | ObjectEvent + AggregationEvent in einem Dokument |

---

## Bekannte Einschränkungen Phase 1

| Einschränkung | Beschreibung | Phase |
|---|---|---|
| `recordTime` Sortierung | Fällt auf `eventTime` zurück — liegt im JSON-Payload, nicht als DB-Spalte | Phase 2 |
| Max XML-Größe nicht erzwungen | `maxXmlSizeKb` aus `CaptureConfig` wird noch nicht als Request-Guard geprüft | Phase 2 |
| `auditEnabled`/`fileWriterEnabled` | Flags vorhanden, aber nicht ausgewertet — immer aktiv | Phase 2 |
| `{eventID}` Slashes | eventIDs mit Slashes im NSS benötigen zusätzliche Tomcat-Konfiguration | Phase 2 |
| JSON-Input | Kein EPCIS 2.0 JSON-Intake — nur EPCIS 1.2 XML | Phase 3 |
| Business Logic | SGTIN State Matrix (REQ201+) nicht implementiert | Phase 2 |

---

## Parallelbetrieb-Checkliste

Vor Go-Live im Parallelbetrieb neben EECC:

```
☐ POST /epcis/capture/events antwortet < 500ms für normale XML-Größen
☐ GET /epcis/query/events gibt valide EPCIS 2.0 JSON zurück
☐ capture_audit Tabelle wird korrekt befüllt (Reconciliation möglich)
☐ Gefilterte EPC Logs erscheinen für GID/USDoD/ADI/BIC
☐ X-EPCIS-Source-ID Header wird korrekt validiert
☐ /actuator/health gibt {"status":"UP"} zurück
☐ Beide Endpunkte (alt: /api/events + neu: /epcis/capture) laufen gleichzeitig
☐ Kein bestehender Test bricht (Regression-Check)
☐ V3 Flyway-Migration läuft durch (capture_audit Tabelle angelegt)
```

---

## Phase 2 — Ausblick

Phase 2 implementiert die Business-Logik die in Phase 1 explizit ausgeschlossen wurde:

| Requirement | Beschreibung |
|---|---|
| REQ201 | SGTIN State Matrix / Event Filter Logic |
| REQ202 | Store Receiving Threshold |
| REQ205 | SSCC-SGTIN Aggregationsregeln |
| REQ206 | Multiple Aggregation Events für gleiche SSCC |
| REQ207 | SGTIN Status-Update via SSCC Observe Event |
| REQ208 | Disaggregation bei vollständigem Store-Receiving |
| REQ401 | EPC State DB |

Phase 2 baut vollständig auf Phase 1 auf — keine Breaking Changes an bestehenden Endpunkten.
