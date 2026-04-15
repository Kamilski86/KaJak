# Wie wir das EPCIS Repository gebaut haben
### Eine vollständige, chronologische Dokumentation des Vorgehens
#### Für Team-Präsentation — C&A EPCIS Repository Projekt

---

## 1. Ausgangssituation: Was war das Problem?

C&A betrieb sein EPCIS Repository (Track & Trace für Produkte im Supply Chain) bei einem externen Partner namens **EECC**. Das Ziel war, dieses System selbst zu hosten — damit C&A unabhängig wird, die Daten selbst kontrolliert, und das System nach eigenen Anforderungen erweitern kann.

**Was EPCIS ist:** Ein globaler GS1-Standard für Ereignisse in der Lieferkette. Wenn ein Karton in einem Lager gescannt wird, wird ein EPCIS-Event erzeugt: "Wer hat was, wann, wo, in welchem Geschäftsprozess getan?"

**Das technische Problem:** Der externe Partner schickte Events im alten Format (**EPCIS 1.2 XML**). Das neue, eigene System musste sie ins moderne Format (**EPCIS 2.0 JSON**) konvertieren — semantisch korrekt, auditierbar und ohne Datenverlust.

**Entwicklungspartner:** Claude Code (AI-gestützte Entwicklung) — jede Phase hatte präzise Spec-Dokumente als Grundlage.

---

## 2. Der erste Schritt: Bevor Code geschrieben wurde

### 2.1 Klare Ziele festlegen (CLAUDE.md)

Bevor eine einzige Zeile Code entstand, wurden **nicht verhandelbare Regeln** schriftlich fixiert:

- Semantisch korrekte Konvertierung — keine rein syntaktische Transformation
- Niemals stille Fehler — kein `catch`-Block der einfach weitermacht
- Niemals Feldwerte erfinden — kein UUID als Business-`eventID` generieren
- Wenn Daten unklar oder ungültig sind: laut scheitern, Grund dokumentieren, Quarantäne
- Jede Konvertierung: deterministisch, idempotent, tracierbar

Diese Regeln wurden in einer Datei namens `CLAUDE.md` festgehalten. Diese Datei ist nicht nur für Menschen — sie ist auch die Arbeitsanweisung für den AI-Entwicklungspartner.

### 2.2 Enterprise Readiness Assessment (10. April)

Nach dem ersten Proof-of-Concept wurde ein detailliertes **Enterprise Readiness Assessment** durchgeführt. Fünf Perspektiven wurden systematisch analysiert: Senior Developer, Business Architect, Security Specialist, Tester, Business Analyst.

Das Assessment identifizierte die kritischen Lücken:

| Priorität | Problem | Warum kritisch |
|---|---|---|
| P0 | Keine Authentifizierung auf dem REST API | Kann nicht ans Netz ohne das |
| P0 | Stille Fehler: `null eventTime` wurde akzeptiert | Datenverlust |
| P0 | UUID wurde als Business-`eventID` emittiert | Verletzt EPCIS-Semantik |
| P0 | `ddl-auto: update` statt Flyway | Datenbankstruktur unkontrollierbar |
| P1 | Hexagonale Architektur deklariert, aber nicht durchgesetzt | Application-Layer importierte direkt Infrastructure |
| P1 | `sourceList`, `destinationList`, `errorDeclaration` fehlten | Mapping-Lücken |
| P1 | Keine GS1 EPCIS 2.0 JSON Schema-Validierung | Konformanz unklar |
| P1 | Keine CBV-Validierung | Ungültige Vokabular-Werte wurden durchgelassen |
| P2 | Keine Testcontainers-Integration-Tests | Echte DB-Tests fehlten |
| P2 | Kein Spring Actuator, keine Micrometer-Metriken | Kein Monitoring möglich |

Dieses Assessment war der Fahrplan für alles was danach gebaut wurde.

### 2.3 Architektur-Entscheidung (ADR-001)

Gleichzeitig wurde eine **Architecture Decision Record** erstellt — ein kurzes Dokument das festhält: Was haben wir entschieden, warum, welche Alternativen wurden abgelehnt.

**Entscheidung:** DDD + Hexagonale Architektur (Ports & Adapters) + Modularer Monolith

**Abgelehnte Alternativen:**

| Alternative | Warum abgelehnt |
|---|---|
| Direkter XML → JSON Mapper | "God class" — Parsing, Mapping, Validierung in einer Klasse. Untestbar. Keine natürliche Heimat für CBV- und Schema-Validierung. |
| Microservices | Operationelle Komplexität ohne Vorteil. Domain zu klein. Modular Monolith erlaubt spätere Extraktion. |

