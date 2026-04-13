# EPCIS Capture Service — Phase 1 Implementierungsspezifikation
## Für Claude Code

---

## KONTEXT

**Unternehmen:** C&A (Retail)
**Situation:** Migration des EPCIS Repository von EECC (externem Partner) zu selbst gehostetem System.
**Basis:** Bestehender `epcis-event-handler` (Spring Boot 4.x, Java 17+, PostgreSQL 18, Maven)
**Strategie:** Parallelbetrieb bis Cut-Over — EECC und neues System laufen gleichzeitig.
**Historische Daten:** Kein Import — nur neue Events ab Go-Live.
**Capture Application Logic (SGTIN State Matrix, REQ201 ff.):** NICHT in Phase 1.

---

## PHASE 1 SCOPE

1. Capture Service — Events empfangen, XSD validieren, EPC filtern, speichern
2. EPC Filter Validation — REQ101.1 + REQ216 — ungültige EPCs filtern und loggen
3. EPCIS Query API — EPCIS 1.2 konforme Abfrage-Schnittstelle für Downstream-Systeme
4. Capture Audit Log — Reconciliation-Tabelle für Parallelbetrieb
5. Health & Readiness Endpoints — Spring Actuator

---

## PROJEKTSTRUKTUR

Vollständig NEUE Package-Struktur im bestehenden Projekt.
Bestehende Klassen NICHT anfassen — nur erweitern.

```
com.example.epcis/
├── api/
│   ├── EpcisEventController.java          # BESTEHEND — nicht ändern
│   ├── EpcisEventResponse.java            # BESTEHEND
│   ├── GlobalExceptionHandler.java        # BESTEHEND
│   ├── capture/                           # NEU
│   │   ├── CaptureController.java
│   │   └── CaptureResponse.java
│   └── query/                             # NEU
│       ├── QueryController.java
│       └── QueryResponse.java
├── application/
│   ├── ConvertEventUseCase.java           # BESTEHEND — nicht ändern
│   ├── capture/                           # NEU
│   │   ├── CaptureEventUseCase.java
│   │   └── EpcFilterService.java
│   └── query/                             # NEU
│       └── QueryEventUseCase.java
├── domain/
│   └── model/                             # BESTEHEND + Erweiterungen
│       ├── EpcisEvent.java                # BESTEHEND
│       ├── ObjectEvent.java               # BESTEHEND
│       ├── AggregationEvent.java          # BESTEHEND
│       ├── Action.java                    # BESTEHEND
│       ├── BusinessTransaction.java       # BESTEHEND
│       ├── QuantityElement.java           # BESTEHEND
│       ├── EpcFormat.java                 # NEU
│       ├── FilterResult.java              # NEU
│       └── CaptureResult.java             # NEU
├── infrastructure/
│   ├── xml/                               # BESTEHEND — nicht ändern
│   ├── json/                              # BESTEHEND — nicht ändern
│   └── persistence/                       # BESTEHEND + Erweiterungen
│       ├── EpcisEventEntity.java          # BESTEHEND
│       ├── EpcisEventRepository.java      # BESTEHEND — Methoden ergänzen
│       ├── JsonDatabaseWriter.java        # BESTEHEND — nicht ändern
│       ├── JsonFileWriter.java            # BESTEHEND — nicht ändern
│       └── audit/                         # NEU
│           ├── CaptureAuditEntity.java
│           └── CaptureAuditRepository.java
└── config/
    └── CaptureConfig.java                 # NEU
```

---

## 1. DOMAIN MODEL — NEUE KLASSEN

### EpcFormat.java
**Package:** `com.example.epcis.domain.model`

