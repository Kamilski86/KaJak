# ADR-001: Hexagonal Architecture + Modular Monolith

**Status**: Accepted  
**Date**: 2024-01  
**Deciders**: Kamil Jasinski

---

## Context

The EPCIS Event Handler must convert EPCIS 1.2 XML events to EPCIS 2.0 JSON/JSON-LD
in a migration-safe, auditable, and testable way. The system needs to:

- Isolate parsing/rendering concerns from business logic.
- Allow the domain model to express EPCIS semantics without contamination from XML or JSON.
- Remain testable at the unit level without infrastructure setup.
- Support future extension (new event types, additional validation stages, batch processing)
  without broad refactoring.

---

## Decision

Adopt **Hexagonal Architecture (Ports & Adapters)** organized as a **Modular Monolith**.

### Module structure

```
com.canda.epcis
├── domain/          — Pure Java; no framework annotations; no I/O
│   ├── model/       — Canonical event model, value objects
│   └── service/     — Domain services (CBV validation, JSON Schema validation)
├── application/     — Use cases; depends only on domain + port interfaces
│   └── port/        — Outbound port interfaces (EventParser, EventRenderer, ...)
├── infrastructure/  — Adapters that implement the ports; Spring components
│   ├── xml/         — EpcisXmlParser, EpcisXmlValidator
│   ├── json/        — Epcis2JsonRenderer, DTOs
│   ├── persistence/ — JPA entities, repositories, DatabaseQuarantineStore
│   └── web/         — CorrelationIdFilter
├── api/             — Inbound HTTP adapters (REST controllers, exception handler)
└── config/          — Spring configuration beans
```

### Port interfaces

Outbound ports are plain Java interfaces in `application.port`:

| Interface | Implemented by |
|---|---|
| `EventParser` | `EpcisXmlParser` |
| `EventValidator` | `EpcisXmlValidator` |
| `EventRenderer` | `Epcis2JsonRenderer` |
| `EventStore` | `JsonDatabaseWriter` |
| `EventAuditWriter` | `JsonFileWriter` |
| `QuarantineStore` | `DatabaseQuarantineStore` |

The use case (`ConvertEventUseCase`) depends only on these interfaces and the domain model.
It has zero imports from `infrastructure.*`.

### Anti-Corruption Layer

`EpcisXmlParser` acts as the ACL between EPCIS 1.2 XML and the canonical domain model.
It translates DOM tree structures into value objects without polluting the domain with XML API types.

---

## Rationale

| Concern | Why this decision |
|---|---|
| Testability | Domain and application layers can be fully unit-tested without Spring, DB, or XML on the classpath. |
| Standards conformance | Domain model expresses EPCIS semantics directly; XML/JSON are transport details. |
| Replaceability | XML parser, JSON renderer, or DB adapter can be swapped without touching business logic. |
| Auditability | The pipeline is explicit and ordered; each stage has a clear contract. |
| Anti-corruption | 1.2 quirks (namespace pollution, flat DOM, attribute-based type discrimination) are contained in the infrastructure layer. |

---

## Rejected Alternatives

### Direct XML → JSON mapping (single mapper class)

Rejected. Would create a god class combining parsing, mapping, and rendering.
Untestable at the field level. CBV and schema validation would have no natural home.
EPCIS semantic invariants would be implicit in the transformation rather than explicit in a domain model.

### Microservices

Rejected for the initial release. The domain is small and the bounded contexts are tightly coupled
in the conversion pipeline. Operational complexity is not justified. The modular monolith structure
allows extraction to microservices if a hard operational reason arises later.

---

## Consequences

**Positive**:
- Domain model is portable and re-usable (e.g., could serve a SHACL validator, a diff tool, or a reconciliation engine without changes).
- Infrastructure concerns are replaceable independently.
- Each conversion stage is independently testable.

**Negative / Trade-offs**:
- More files/classes than a simple controller-service-repo stack.
- Port interfaces add a thin indirection layer that requires team familiarity with hexagonal patterns.
- Lombok `@SuperBuilder` on polymorphic domain models requires care (type inference limitation addressed in tests by using concrete builders directly).