**Die Schichten-Regel:**
```
domain/        ← Reines Java. Kein Framework. Keine I/O.
application/   ← Use Cases. Kennt nur Ports (Interfaces) + Domain.
infrastructure/← Spring-Adapter. Implementiert die Ports.
api/           ← REST Controller.
config/        ← Spring Konfiguration.

REGEL: domain/ und application/ dürfen NIEMALS von infrastructure/ importieren.
```

---

## 3. Phase 0 — Foundation (10. April): Das Fundament legen

Der allererste lauffähige Stand enthielt:

**Domain-Modell (`domain/model/`):**
- `EpcisEvent` — abstrakte Basis mit allen gemeinsamen Feldern: `eventTime`, `eventTimeZoneOffset`, `recordTime`, `eventId`, `action`, `bizStep`, `disposition`, `readPoint`, `bizLocation`, `bizTransactionList`, `sourceList`, `destinationList`, `errorDeclaration`, `ilmd`, `extensions`
- `ObjectEvent` — mit `epcList` und `quantityList`
- `AggregationEvent` — mit `parentId`, `childEpcs`, `childQuantityList`
- Value Objects: `QuantityElement`, `BusinessTransaction`, `Source`, `Destination`, `ErrorDeclaration`, `IlmdPayload`, `ExtensionPayload`

**Anti-Corruption Layer (`infrastructure/xml/`):**
- `EpcisXmlParser` — liest EPCIS 1.2 XML (DOM) und gibt Domain-Objekte zurück. XXE-Angriffe abgesichert.
- `EpcisXmlValidator` — validiert XML gegen offizielles EPCIS 1.2 XSD-Schema.

Warum "Anti-Corruption Layer"? Weil EPCIS 1.2 XML-Strukturen (mit Namespace-Pollution, Attribut-basierten Typen, flat DOM-Bäumen) niemals in das Domain-Modell eindringen dürfen. Der Parser ist die Grenze — dahinter gibt es keine XML-Klassen mehr.

**JSON-Rendering (`infrastructure/json/`):**
- `Epcis2JsonRenderer` — nimmt Domain-Objekte, erzeugt EPCIS 2.0 JSON/JSON-LD

**Datenbank:**
- Flyway V1: `epcis_event` Tabelle mit `JSONB`-Spalte (PostgreSQL native JSON-Speicherung + GIN-Index für Abfragen)
- Flyway V2: `epcis_quarantine` Tabelle

**Persistence:**
- `JsonDatabaseWriter` — speichert konvertierte Events
- `DatabaseQuarantineStore` — speichert abgelehnte Events

**Port-Interfaces (`application/port/`):**
- `EventParser`, `EventValidator`, `EventRenderer`, `EventStore`, `EventAuditWriter`, `QuarantineStore` — alle als Java-Interfaces

**Use Case (`application/`):**
- `ConvertEventUseCase` — orchestriert die Pipeline. Importiert **nur** die Port-Interfaces und das Domain-Modell — null Infrastructure-Klassen.

**Commits:**
```
6b59f61  Initial commit: EPCIS 1.2 → 2.0 conversion foundation
a1096d4  Refine domain model, parser, renderer, and persistence layer
4326cd2  feat: initial EPCIS event handler implementation
```

---

## 4. Die Konversionspipeline: Wie ein Event verarbeitet wird

Das ist das Herzstück des Systems — jedes Event durchläuft diese Pipeline:

```
HTTP POST /api/events/convert
       │
       ▼
[Stage 1] EpcisXmlValidator
       ├── XXE-Härtung: DOCTYPE verboten, keine externen Entities
       ├── XML well-formed?
       └── Valide gegen EPCIS 1.2 XSD?
       FAIL → HTTP 400 (kein Quarantine — kein wiederherstellbarer Inhalt)
       │
       ▼
[Stage 2] EpcisXmlParser
       ├── eventTime vorhanden und parsebar?
       ├── action valide (ADD/OBSERVE/DELETE)?
       ├── event type bekannt (ObjectEvent/AggregationEvent)?
       └── → Domain-Objekt erzeugen
       FAIL (unbekannter Event-Typ) → QUARANTINE (UNSUPPORTED_EVENT_TYPE) + HTTP 422
       │
       ▼
[Stage 5] CbvVocabularyValidator
       ├── bizStep in CBV 2.0 Allowlist (45 Werte)?
       ├── disposition in CBV 2.0 Allowlist (29 Werte)?
       ├── sourceList/destinationList Typen valide?
       └── errorDeclaration.reason valide?
       FAIL → QUARANTINE (CBV_VIOLATION) + HTTP 422
       │
       ▼
[Render] Epcis2JsonRenderer
       └── Domain → EPCIS 2.0 JSON Envelope
       │
       ▼
[Persist] JsonDatabaseWriter → epcis_event (PostgreSQL JSONB)
[Audit]   JsonFileWriter → ./output/events/*.json
       │
       ▼
[Stage 4] Epcis2JsonSchemaValidator
       └── Valide gegen offizielles GS1 EPCIS 2.0 JSON Schema (Draft-07)?
       FAIL → HTTP 500 (Renderer-Bug, nicht Client-Fehler)
       │
       ▼
HTTP 200 OK — EpcisDocumentDto
```