```java
/**
 * Erlaubte und verbotene EPC Pure Identity URI Formate.
 *
 * REQ101.1: epcList, parentId, childEPCs dürfen nur SGTIN und SSCC enthalten.
 * REQ216:   GID, USDoD, ADI, BIC sind explizit verboten und müssen gefiltert werden.
 *
 * Filterlogik:
 * - isForbidden() → immer entfernen + loggen (REQ216)
 * - isAllowedInEpcList() → SGTIN und SSCC erlaubt
 * - isValidParentId() → nur SSCC erlaubt (REQ216 Punkt 2)
 */
public enum EpcFormat {
    SGTIN("urn:epc:id:sgtin:"),
    SSCC("urn:epc:id:sscc:"),
    SGLN("urn:epc:id:sgln:"),
    GRAI("urn:epc:id:grai:"),
    GIAI("urn:epc:id:giai:");

    private final String prefix;

    EpcFormat(String prefix) { this.prefix = prefix; }
    public String getPrefix() { return prefix; }

    public static boolean isAllowedInEpcList(String epc) {
        // REQ101.1: nur SGTIN und SSCC
        if (epc == null || epc.isBlank()) return false;
        return epc.startsWith("urn:epc:id:sgtin:")
            || epc.startsWith("urn:epc:id:sscc:");
    }

    public static boolean isValidParentId(String epc) {
        // REQ216 Punkt 2: parentID muss SSCC sein
        if (epc == null || epc.isBlank()) return false;
        return epc.startsWith("urn:epc:id:sscc:");
    }

    public static boolean isForbidden(String epc) {
        // REQ216 Punkt 1: GID, USDoD, ADI, BIC explizit verboten
        if (epc == null) return false;
        return epc.startsWith("urn:epc:id:gid:")
            || epc.startsWith("urn:epc:id:usdod:")
            || epc.startsWith("urn:epc:id:adi:")
            || epc.startsWith("urn:epc:id:bic:");
    }

    public static String detectFormat(String epc) {
        if (epc == null) return "NULL";
        for (EpcFormat f : values()) {
            if (epc.startsWith(f.prefix)) return f.name();
        }
        if (isForbidden(epc)) return "FORBIDDEN";
        return "UNKNOWN";
    }
}
```

### FilterResult.java
**Package:** `com.example.epcis.domain.model`

```java
/**
 * Ergebnis der EPC-Filterung für ein einzelnes Event.
 * Immutable Value Object.
 *
 * eventShouldBeDropped = true wenn:
 * - parentID eines AggregationEvent kein SSCC ist
 * - nach Filterung keine EPCs mehr übrig sind
 */
@Getter
@Builder
public class FilterResult {
    private final List<String> acceptedEpcs;
    private final List<FilteredEpc> filteredEpcs;
    private final boolean eventShouldBeDropped;
    private final String dropReason; // nur gesetzt wenn eventShouldBeDropped=true

    @Getter
    @Builder
    public static class FilteredEpc {
        private final String epc;
        private final String reason;
        // Erlaubte Werte: FORBIDDEN_GID, FORBIDDEN_USDOD, FORBIDDEN_ADI,
        //                 FORBIDDEN_BIC, UNSUPPORTED_FORMAT,
        //                 INVALID_PARENT_ID_NOT_SSCC
    }
}
```

### CaptureResult.java
**Package:** `com.example.epcis.domain.model`

```java
/**
 * Gesamtergebnis einer Capture-Session.
 * Wird als HTTP Response zurückgegeben und in capture_audit persistiert.
 */
@Getter
@Builder
public class CaptureResult {
    private final String sessionId;
    private final int totalReceived;
    private final int totalAccepted;
    private final int totalFiltered;   // EPCs gefiltert, Event behalten
    private final int totalDropped;    // Events komplett abgelehnt
    private final List<String> captureIds;
    private final List<CaptureError> errors;

    @Getter
    @Builder
    public static class CaptureError {
        private final String eventId;
        private final String errorCode;
        private final String message;
    }
}
```

---

## 2. APPLICATION — CAPTURE

### EpcFilterService.java
**Package:** `com.example.epcis.application.capture`
**Annotation:** `@Service`

**Methoden:**

```java
/**
 * Filtert ungültige EPCs aus EPCIS Events.
 *
 * Implementiert REQ101.1 und REQ216.
 *
 * Logging-Pflicht (REQ216 + REQ603) — für jeden gefilterten EPC MUSS geloggt werden:
 *   WARN "EPC_FILTERED epc={} eventId={} eventType={} reason={} filteredCount={}"
 *
 * Reihenfolge der Prüfung:
 * 1. isForbidden? → FORBIDDEN_xxx
 * 2. isAllowedInEpcList? → UNSUPPORTED_FORMAT
 */
public FilterResult filterObjectEvent(ObjectEvent event);

/**
 * Zusätzlich zu EPC-Filter:
 * parentID muss SSCC sein — wenn nicht → eventShouldBeDropped=true (REQ216 Punkt 2)
 */
public FilterResult filterAggregationEvent(AggregationEvent event);
```

