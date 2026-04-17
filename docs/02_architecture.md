# Architecture: EPCIS Event Handler

## Overview

The EPCIS Event Handler converts EPCIS 1.2 XML documents into EPCIS 2.0 JSON/JSON-LD.
It is designed as a **migration-safe, auditable conversion service** — not a generic EPCIS relay.
Every conversion is deterministic, idempotent, and fully traceable.

## Architectural Style

**DDD + Hexagonal Architecture (Ports & Adapters) + Modular Monolith**

```
┌────────────────────────────────────────────────────────────────┐
│  Inbound Adapters (API layer)                                  │
│  EpcisEventController  QuarantineController                    │
└────────────────────┬───────────────────────────────────────────┘
                     │ calls
┌────────────────────▼───────────────────────────────────────────┐
│  Application Layer (Use Cases)                                 │
│  ConvertEventUseCase                                           │
│  Depends ONLY on port interfaces — no infra imports            │
└──┬──────────────┬────────────────┬────────────────┬────────────┘
   │ EventParser  │ EventValidator │ EventRenderer  │ EventStore
   │ EventAudit   │ QuarantineStore│                │
┌──▼──────────────▼────────────────▼────────────────▼────────────┐
│  Domain Layer                                                  │
│  EpcisEvent (ObjectEvent, AggregationEvent)                    │
│  Value Objects: Source, Destination, ErrorDeclaration,         │
│                 IlmdPayload, ExtensionPayload, QuantityElement  │
│  Domain Services: CbvVocabularyValidator,                      │
│                   Epcis2JsonSchemaValidator                     │
└────────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────────┐
│  Infrastructure / Outbound Adapters                            │
│  EpcisXmlParser (XML → Domain)                                 │
│  EpcisXmlValidator (XSD validation)                            │
│  Epcis2JsonRenderer (Domain → JSON/JSON-LD)                    │
│  JsonDatabaseWriter (JPA → PostgreSQL)                         │
│  JsonFileWriter (audit file)                                   │
│  DatabaseQuarantineStore (quarantine persistence)              │
└────────────────────────────────────────────────────────────────┘
```

## Module Boundaries

| Package | Responsibility | Allowed Dependencies |
|---|---|---|
| `domain.model` | Canonical event model, value objects | None (pure Java) |
| `domain.service` | CBV validation, JSON Schema validation | `domain.model` only |
| `application` | Use case orchestration | `domain.*`, `application.port` |
| `application.port` | Outbound port interfaces | `domain.model` |
| `infrastructure.*` | Adapters implementing ports | `application.port`, `domain.*`, frameworks |
| `api` | REST controllers | `application`, `infrastructure.persistence` |
| `config` | Spring config | Frameworks |

**Rule**: The `application` and `domain` layers must never import from `infrastructure` or `api`.

## Conversion Pipeline

```
  HTTP POST /api/events
       │
       ▼
  [Stage 1] XSD Structural Validation (EpcisXmlValidator)
       │  fail → 400 Bad Request
       ▼
  [Stage 2] XML Parsing → Canonical Domain Model (EpcisXmlParser)
       │  fail (unsupported type) → QUARANTINE + 422
       ▼
  [Stage 5] CBV Vocabulary Validation (CbvVocabularyValidator)
       │  fail (invalid CBV URI) → QUARANTINE + 422
       ▼
  [Render]  Domain → EPCIS 2.0 JSON (Epcis2JsonRenderer)
       │
       ▼
  [Persist] JSON saved to PostgreSQL (JsonDatabaseWriter)
  [Audit]   JSON written to file    (JsonFileWriter)
       │
       ▼
  [Stage 4] JSON Schema Validation against GS1 EPCIS 2.0 schema
       │  fail → 500 (renderer bug, not client error)
       ▼
  200 OK — EpcisDocumentDto
```

> Stage numbering follows the Validation Strategy document. Stages 3 and 6
> (canonical domain validation and migration reconciliation) are planned for a future release.

## Quarantine

Events that cannot be safely converted are written to the `epcis_quarantine` table rather than
discarded. Each quarantine record carries:

- `error_code` — machine-readable classification (`UNSUPPORTED_EVENT_TYPE`, `CBV_VIOLATION`)
- `reason` — human-readable explanation
- `raw_fragment` — the original XML for reprocessing
- `created_at` — timestamp

Quarantined events are visible via `GET /api/quarantine`.

## Persistence

- **`epcis_event`** — stores every successfully converted event as JSONB + metadata.
  GIN index on `payload` enables efficient JSONB queries (by action, bizStep, EPC, GLN, etc.).
- **`epcis_quarantine`** — stores every rejected event fragment with error classification.
- Schema is managed by **Flyway** versioned migrations (`V1`, `V2`).

## Observability

- **Spring Actuator** exposes `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`.
- **Micrometer counters** track: `epcis.events.received`, `epcis.events.converted`,
  `epcis.events.quarantined`, `epcis.events.failed`.
- **MDC correlation ID**: every request carries a `correlationId` in all log lines
  (set by `CorrelationIdFilter`; echoed in the `X-Correlation-ID` response header).

## Anti-Corruption Layer

EPCIS 1.2 XML structures (partner-specific, namespace-polluted DOM trees) are parsed by
`EpcisXmlParser` and mapped into the canonical domain model. The domain model has no
knowledge of XML, Jackson, or any framework. This ACL boundary ensures that:

- Legacy structural quirks are isolated in the infrastructure layer.
- Domain business rules are expressed purely in terms of the canonical model.
- New event types or format versions can be added without touching business logic.

## Technology Stack

| Concern | Technology |
|---|---|
| Language / Runtime | Java 17, Spring Boot 4.0 |
| Web | Spring MVC (embedded Tomcat) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Schema migrations | Flyway |
| XML parsing | W3C DOM (XXE-hardened) |
| JSON | Jackson 2 with JavaTimeModule |
| JSON Schema validation | `networknt/json-schema-validator` (GS1 EPCIS 2.0 Draft-07) |
| Metrics | Micrometer + Prometheus |
| Testing (unit) | JUnit 5, AssertJ, Mockito |
| Testing (integration) | Testcontainers (PostgreSQL) |