Die Stage-Nummerierung folgt dem Validation Strategy Dokument. Stages 3 und 6 (kanonische Domain-Validierung und Migration-Reconciliation) sind für spätere Releases geplant.

---

## 5. Das Mapping-Matrix Prinzip: Jede Feldentscheidung dokumentiert

Eine der wichtigsten Designentscheidungen war: **Jede Feldübersetzung von EPCIS 1.2 → EPCIS 2.0 muss dokumentiert sein** (in `docs/mapping-matrix.md`).

Das Mapping erfolgt immer über drei Stufen:

```
EPCIS 1.2 XML Feld  →  Canonical Model Feld  →  EPCIS 2.0 JSON Feld
```

**Beispiele dokumentierter Mapping-Entscheidungen:**

| Status | Feld | Entscheidung |
|---|---|---|
| ✅ | `eventTime` | Pflichtfeld. Als `OffsetDateTime` geparst. Fehlt → Exception. |
| ✅ | `readPoint/id` | In 1.2 nested `<id>` Element → in 2.0 Objekt `{"id": "..."}` |
| ⚠️ | `eventID` | Optional in 1.2. Fehlt → wird NICHT erfunden und NICHT emittiert |
| ⚠️ | `ilmd` | Flat leaf-text Extraktion. Auf DELETE-Events immer abgelehnt. |
| ⚠️ | `extension` | Nur lokaler Name. Namespace-URI wird nicht bewahrt. |
| 🚫 | `TransactionEvent` | In Quarantäne — nicht im Scope |
| 🚫 | `TransformationEvent` | In Quarantäne — EPCIS 2.0 only, kein 1.2 Äquivalent |
| ❌ | `persistentDisposition` | Nicht generiert — darf nicht aus 1.2 abgeleitet werden |
| ❌ | `sensorElementList` | Nicht gemappt — out of scope |

Warum ist das so wichtig? Ohne diese Matrix arbeiten verschiedene Entwickler nach verschiedenen Interpretationen — und man merkt es erst, wenn ein Downstream-System falsche Daten bekommt.

---

## 6. Phase 1 — Capture Service (12.-13. April): Echten Traffic empfangen

### Was fehlte

Die Foundation konnte konvertieren — aber noch keinen echten Traffic von Stores empfangen. Es gab keinen dedizierten Capture-Endpunkt, kein EPC-Filtering, keine Capture-Audit-Tabelle.

### Zuerst: Spec-Dokument schreiben

Bevor Code geschrieben wurde, entstand `docs/CAPTURE_SERVICE_PHASE1_SPEC.md`. Dieses Dokument enthielt:
- **Scope** — was IN dieser Phase ist, und was EXPLIZIT NICHT
- Neue Package-Struktur
- Datenbankschema (neue Tabellen)
- Neue API-Endpunkte (Methode, Pfad, Request, Response)
- Test-Anforderungen

**Explizit NICHT in Phase 1:**
- SGTIN State Matrix (REQ201 ff.) — Phase 2
- Push-Benachrichtigungen — Phase 3
- Integration mit SAP/DWH — Phase 3

Dieser Negativ-Scope ist genauso wichtig wie der positive. Ohne ihn entsteht Feature Creep.

### Was implementiert wurde

**EPC Filter (`application/capture/EpcFilterService.java`):**  
Nicht alle EPC-Codes von Stores sind gültig formatiert. REQ101.1 und REQ216 definieren erlaubte Formate (SGTIN, SSCC, etc.). Der Filter:
- Akzeptiert valide EPCs → werden normal verarbeitet
- Filtert ungültige EPCs → WARN-Log + `FilterResult` (nicht still verworfen!)
- Führt niemals dazu, dass das ganze Event abgelehnt wird

**Capture Use Case (`application/capture/CaptureEventUseCase.java`):**  
Neuer Endpunkt `POST /epcis/capture/events`. Für jedes Event im XML-Dokument:
1. XSD-Validierung
2. XML-Parsing
3. EPC-Filter
4. JSON-Rendering
5. Speichern in DB + Audit-Datei
6. `CaptureAuditRepository.save()`