**Wichtig:**
- Nach Filterung: wenn acceptedEpcs leer → `eventShouldBeDropped = true`
- `applyFilter()` gibt ein neues Event-Objekt zurück mit den gefilterten EPCs (nicht das Original mutieren)
- Jeder gefilterte EPC wird einzeln geloggt — nie silent droppen

### CaptureEventUseCase.java
**Package:** `com.example.epcis.application.capture`
**Annotation:** `@Service`, `@Transactional`

**Abhängigkeiten (Constructor Injection):**
- `EpcisXmlValidator` (bestehend)
- `EpcisXmlParser` (bestehend)
- `EpcFilterService` (neu)
- `Epcis2JsonRenderer` (bestehend)
- `JsonDatabaseWriter` (bestehend)
- `JsonFileWriter` (bestehend)
- `CaptureAuditRepository` (neu)

**Methode:**
```java
/**
 * Vollständiger Capture-Prozess:
 * 1. XSD-Validierung → EpcisValidationException bei Fehler (→ HTTP 400)
 * 2. XML Parsen → Domain-Objekte
 * 3. Pro Event: EPC filtern, ggf. droppen
 * 4. Akzeptierte Events: JSON rendern + in DB + in Datei schreiben
 * 5. CaptureAudit speichern
 * 6. CaptureResult zurückgeben
 *
 * Fehler in einzelnen Events unterbrechen NICHT die gesamte Session.
 * Fehler werden in CaptureResult.errors gesammelt.
 *
 * @param xml      EPCIS 1.2 XML
 * @param sourceId Datenquelle — z.B. "STORE-DE-001", "DC-HAMBURG", "PARTNER-DM"
 *                 Pflichtfeld — wenn null oder leer → IllegalArgumentException
 */
@Transactional
public CaptureResult capture(String xml, String sourceId);
```

**Logging-Konventionen:**
```
INFO  CAPTURE_START  sessionId={} sourceId={} xmlLength={}
INFO  CAPTURE_PARSED sessionId={} eventCount={}
WARN  CAPTURE_EVENT_DROPPED sessionId={} eventId={} reason={}
ERROR CAPTURE_EVENT_ERROR sessionId={} eventId={} error={}
INFO  CAPTURE_COMPLETE sessionId={} received={} accepted={} dropped={} errors={}
```

---

## 3. APPLICATION — QUERY

### QueryEventUseCase.java
**Package:** `com.example.epcis.application.query`
**Annotation:** `@Service`

**Zweck:** EPCIS 1.2 konformes Query Interface für Downstream-Systeme.
Diese Klasse wrappt das bestehende `EpcisEventRepository` mit EPCIS-konformen
Parameter-Namen und Response-Strukturen.

**Methoden:**
```java
/**
 * SimpleEventQuery — entspricht EPCIS 1.2 Query Interface SimpleEventQuery.
 * Alle Parameter optional, kombinierbar.
 *
 * EPCIS 1.2 Parameter → intern mapped auf EpcisEventRepository.search()
 *
 * @param eventType          "ObjectEvent" | "AggregationEvent" | null
 * @param action             "ADD" | "OBSERVE" | "DELETE" | null
 * @param bizStep            URI z.B. "urn:epcglobal:cbv:bizstep:shipping" | null
 * @param disposition        URI | null
 * @param readPoint          SGLN URI | null
 * @param bizLocation        SGLN URI | null
 * @param epcMatch           EPC URI (sucht in epcList + childEPCs) | null
 * @param parentId           SSCC URI | null
 * @param gln                SGLN URI (sucht in readPoint + bizLocation) | null
 * @param eventTimeLT        eventTime < Wert | null
 * @param eventTimeGT        eventTime > Wert | null
 * @param maxEventCount      maximale Anzahl Ergebnisse (default: 1000, max: 10000)
 * @param orderBy            "eventTime" | "recordTime" (default: "eventTime")
 * @param orderDirection     "ASC" | "DESC" (default: "DESC")
 */
public List<EpcisEvent> simpleEventQuery(
    String eventType, String action, String bizStep, String disposition,
    String readPoint, String bizLocation, String epcMatch, String parentId,
    String gln, OffsetDateTime eventTimeLT, OffsetDateTime eventTimeGT,
    Integer maxEventCount, String orderBy, String orderDirection);

/**
 * Einzelnes Event per eventID abrufen.
 * @throws EpcisQueryException wenn nicht gefunden (→ HTTP 404)
 */
public EpcisEvent getEventById(String eventId);
```

