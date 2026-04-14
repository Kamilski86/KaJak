# EPCIS Event Handler — System Documentation

## Übersicht

Der `epcis-event-handler` ist ein Migrations- und Konvertierungssystem für EPCIS-Events im Kontext der C&A Retail Supply Chain.

**Kernaufgabe:** Semantisch korrekte, testbare und migrationsichere Konvertierung von EPCIS 1.2 XML Events in EPCIS 2.0 JSON/JSON-LD — sowie Betrieb einer EPCIS 2.0 REST Binding konformen Query-Schnittstelle für Downstream-Systeme.

**Kontext:** Migration des EPCIS Repository von EECC (externem Partner) zu einem selbst gehosteten System bei C&A. Das neue System läuft parallel zu EECC bis zum Cut-Over (Parallelbetrieb-Strategie).

---

## Architektur

### Prinzipien

- **DDD + Hexagonale Architektur** — Domain kennt keine Frameworks, keine XML/JSON-Details
- **Modularer Monolith** — keine Microservices bis ein operativer Zwang vorliegt
- **Anti-Corruption Layer** — EPCIS 1.2 Strukturen dringen nie in das Domain-Modell ein
- **Keine stillen Fehler** — jede Filterung, jede Ablehnung wird geloggt und auditiert

### Schichten

```
┌─────────────────────────────────────────────────────────────┐
│  API Layer                                                    │
│  POST /epcis/capture/events    GET /epcis/query/events       │
│  POST /api/events/convert      GET /api/events               │
├─────────────────────────────────────────────────────────────┤
│  Application Layer                                            │
│  CaptureEventUseCase   QueryEventUseCase   ConvertEventUseCase│
├─────────────────────────────────────────────────────────────┤
│  Domain Layer                                                 │
│  EpcisEvent  ObjectEvent  AggregationEvent                   │
│  EpcFormat   FilterResult  CaptureResult                     │
│  EpcFilterService  CbvVocabularyValidator                    │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                         │
│  EpcisXmlParser  EpcisXmlValidator  Epcis2JsonRenderer       │
│  EpcisEventRepository  CaptureAuditRepository                │
│  JsonDatabaseWriter  JsonFileWriter                          │
└─────────────────────────────────────────────────────────────┘
```

### Bounded Contexts

| Context | Verantwortung |
|---|---|
| **Intake & Parsing** | EPCIS 1.2 XML lesen, XSD validieren, in Domain-Objekte deserialisieren |
| **Canonical Event Domain** | Kanonisches Event-Modell, Business-Invarianten, ubiquitäre Sprache |
| **Conversion & Rendering** | Legacy → Canonical → EPCIS 2.0 JSON/JSON-LD |
| **Validation & Conformance** | Strukturelle, semantische, CBV- und Schema-Validierung |
| **Migration Orchestration** | Batch, Restartbarkeit, Idempotenz, Reconciliation |
| **Observability & Audit** | Strukturiertes Logging, Metriken, Event-Traceability |

---

## Technology Stack

| Komponente | Technologie |
|---|---|
| Framework | Spring Boot 4.x |
| Sprache | Java 17 (Ziel), läuft auf Java 26 |
| Datenbank | PostgreSQL 18 (Produktion), H2 (local-Profil) |
| ORM | Hibernate / Spring Data JPA |
| Migrationen | Flyway |
| XML-Parsing | JAXP DOM (XXE-gehärtet) |
| JSON | Jackson 3.x |
| Schema-Validierung | networknt json-schema-validator |
| Metriken | Micrometer + Prometheus |
| Tests | JUnit 5, Mockito, Testcontainers, AssertJ |
| Build | Maven |

---

## Datenfluss

### EPCIS 1.2 XML → EPCIS 2.0 JSON (Conversion Pipeline)