**Capture Audit Log (Flyway V2):**  
Neue Tabelle `capture_audit` mit: Session-ID, Source-ID (z.B. `STORE-DE-001`), Eingangszeit, total received, accepted, filtered, dropped, errors. Das ist die Grundlage für den **Parallelbetrieb** — man kann direkt vergleichen ob EECC und das neue System dieselben Zahlen sehen.

**Query API (`api/query/QueryController.java`):**  
EPCIS 2.0 REST Binding konforme Query-Endpunkte:
- `GET /epcis/query/events` — SimpleEventQuery
- `GET /epcis/query/events/{eventID}` — Einzelnes Event

**Observability:**  
- Spring Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Micrometer-Counters: `epcis.events.received`, `epcis.events.converted`, `epcis.events.quarantined`, `epcis.events.failed`
- `CorrelationIdFilter` — jeder Request bekommt eine UUID, alle Log-Zeilen enthalten sie

**Commits:**
```
618bec4  feat: epcis event handler phase 1 complete - before capture service
3f67e97  feat: implement EPCIS Capture Service Phase 1
5be21ef  fix: resolve test failures ahead of Phase 1 PR
803ff50  fix: normalize EPCIS 1.2 CBV URNs to EPCIS 2.0 short names in renderer
```

---

## 7. Phase 2 — Inventory Service (14. April): Wo ist welches Produkt?

### Was fehlte

Events empfangen und speichern reicht nicht — das Business will wissen: **Wo ist Produkt X gerade?** Events erzählen eine Geschichte, aber sie liefern keine direkte Antwort auf "Wo ist das jetzt?"

### Spec-Dokument: `docs/INVENTORY_SERVICE_PHASE2_SPEC.md`

Scope:
- EPC State DB — letzter bekannter Zustand jeder SGTIN
- Bewegungshistorie
- SSCC-Inhalte (Paletteninhalte)
- Rebuild-Endpoint

Explizit NICHT:
- SGTIN State Matrix REQ201 — Phase 3
- Automatische Disaggregation bei Store Receiving — Phase 3
- Push-Benachrichtigungen — Phase 3

### Was implementiert wurde

**Datenbankschema (Flyway V3 + V4):**
- `epc_state` — letzter bekannter Zustand jeder SGTIN: Standort, bizStep, disposition, letztes Event-Timestamp
- `sscc_content` — welche SGTINs sind auf welcher Palette (SSCC)?
- `movement_history` — vollständige Bewegungshistorie

**Inventory Processor (`application/inventory/InventoryProcessorService.java`):**  
Läuft nach jeder erfolgreichen Capture und aktualisiert `epc_state`. Wenn ein Event sagt "SGTIN X wurde in Store DE-001 empfangen (action: ADD, bizStep: receiving)", wird der Zustand von X auf diesen Standort aktualisiert.

**Rebuild-Endpoint:**  
`POST /api/inventory/rebuild` — löscht alle berechneten Zustände und berechnet sie komplett neu aus den rohen Events. Das garantiert **Idempotenz** — `epc_state` ist immer konsistent mit dem tatsächlichen Event-Stream.

**Query-Endpunkte:**
- `GET /api/inventory/epc/{epc}` — Wo ist dieser EPC gerade? (aktueller Zustand + Bewegungshistorie)
- `GET /api/inventory/location/{gln}` — Was liegt an diesem Standort?
- `GET /api/inventory/sscc/{sscc}` — Was ist auf dieser Palette?
- `GET /api/inventory/stock` — Bestandsübersicht nach GTIN
- `GET /api/inventory/available` — Verfügbare Mengen

**Commits:**
```
c6d27af  feat: add Inventory Service Phase 2 — EPC state tracking, movement history, SSCC content, rebuild endpoint
e42564d  fix: phase 2 smoke test passed - inventory processor working
```

---

## 8. Phase 3 — Downstream Integrations (14. April): Andere Systeme versorgen

### Was fehlte

Das System hatte Events und Bestandsdaten — aber andere Systeme (GSPM, SAP ERP, DWH, SAP EWM, ItemOptix) wussten davon nichts.

### Spec-Dokument: `docs/DOWNSTREAM_INTEGRATIONS_PHASE3_SPEC.md`

**Wichtige Design-Entscheidung vor Beginn: Kein Kafka.**  
Warum? Kafka bringt eigene Infrastruktur (Broker, Offset-Management, Consumer Groups). Die Anforderungen ließen sich mit einem einfacheren Muster lösen.

Stattdessen: **Outbox Pattern** + Spring `@Scheduled` Jobs.