---

## 4. INFRASTRUCTURE — AUDIT

### CaptureAuditEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.audit`
**Annotation:** `@Entity`, `@Table(name = "capture_audit")`

**Felder:**

| Spalte | Typ | Nullable | Beschreibung |
|---|---|---|---|
| id | BIGINT IDENTITY | NOT NULL | PK |
| session_id | VARCHAR(36) UNIQUE | NOT NULL | UUID der Capture-Session |
| source_id | VARCHAR(100) | NOT NULL | Datenquelle (z.B. "STORE-DE-001") |
| received_at | TIMESTAMPTZ | NOT NULL | Zeitpunkt des Eingangs |
| total_received | INT | NOT NULL | Anzahl Events im XML |
| total_accepted | INT | NOT NULL | Erfolgreich gespeicherte Events |
| total_filtered | INT | NOT NULL | Events bei denen EPCs gefiltert wurden |
| total_dropped | INT | NOT NULL | Komplett abgelehnte Events |
| total_errors | INT | NOT NULL | Fehler während Verarbeitung |

### CaptureAuditRepository.java
**Package:** `com.example.epcis.infrastructure.persistence.audit`
**Annotation:** `@Repository`
**Extends:** `JpaRepository<CaptureAuditEntity, Long>`

```java
List<CaptureAuditEntity> findBySourceIdOrderByReceivedAtDesc(String sourceId);
List<CaptureAuditEntity> findByReceivedAtBetween(OffsetDateTime from, OffsetDateTime to);
long countByReceivedAtBetween(OffsetDateTime from, OffsetDateTime to);
```

---

## 5. API LAYER

### CaptureController.java
**Package:** `com.example.epcis.api.capture`
**Annotation:** `@RestController`, `@RequestMapping("/epcis/capture")`

**Endpunkt:**
```
POST /epcis/capture/events
Content-Type: application/xml
Header: X-EPCIS-Source-ID: {sourceId}   ← Pflicht-Header

Response 200: CaptureResponse (JSON)
Response 400: { "error": "..." }         — XSD-Fehler, fehlender Source-ID Header
Response 500: { "error": "..." }         — interner Fehler
```

**Wichtig:** `X-EPCIS-Source-ID` Header ist Pflicht.
Wenn fehlend → HTTP 400 mit `{"error": "X-EPCIS-Source-ID header is required"}`.

```java
@PostMapping(
    value = "/events",
    consumes = MediaType.APPLICATION_XML_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<CaptureResponse> captureEvents(
    @RequestBody String xml,
    @RequestHeader("X-EPCIS-Source-ID") String sourceId);
```

### CaptureResponse.java
**Package:** `com.example.epcis.api.capture`

```java
// Direkt aus CaptureResult gemappt
@Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptureResponse {
    private final String sessionId;
    private final int totalReceived;
    private final int totalAccepted;
    private final int totalFiltered;
    private final int totalDropped;
    private final List<String> captureIds;
    private final List<ErrorDetail> errors;

    @Getter @Builder
    public static class ErrorDetail {
        private final String eventId;
        private final String errorCode;
        private final String message;
    }
}
```

### QueryController.java
**Package:** `com.example.epcis.api.query`
**Annotation:** `@RestController`, `@RequestMapping("/epcis/query")`

**Endpunkte:**