```
Client XML
    │
    ▼
EpcisXmlValidator      ← XSD-Validierung (EPCglobal-epcis-1_2.xsd)
    │ EpcisValidationException → HTTP 400 / Quarantine
    ▼
EpcisXmlParser         ← DOM-Parsing, XXE-gesichert
    │ EpcisParsingException → Quarantine
    ▼
CbvVocabularyValidator ← CBV URI Validierung
    │ CbvValidationException → Quarantine
    ▼
EpcFilterService       ← REQ101.1 + REQ216: EPC-Format-Filterung
    │ Gefilterte EPCs: WARN-Log, FilterResult
    ▼
Epcis2JsonRenderer     ← Canonical → EPCIS 2.0 JSON
    │
    ▼
Epcis2JsonSchemaValidator ← GS1 JSON Schema Validierung
    │
    ├── JsonDatabaseWriter  → epcis_event (PostgreSQL)
    └── JsonFileWriter      → ./output/events/*.json (Audit-Datei)
```

### Capture Pipeline (Phase 1)

```
POST /epcis/capture/events
X-EPCIS-Source-ID: STORE-DE-001
Content-Type: application/xml

    │
    ▼
CaptureController
    │ fehlendes Header → MissingRequestHeaderException → 400
    ▼
CaptureEventUseCase
    ├── XSD-Validierung
    ├── XML-Parsing
    ├── Pro Event: EpcFilterService
    │       ├── Akzeptiert → render + DB + Datei
    │       └── Dropped   → WARN-Log + CaptureResult.errors
    ├── CaptureAuditRepository.save()
    └── CaptureResult → HTTP 201
```

---

## Was passiert bei JSON-Input?

Das System verarbeitet **ausschließlich EPCIS 1.2 XML** als Input.

| Szenario | Ergebnis |
|---|---|
| `Content-Type: application/json` an `/epcis/capture/events` | **HTTP 415 Unsupported Media Type** — Spring MVC lehnt vor Controller-Aufruf ab |
| `Content-Type: application/xml` mit JSON-Body | **HTTP 400** — `EpcisXmlValidator` wirft `EpcisValidationException` (SAX-Parse-Fehler) |
| Gültiger XML-Body mit JSON-ähnlichem Inhalt | **HTTP 400** — XSD-Validierung schlägt fehl |

**EPCIS 2.0 JSON-Input** (für spätere Phasen wenn C&A das neue EPCIS 2.0 System betreibt) würde einen eigenen Intake-Adapter benötigen, der:
- `Content-Type: application/json` / `application/ld+json` akzeptiert
- Direkt in das kanonische Domain-Modell deserialisiert (kein XML-Umweg)
- Die bestehende Filter-, Render- und Persistenz-Pipeline nutzt

Diese Erweiterung ist für Phase 3+ geplant (Downstream-Integrationen).

---

## API-Endpunkte

### Legacy Conversion API (`/api/events`)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/events/convert` | EPCIS 1.2 XML → EPCIS 2.0 JSON (vollständige Pipeline mit CBV + Schema-Validierung) |
| `GET` | `/api/events` | Paginierte Suche nach Events |
| `GET` | `/api/events/{id}` | Event per DB-ID (Long) |

### Capture API (`/epcis/capture`) — Phase 1

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/epcis/capture/events` | Events empfangen, EPC-filtern, speichern. Header `X-EPCIS-Source-ID` Pflicht. |

### Query API (`/epcis/query`) — Phase 1, EPCIS 2.0 REST Binding

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/epcis/query/events` | SimpleEventQuery mit EPCIS 2.0 Parametern |
| `GET` | `/epcis/query/events/{eventID}` | Einzelnes Event per EPCIS eventID (URL-encoded) |

### Observability (`/actuator`)

| Pfad | Beschreibung |
|---|---|
| `/actuator/health` | Health + Readiness |
| `/actuator/metrics` | Micrometer Metriken |
| `/actuator/prometheus` | Prometheus Scrape-Endpunkt |

---

## Domain-Modell

### Aggregate / Entities