**Was ist das Outbox Pattern?**  
Anstatt direkt aus dem Capture-Pfad in externe Systeme zu schreiben (was bei einem Fehler inkonsistente Zustände erzeugt), wird jedes Event **zuerst in eine `outbox` Tabelle** geschrieben. Ein separater Hintergrundprozess (`OutboxProcessor`) liest diese Tabelle und liefert an Downstream. Wenn ein externes System nicht erreichbar ist, bleibt das Event in der Outbox und wird beim nächsten Lauf neu versucht.

```
Capture Event
    │
    ├── epcis_event ← zuerst, immer
    └── outbox ← für Downstream-Lieferung
           │
           ▼
    OutboxProcessor (Background Job)
           │
           ├─ GSPM (REQ301/302/303)
           ├─ SAP ERP via Mule (REQ308)
           ├─ DWH (REQ305/306)
           └─ SAP EWM (REQ309)
```

**Scope Phase 3:**
- Block A: GSPM-Integrationen (REQ301-303) — Inventory, Shipping, Arrival Notifications
- Block B: ERP + DWH + EWM (REQ305/306/308/309)
- Block C: Subscription Service — externe Systeme registrieren sich und werden benachrichtigt (EPCIS 2.0 Subscription-Mechanismus)

**Subscription Service:**  
`POST /api/subscriptions` — Registrierung. `SubscriptionDispatcher` sendet Events an registrierte Empfänger (Halo, ItemOptix, Mule).

**Commits:**
```
b2126f8  feat: Phase 3 Downstream Integrations — Outbox, Subscription Service, Ops API
c6d2e6c  fix: replace HALO with ITEMOPTIX throughout (system rename)
```

---

## 9. Phase 4 — CBV Validator + GS1 Digital Link (15. April): Standards-Konformanz

### Was fehlte

Das System validierte Struktur (Stage 1) und basic Semantik (Stage 2), aber noch nicht die **Werte** der Business-Vokabular-Felder.

### Block A: CBV Conformance Validator

CBV = Core Business Vocabulary — die offizielle GS1-Liste erlaubter Werte.

**Validierte Felder:**
- `bizStep` — 45 erlaubte Werte (z.B. `urn:epcglobal:cbv:bizstep:shipping`)
- `disposition` — 29 erlaubte Werte (z.B. `urn:epcglobal:cbv:disp:in_transit`)
- `errorDeclaration.reason` — 2 erlaubte Werte
- `sourceList/destinationList` Typen — 3 erlaubte SDT-Werte
- ILMD auf DELETE-Events — immer abgelehnt (CBV-Semantik-Regel)

**Wer validiert:** `CbvVocabularyValidator` (Domain Service) + `CbvValidationService` (Application Service)

**Woher kommen die erlaubten Werte?** Datenbanktabelle `cbv_vocabulary` (Flyway V7), die beim Start der Anwendung aus der CBV-Referenzdatei befüllt wird — so können neue CBV-Versionen ohne Code-Änderung eingespielt werden.

**Bei Verstoß:** Quarantäne mit Fehlercode `CBV_VIOLATION` + Original-XML erhalten.

**User-defined Extensions** (nicht `urn:epcglobal:cbv:*`): werden mit WARN-Log akzeptiert — Partner-Extensions blockieren nicht die Pipeline.

### Block B: GS1 Digital Link Resolver

GS1 Digital Link ist ein Standard der QR-Codes auf Produkten mit Track-&-Trace-Daten verknüpft.

**Endpunkt:** `GET /api/digital-link/resolve?url=...`

Ein QR-Code-Scan liefert damit:
- Vollständige Event-Historie des Produkts
- Aktueller Bestandsstatus (aus Phase 2)

**`DigitalLinkParser`** — parsed GS1 Digital Link URLs (mit Application Identifiers wie `01` für GTIN, `21` für Seriennummer) in strukturierte `DigitalLinkUri` Objekte.

### Letzter Fix: GS1 TDS 2.0 §6.3.2 SSCC-Validierung

SSCC-Codes (Paletten-IDs) haben eine exakte Struktur nach GS1 TDS 2.0 §6.3.2: 18 Ziffern, spezifisches Check-Digit-Verfahren (Modulo-10). Das wurde als finale Härtung eingebaut.

**Commits:**
```
26ebbfc  feat: Phase 4 — CBV Validator + GS1 Digital Link Resolver
b96a720  fix: add missing H2 migration scripts V3-V6 for local profile
04c1ea0  fix: enforce GS1 TDS 2.0 §6.3.2 SSCC structural validation
```

---

## 10. Das Testing-Konzept: Drei Ebenen

### Ebene 1: Unit Tests (mit Mockito)

Geschäftslogik ohne Datenbank, ohne XML-Parser, ohne Spring:

| Test-Klasse | Was getestet wird |
|---|---|
| `EpcFilterServiceTest` | EPC-Filterung: gültige/ungültige Formate, Edge Cases |
| `CbvVocabularyValidatorTest` | CBV-Validierung: alle 45 bizStep-Werte, Negativ-Fälle |
| `CbvValidationServiceTest` | CBV-Service-Verhalten |
| `EpcisXmlParserTest` | XML-Parsing: Pflichtfelder fehlen, unbekannte Event-Typen |
| `Epcis2JsonRendererTest` | JSON-Rendering: alle Felder, ILMD, Extensions |
| `InventoryProcessorServiceTest` | Bestandsberechnung: ADD/DELETE/OBSERVE Semantik |
| `InventoryQueryServiceTest` | Query-Logik |
| `InventoryRebuildServiceTest` | Rebuild-Idempotenz |
| `DigitalLinkParserTest` | URL-Parsing: gültige/ungültige GS1 Digital Link URLs |
| `SubscriptionDispatcherTest` | Subscription-Delivery |
| `OutboxProcessorTest` | Outbox-Retry-Logik |
| `CaptureEventUseCaseTest` | Use Case Orchestration (gemockte Ports) |

### Ebene 2: Integration Tests (mit Testcontainers)

Echte PostgreSQL-Datenbank — kein H2, keine Mocks:

| Test-Klasse | Was getestet wird |
|---|---|
| `CaptureControllerIntegrationTest` | Vollständige Capture-Pipeline: HTTP → DB |
| `InventoryControllerIntegrationTest` | Inventory-Abfragen nach echten Inserts |
| `ConvertEventUseCaseIntegrationTest` | Conversion-Pipeline end-to-end |
| `SubscriptionControllerIntegrationTest` | Subscription-Registrierung und -Abfrage |
| `DigitalLinkControllerIntegrationTest` | Digital Link Resolver |

**Warum echte DB?** Weil H2 nicht dasselbe ist wie PostgreSQL. JSONB-Queries, GIN-Indizes, und Flyway-Migrationen müssen gegen echte PostgreSQL getestet werden. Testcontainers macht das erschwinglich — keine manuelle DB-Einrichtung.

### Ebene 3: Fixture Tests / Golden Master

Echte EPCIS 1.2 XML-Dateien als Input, erwartetes JSON als Output:

**Standard-Fixtures (`src/test/resources/fixtures/`):**
- `object-event-add-with-ilmd.xml`
- `object-event-observe.xml`
- `object-event-delete-with-errordeclaration.xml`
- `object-event-with-forbidden-epc.xml`
- `aggregation-event-add.xml`
- `aggregation-event-delete.xml`
- `mixed-events.xml` — mehrere Event-Typen in einem Dokument

**DM-Fixtures — reale C&A Supply Chain Events (`src/test/resources/fixtures/dm-events/`):**

Diese Fixtures sind echte Geschäfts-Events der C&A Lieferkette:

| Datei | Beschreibung |
|---|---|
| `101 - DM Encoding Event.xml` | RFID-Encoding in der Druckerei |
| `102 - DM Packing Items to Boxes Event (SGTIN).xml` | Einpacken von Artikeln (SGTIN) |
| `103 - DM Packing Items to Boxes Event (GTIN + Qty).xml` | Einpacken (GTIN + Menge) |
| `105 - DM Shipping Boxes Event.xml` | Versand von Kartons |
| `106 - DM Delete Boxes Event.xml` | Löschung von Karton-Aggregationen |
| `201 - DM DC Receiving Event.xml` | DC-Wareneingang |
| `202 - DM Stock Correction Increase Event.xml` | Bestandskorrektur Erhöhung |
| `203 - DM Stock Correction Decrease Event.xml` | Bestandskorrektur Verringerung |
| `204 - DM DC Tunnel Read Event.xml` | Tunnel-RFID-Lesung im DC |
| `205 - DM DC Trolley Read Event.xml` | Trolley-Lesung im DC |
| `206 - DM Loading Event (SAP Goods Issue).xml` | Beladung (SAP Warenausgang) |
| `207 - DM Departing Event.xml` | Abfahrt vom DC |
| `208 - DM Online DC Event.xml` | Online-DC Event |
| `302 - DM Cycle Counting Event.xml` | Inventurzählung im Store |
| `303 - DM Moving Event.xml` | Umlagerung im Store |
| `304 - DM Handheld Retirement Event.xml` | Handheld-Ausbuchung |
| `305 - DM Multi-Retirement Event.xml` | Mehrfach-Ausbuchung |
| `306 - DM Observed by EAS Event.xml` | EAS-Gate-Lesung |
| `307 - DM Reserving Event.xml` | Reservierung |
| `308 - DM Unreserving Event.xml` | Reservierung aufheben |
| `309 - DM Retagging Event.xml` | Umnumerierung |
| `310 - DM Selling Event.xml` | Verkauf |
| `311 - DM Returning Event.xml` | Rückgabe |
| `312 - DM Void Sale Event.xml` | Storno |
| `313 - DM Branch Transfer Event.xml` | Filialübertragung |
| `401 - DM SSCC:SGTIN Deletion Event.xml` | Palettenauflösung |