```
GET /epcis/query/events
    Parameter (alle optional):
    ?eventType=ObjectEvent
    ?action=ADD
    ?bizStep=urn:epcglobal:cbv:bizstep:shipping
    ?disposition=urn:epcglobal:cbv:disp:in_transit
    ?readPoint=urn:epc:id:sgln:...
    ?bizLocation=urn:epc:id:sgln:...
    ?epcMatch=urn:epc:id:sgtin:...        ← sucht in epcList UND childEPCs
    ?parentId=urn:epc:id:sscc:...
    ?gln=urn:epc:id:sgln:...              ← sucht in readPoint + bizLocation
    ?eventTimeGT=2024-01-15T00:00:00+02:00
    ?eventTimeLT=2024-01-15T23:59:59+02:00
    ?maxEventCount=100                     ← default 1000, max 10000
    ?orderBy=eventTime                     ← eventTime | recordTime
    ?orderDirection=DESC                   ← ASC | DESC

Response 200: QueryResponse (JSON)

GET /epcis/query/events/{eventId}
Response 200: einzelnes QueryResponse.Event
Response 404: { "error": "Event not found: {eventId}" }
```

### QueryResponse.java
**Package:** `com.example.epcis.api.query`

```java
@Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResponse {
    private final int totalResults;
    private final List<Object> events;  // geparste JSON-Payloads aus DB
}
```

---

## 6. KONFIGURATION

### CaptureConfig.java
**Package:** `com.example.epcis.config`
**Annotation:** `@Configuration`, `@ConfigurationProperties(prefix = "epcis.capture")`

```java
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.capture")
public class CaptureConfig {
    private int maxEventsPerRequest = 1000;  // max Events pro XML-Dokument
    private int maxXmlSizeKb = 10240;        // max 10 MB pro Request
    private boolean auditEnabled = true;
    private boolean fileWriterEnabled = true;
}
```

### application.yml — Ergänzungen
```yaml
# Ergänze in der bestehenden application.yml:

epcis:
  output:
    directory: ./output/events    # BESTEHEND
  schema:
    path: classpath:xsd/EPCglobal-epcis-1_2.xsd  # BESTEHEND
  capture:
    max-events-per-request: 1000
    max-xml-size-kb: 10240
    audit-enabled: true
    file-writer-enabled: true

# Spring Actuator für Health Checks
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

---

## 7. DATENBANK — NEUE TABELLEN

Hibernate erstellt diese automatisch durch `ddl-auto: update`.
Trotzdem hier explizit für Dokumentation und manuelle Migration:

```sql
-- Capture Audit Tabelle
CREATE TABLE capture_audit (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    session_id   VARCHAR(36)  NOT NULL UNIQUE,
    source_id    VARCHAR(100) NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL,
    total_received INT        NOT NULL DEFAULT 0,
    total_accepted INT        NOT NULL DEFAULT 0,
    total_filtered INT        NOT NULL DEFAULT 0,
    total_dropped  INT        NOT NULL DEFAULT 0,
    total_errors   INT        NOT NULL DEFAULT 0
);

-- Index für häufige Abfragen
CREATE INDEX idx_capture_audit_source   ON capture_audit(source_id);
CREATE INDEX idx_capture_audit_received ON capture_audit(received_at);