| Klasse | Beschreibung |
|---|---|
| `EpcisEvent` | Abstrakte Basisklasse — alle gemeinsamen Felder (When/Where/Why) |
| `ObjectEvent` | EPCIS ObjectEvent — epcList, quantityList, ILMD |
| `AggregationEvent` | EPCIS AggregationEvent — parentID, childEPCs, childQuantityList |

### Value Objects

`EventTime`, `Action`, `BusinessTransaction`, `QuantityElement`, `Source`, `Destination`, `ErrorDeclaration`, `IlmdPayload`, `ExtensionPayload`, `EpcFormat`, `FilterResult`, `CaptureResult`

### Domain Services

| Klasse | Verantwortung |
|---|---|
| `EpcFilterService` | REQ101.1 + REQ216: EPC-Format-Filterung |
| `CbvVocabularyValidator` | CBV URI Validierung (bizStep, disposition, etc.) |
| `Epcis2JsonSchemaValidator` | GS1 EPCIS 2.0 JSON Schema Validierung |

---

## Datenbankschema

### `epcis_event`

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `event_type` | VARCHAR(50) | "ObjectEvent" / "AggregationEvent" |
| `event_time` | TIMESTAMPTZ | Zeitstempel des Events |
| `payload` | JSONB | Vollständiges EPCIS 2.0 JSON-Objekt |
| `created_at` | TIMESTAMPTZ | Eingangszeit in das System |

### `quarantine`

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | BIGINT | PK |
| `error_code` | VARCHAR | UNSUPPORTED_EVENT_TYPE / CBV_VIOLATION / etc. |
| `message` | TEXT | Fehlerbeschreibung |
| `raw_payload` | TEXT | Original-XML |
| `created_at` | TIMESTAMPTZ | Quarantäne-Zeitpunkt |

### `capture_audit`

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | BIGINT | PK |
| `session_id` | VARCHAR(36) | UUID der Capture-Session |
| `source_id` | VARCHAR(100) | Datenquelle (z.B. "STORE-DE-001") |
| `received_at` | TIMESTAMPTZ | Eingangszeit |
| `total_received` | INT | Anzahl Events im XML |
| `total_accepted` | INT | Gespeicherte Events |
| `total_filtered` | INT | Events mit gefilterten EPCs |
| `total_dropped` | INT | Komplett abgelehnte Events |
| `total_errors` | INT | Verarbeitungsfehler |

---

## Konfiguration

### Pflichtfelder

```yaml
spring:
  datasource:
    url: jdbc:postgresql://...
    username: ...
    password: ...

epcis:
  output:
    directory: ./output/events        # Ausgabeverzeichnis für JSON-Audit-Dateien
  schema:
    path: classpath:xsd/EPCglobal-epcis-1_2.xsd
```

### Capture-Konfiguration

```yaml
epcis:
  capture:
    max-events-per-request: 1000      # Maximale Events pro XML-Dokument
    max-xml-size-kb: 10240            # Maximale XML-Größe (10 MB)
    audit-enabled: true               # Capture-Audit in DB schreiben
    file-writer-enabled: true         # JSON-Dateien schreiben
```

---

## Lokale Entwicklung

```bash
# PostgreSQL starten (Docker)
docker run -d -p 5432:5432 -e POSTGRES_DB=epcis -e POSTGRES_USER=epcis \
  -e POSTGRES_PASSWORD=epcis postgres:16-alpine

# Anwendung starten
./mvnw spring-boot:run

# Tests ausführen (benötigt Docker für Testcontainers)
./mvnw test
```

---

## Phasen-Roadmap

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | Capture Service, EPC-Filter, Query API, Capture Audit | ✅ Implementiert |
| Phase 2 | SGTIN State Matrix, REQ201-212 (Business Logic) | Geplant |
| Phase 3 | Downstream-Integrationen (GSPM, DWH, ERP, EWM), Subscription Query | Geplant |