Diese 27 DM-Fixtures decken nahezu den gesamten C&A Supply Chain Zyklus ab — von der Druckerei bis zum Store-Verkauf.

---

## 11. Das Datenbankschema: Vollständige Evolution

Jede Datenbankänderung ist eine versionierte Flyway-Migration. Niemals manuelle Änderungen.

```
V1__initial_schema.sql
   → epcis_event (id, event_type, event_time, payload JSONB, created_at)
   → epcis_quarantine (id, error_code, message, raw_payload, created_at)
   → GIN-Index auf payload für JSONB-Queries

V2__capture_audit.sql
   → capture_audit (session_id, source_id, received_at, total_received,
                    total_accepted, total_filtered, total_dropped, total_errors)

V3__inventory_tables.sql
   → epc_state (epc, current_location, biz_step, disposition, last_event_time)
   → sscc_content (sscc, child_epc)

V4__movement_history.sql
   → movement_history (epc, event_time, location, biz_step, disposition, event_id)

V5__outbox_table.sql
   → outbox (id, event_id, payload, status, created_at, processed_at)

V6__subscription_table.sql
   → subscription (id, subscriber_id, callback_url, event_type, created_at)

V7__cbv_vocabulary_table.sql
   → cbv_vocabulary (id, vocabulary_type, uri, label)

V8__cbv_quarantine_table.sql
   → cbv_quarantine (id, event_id, violated_field, violated_value, raw_payload, created_at)
```

---

## 12. Das Quarantine-Konzept: Niemals Datenverlust

Ein Event das nicht verarbeitet werden kann, wird **niemals still verworfen**. Es landet in der Quarantäne mit:
- `error_code` — maschinenlesbarer Code: `UNSUPPORTED_EVENT_TYPE`, `CBV_VIOLATION`
- `reason` — menschenlesbare Erklärung
- `raw_payload` — das Original-XML für spätere Neuverarbeitung
- `created_at` — Timestamp

Quarantinierte Events sind sichtbar über `GET /api/quarantine`.

**Recovery-Pfad:**
- `UNSUPPORTED_EVENT_TYPE`: Event-Typ implementieren → Raw-XML aus Quarantäne resubmitten
- `CBV_VIOLATION`: URI am Quellsystem korrigieren → Raw-XML resubmitten

---

## 13. Technologie-Stack und Entscheidungen

| Technologie | Entscheidung | Begründung |
|---|---|---|
| Spring Boot 4.x | ✅ | Enterprise-Standard, gute XML/JSON/JPA-Unterstützung |
| Java 17 | ✅ | Records, moderne Pattern-Matching |
| PostgreSQL + JSONB | ✅ | EPCIS 2.0 JSON nativ speichern + GIN-Index |
| Flyway | ✅ | Versionierte Datenbankmigrationen |
| Testcontainers | ✅ | Echte PostgreSQL in Integration Tests |
| H2 (local) | ✅ | Schneller lokaler Start ohne Docker |
| networknt json-schema-validator | ✅ | GS1 offizielles EPCIS 2.0 JSON Schema |
| Micrometer + Prometheus | ✅ | Operations-Metriken |
| Kafka | ❌ | Nicht gerechtfertigt — Outbox Pattern reicht |
| Microservices | ❌ | Domain zu klein — Modularer Monolith |

---

## 14. Observability: Was das System über sich selbst meldet

**Metriken (Prometheus/Micrometer):**
```
epcis.events.received      ← Alle eingehenden Events
epcis.events.converted     ← Erfolgreich konvertiert
epcis.events.quarantined   ← In Quarantäne (mit reason-Tag)
epcis.events.failed        ← Technische Fehler
epcis.inventory.processed  ← Inventory-Updates
epcis.cbv.violations       ← CBV-Verstöße
```

**Korrelations-IDs:**  
Jeder HTTP-Request bekommt eine UUID (`X-Correlation-ID` Response-Header). Alle Log-Zeilen enthalten diese ID via MDC. Ein einzelnes Event ist durch alle Log-Zeilen verfolgbar — auch bei 1000 parallelen Requests.

**Health-Endpunkte:**  
`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`

---

## 15. Wie Claude Code als Entwicklungspartner eingesetzt wurde

### Das Vorgehen