-- Bestehende epcis_event Tabelle — kein Änderungsbedarf
-- (source_id könnte später ergänzt werden — nicht in Phase 1)
```

---

## 8. TESTS — MINIMUM FÜR PHASE 1

### EpcFilterServiceTest.java
**Package:** `com.example.epcis.application.capture`

Pflicht-Testfälle:
```
✅ filterObjectEvent — valide SGTINs → alle akzeptiert
✅ filterObjectEvent — GID EPC → gefiltert, Event behalten (andere EPCs noch da)
✅ filterObjectEvent — alle EPCs verboten → eventShouldBeDropped = true
✅ filterObjectEvent — leere epcList → kein Fehler
✅ filterAggregationEvent — parentID kein SSCC → eventShouldBeDropped = true
✅ filterAggregationEvent — valide childEPCs → alle akzeptiert
✅ filterAggregationEvent — gemischte EPCs (valid + GID) → nur valid behalten
✅ EpcFormat.isForbidden — GID, USDoD, ADI, BIC → true
✅ EpcFormat.isAllowedInEpcList — SGTIN, SSCC → true; SGLN → false
✅ EpcFormat.isValidParentId — SSCC → true; SGTIN → false
```

### CaptureEventUseCaseTest.java
Pflicht-Testfälle:
```
✅ capture — valides XML → CaptureResult.totalAccepted = Anzahl Events
✅ capture — XML mit verbotenen EPCs → gefiltert, totalFiltered > 0
✅ capture — ungültiges XML → EpcisValidationException
✅ capture — sourceId null/blank → IllegalArgumentException
✅ capture — XML mit gemischten Events (ObjectEvent + AggregationEvent)
```

### CaptureControllerIntegrationTest.java
Pflicht-Testfälle:
```
✅ POST /epcis/capture/events — valides XML + Header → 200 + CaptureResponse
✅ POST /epcis/capture/events — fehlendes X-EPCIS-Source-ID Header → 400
✅ POST /epcis/capture/events — ungültiges XML → 400
✅ GET /epcis/query/events — kein Parameter → 200 + alle Events
✅ GET /epcis/query/events?eventType=AggregationEvent → nur AggregationEvents
✅ GET /epcis/query/events/{id} — vorhandene ID → 200
✅ GET /epcis/query/events/{id} — unbekannte ID → 404
```

---

## 9. NICHT IN PHASE 1 — EXPLIZIT AUSGESCHLOSSEN

Diese Requirements sind dokumentiert aber NICHT implementieren:

| Requirement | Beschreibung | Phase |
|---|---|---|
| REQ201 | SGTIN State Matrix / Event Filter Logic | Phase 2 |
| REQ202 | Store Receiving Threshold | Phase 2 |
| REQ205 | SSCC-SGTIN Aggregationsregeln | Phase 2 |
| REQ206 | Multiple Aggregation Events für gleiche SSCC | Phase 2 |
| REQ207 | SGTIN Status-Update via SSCC Observe Event | Phase 2 |
| REQ208 | Disaggregation bei vollständigem Store-Receiving | Phase 2 |
| REQ209 | Void Sale Events | Phase 2 |
| REQ210 | SGTIN nur in einer Aggregation zur gleichen Zeit | Phase 2 |
| REQ212 | Departing Event erzeugen | Phase 2 |
| REQ401 | EPC State DB | Phase 2 |
| REQ102 | EPCIS Subscription Query Interface | Phase 3 |
| REQ301-316 | Alle Downstream-Integrationen (GSPM, DWH, ERP, EWM) | Phase 3 |

---

## 10. PARALLELBETRIEB-CHECKLISTE

Bevor das neue System parallel zu EECC läuft, muss sichergestellt sein:

```
☐ POST /epcis/capture/events antwortet < 500ms für normale XML-Größen
☐ GET /epcis/query/events gibt valide EPCIS 2.0 JSON zurück
☐ capture_audit Tabelle wird korrekt befüllt
☐ Filtered EPC Logs erscheinen für verbotene Formate
☐ X-EPCIS-Source-ID Header wird korrekt validiert
☐ /actuator/health gibt {"status":"UP"} zurück
☐ Beide Endpunkte (alt: /api/events + neu: /epcis/capture) laufen gleichzeitig
☐ Kein bestehender Test bricht
```

---

## 11. DEFINITION OF DONE PHASE 1

```
☐ Alle Pflicht-Tests grün
☐ POST /epcis/capture/events verarbeitet ObjectEvent + AggregationEvent korrekt
☐ EPC Filter filtert GID/USDoD/ADI/BIC und loggt jeden gefilterten EPC
☐ parentID Validierung auf SSCC für AggregationEvents
☐ GET /epcis/query/events mit allen 11 Parametern funktioniert
☐ GET /epcis/query/events/{eventId} mit 200/404
☐ capture_audit Tabelle enthält Reconciliation-Daten pro Session
☐ X-EPCIS-Source-ID Header wird auf /epcis/capture erzwungen
☐ Bestehende /api/events Endpunkte weiter funktionsfähig (kein Regression)
☐ /actuator/health UP
☐ application.yml epcis.capture Properties konfiguriert
```