1. **CLAUDE.md als Arbeitsanweisung:** Die Architektur-Regeln, nicht-verhandelbaren Prinzipien und Coding-Standards wurden in CLAUDE.md festgehalten — sowohl für Menschen als auch als maschinenlesbare Instruktion für Claude Code.

2. **Spec-Dokumente als Phase-Input:** Für jede Phase wurde zuerst ein vollständiges Spec-Dokument geschrieben. Dieses enthielt: Scope (inkl. Negativ-Scope), Package-Struktur, DB-Schema, API-Endpunkte, Test-Anforderungen. Erst dann begann die Implementierung.

3. **Enterprise Readiness Assessment als Steuerungsinstrument:** Das Assessment vom 10. April war nicht eine Kritik — es war der priorisierte Arbeitsplan. P0-Lücken wurden sofort geschlossen, P1-P3 strukturierten die Phasen.

4. **Iterative Verbesserung:** Wenn etwas nicht funktionierte (z.B. fehlende H2-Migrationsskripte für den lokalen Profil), wurde es sofort gefixt und nicht aufgeschoben.

### Was besonders gut funktioniert hat

- Klare, präzise Spec-Dokumente → präzise Implementierung
- Architektur-Regeln in CLAUDE.md → konsistente Durchsetzung
- Phasen-Grenzen mit explizitem Negativ-Scope → kein Feature Creep
- Tests parallel zur Implementierung → sofortiges Feedback

### Was wir gelernt haben

- **Spec vor Code ist keine optionale Disziplin.** Ohne klare Spec produziert auch AI generischen, falsch abgestimmten Code.
- **Architektur-Regeln müssen explizit sein.** Eine Regel die nur im Kopf existiert, wird nicht durchgesetzt.
- **Der Negativ-Scope ist genauso wichtig wie der positive Scope.** "Das machen wir nicht in dieser Phase" verhindert endlose Sprints.
- **Enterprise Readiness Assessment early.** Lieber früh die Lücken kennen und schließen als spät.

---

## 16. Offene Punkte (bewusst nicht implementiert)

Was noch aussteht:

| Thema | Warum aufgeschoben |
|---|---|
| SGTIN State Matrix (REQ201-212) | Komplexe Geschäftslogik — braucht separate Spezifikation |
| Automatische Disaggregation bei Store Receiving | Abhängig von State Matrix |
| Batch-Verarbeitung mit Restart-Fähigkeit | Getrennte Phase |
| Stage 3: Kanonische Domain-Validierung | Interne Konsistenzprüfung des Canonical Model |
| Stage 6: Migration Reconciliation | Batch-Level Qualitätsmetriken |
| EPCIS 2.0 JSON-Input | Eigener Intake-Adapter nötig |
| SHACL Validation für JSON-LD | Für strikte JSON-LD-Konformanz |
| Authentifizierung (OAuth2/mTLS) | P0 für Produktion — noch nicht deployed |

---

## 17. Kennzahlen

| Metrik | Wert |
|---|---|
| Entwicklungszeitraum | 10.-15. April 2026 (5 Tage) |
| Phasen | 4 (Foundation + Phase 1-4) |
| Git-Commits | 22 |
| Java-Klassen | ~80 |
| Test-Klassen | 21 |
| Datenbankmigrationen (Flyway) | 8 |
| REST-Endpunkte | ~20 |
| Test-Fixtures (XML) | 33 (davon 27 reale C&A Events) |
| Dokumentations-Dateien | 8 Markdown-Dokumente + 1 ADR |
| Implementierte GS1-Standards | EPCIS 2.0, CBV 2.0, GS1 TDS 2.0 §6.3.2, GS1 Digital Link 1.1.2 |

---

## 18. Zusammenfassung: Die 5 wichtigsten Lektionen

**1. Spec vor Code.**  
Schreibe zuerst auf was du baust, dann baue es. Das spart mehr Zeit als es kostet.

**2. Architektur-Entscheidungen dokumentieren (ADRs).**  
Kurze Dokumente: Was, warum, welche Alternativen abgelehnt. In 6 Monaten weiß man sonst nicht mehr warum das System so gebaut ist.

**3. Fehler dürfen nicht still sein.**  
Ein System das Fehler verschluckt ist gefährlicher als eines das ausfällt. Im ersten Fall weißt du es nicht.

**4. Tests mit echten Abhängigkeiten (Testcontainers).**  
Mocks lügen manchmal. Echte PostgreSQL in Tests mit Testcontainers ist erschwinglich und zuverlässig.

**5. Phasen mit harten Grenzen.**  
Der Negativ-Scope ("was wir NICHT bauen") ist genauso wichtig wie der positive Scope. Ohne ihn gibt es keinen Sprint-End.

---

*Erstellt: April 2026 | Projekt: epcis-repository | Autor: Kamil Jasinski*
