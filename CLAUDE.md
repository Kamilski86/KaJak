# CLAUDE.md — EPCIS Event Handler

## Mission
For this project, you act as Senior Developer, Senior Analyst, and Senior Architect.
The objective is the semantically correct, testable, and migration-safe conversion of EPCIS events from EPCIS 1.2 into EPCIS 2.0 JSON/JSON-LD.

## Project Context
- This project is part of a full EPCIS migration from an external partner into an internal enterprise system.
- In scope for the first production-grade release: `ObjectEvent`, `AggregationEvent`.
- Source: EPCIS 1.2; target: EPCIS 2.0 JSON/JSON-LD.
- The system is a robust migration and conversion application with auditability, not a generic EPCIS playground.

## Non-Negotiable Goals
- Always preserve business semantics; never perform a purely syntactic transformation.
- Preserve the EPCIS dimensions What / When / Where / Why / How.
- Never perform silent, lossy conversion.
- Never invent field values, CBV values, identifiers, or business semantics.
- If data is unclear, invalid, or cannot be mapped safely: fail loudly, document the reason, and route it to quarantine.
- Every conversion must be deterministic, idempotent, and traceable.

## Target Architecture
- DDD + Hexagonal Architecture + Modular Monolith first.
- Strictly separate: Domain, Application/Use Cases, Adapters, Infrastructure.
- The domain must not depend on XML, JSON, HTTP, database, or framework details.
- Legacy partner structures must never leak directly into the domain.
- Use an Anti-Corruption Layer between EPCIS 1.2 input and the internal canonical model; prefer a modular monolith unless there is a hard operational reason for microservices.
- If the repository is greenfield and no stack is mandated, prefer a JVM stack with strong XML / JSON / schema / RDF support.

## Bounded Contexts
### 1) Intake & Parsing
- Read EPCIS 1.2 documents or event payloads.
- Enforce secure XML processing.
- Deserialize into legacy transport models.
- Optionally validate input against XSD.

### 2) Canonical Event Domain
- Canonical event model decoupled from transport formats.
- Business invariants.
- Ubiquitous language.
- Representation of event semantics independent of XML or JSON.

### 3) Conversion & Rendering
- Map legacy models into the canonical model.
- Render from the canonical model into EPCIS 2.0 JSON/JSON-LD.
- Keep transformation logic pure and deterministic.

### 4) Validation & Conformance
- Structural validation.
- Business validation.
- CBV / identifier / URI validation.
- Output validation against official GS1 artifacts.

### 5) Migration Orchestration & Reconciliation
- Batch processing, restartability, idempotency.
- Statistics, diffing, reconciliation, and audit reports.

### 6) Observability & Audit
- Structured logs.
- Migration metrics.
- Per-event traceability.
- Error classification.

## Ubiquitous Language
- `LegacyEvent` = incoming EPCIS 1.2 event.
- `CanonicalEvent` = internal business target model.
- `ConvertedEvent` = rendered EPCIS 2.0 JSON/JSON-LD event.
- `ConformanceValidation` = validation against official EPCIS / CBV rules.
- `MigrationBatch` = business-relevant processable batch.
- `QuarantineItem` = event that must not be migrated automatically.
- `MappingDecision` = explicitly documented rule for field or semantic mapping.

## Domain Model
### Aggregates / Entities
- `CanonicalEvent`, `CanonicalObjectEvent`, `CanonicalAggregationEvent`, `MigrationBatch`, `ValidationResult`, `ConversionReport`

### Value Objects
- `EventTime`, `EventTimeZoneOffset`, `EventId`, `BusinessStep`, `Disposition`, `ReadPoint`, `BusinessLocation`, `ParentId`, `EpcIdentifier`, `QuantityElement`, `BusinessTransaction`, `Source`, `Destination`, `ErrorDeclaration`, `IlmdPayload`, `ExtensionPayload`

### Domain Services
- `LegacyEventParser`, `LegacyToCanonicalMapper`, `CanonicalToEpcis2Renderer`, `ConformanceValidator`, `VocabularyValidator`, `ReconciliationService`

## Conversion Rules
- Always convert through the canonical model; never map directly from XML to JSON inside a monolithic mapper class.
- Strictly distinguish instance-level identifiers (`epcList`, `childEPCs`) from class-level or quantity-based data (`quantityList`, `childQuantityList`).
- Preserve `eventTime`, `recordTime`, `eventTimeZoneOffset`, `bizStep`, `disposition`, `readPoint`, `bizLocation`, `bizTransactionList`, `sourceList`, `destinationList`, `eventID`, `errorDeclaration`, ILMD, and valid extensions whenever they can be mapped correctly.
- Use only officially valid EPCIS / CBV field names and semantics.
- For EPCIS 2.0, use only structures backed by official GS1 schemas, context documents, and examples.
- Do not artificially generate new EPCIS 2.0 capabilities unless there is an explicit business rule to do so.
- In particular, do not implicitly infer `persistentDisposition`, `AssociationEvent`, `TransformationEvent`, `TransactionEvent`, or any new 2.0 semantics.
- If `eventID` is missing in the legacy event, an internal technical trace identifier may be generated, but it must never be emitted as an official business EPCIS `eventID` unless this is explicitly justified.
- Extensions must be handled in a namespace-safe and context-safe way.
- If an extension or ILMD structure cannot be transferred cleanly into EPCIS 2.0, stop automatic migration and document the gap.

## Event-Specific Rules
### ObjectEvent
- Support `epcList` and `quantityList` correctly.
- Respect action semantics (`ADD`, `OBSERVE`, `DELETE`).
- Preserve object, location, and business process semantics.
- Handle sensor-specific cases only if they are actually present in the input and business-validated.

### AggregationEvent
- Support `parentID`, `childEPCs`, and `childQuantityList` correctly.
- DELETE semantics with empty child lists are only allowed where the standard permits it.
- Aggregation logic must not be mixed with association semantics.
- `parentID` and child structures must be validated as one consistent business unit.

## Validation Strategy
### Stage 1: Safe Technical Input Validation
- XML parser hardening: no XXE, no unsafe external entities, no blind trust in parser defaults.
- Validate well-formedness.
- Optional but preferred: EPCIS 1.2 XSD validation for inbound payloads.

### Stage 2: Legacy Semantic Validation
- Mandatory fields, cardinalities, event-type-specific rules, action-dependent rules, identifier types.
- Lists must not be implicitly normalized to empty if that changes semantics.

### Stage 3: Canonical Domain Validation
- The canonical model must be business-valid in its own right; do not allow contradictory states or half-mapped events.

### Stage 4: EPCIS 2.0 Structural Validation
- Validate against the official GS1 EPCIS 2.0 JSON Schema.
- Validate JSON-LD compatibility against the official GS1 SHACL definition whenever JSON-LD is generated or checked.
- Prefer official or well-established libraries over custom-built validators.

### Stage 5: CBV and Identifier Validation
- Explicitly validate allowed CBV values, URI shapes, and vocabulary fields such as `bizStep`, `disposition`, `source`, `destination`, `errorDeclaration.reason`, and related terms.

### Stage 6: Migration Validation
- Input count = processed + rejected + quarantined events.
- Produce reconciliation and quality metrics.
- Return machine-readable error codes and human-readable explanations.

## Documentation Obligations (project-specific)
- `docs/mapping-matrix.md` for field mapping 1.2 → Canonical → 2.0.
- `docs/validation-strategy.md` for all validation stages.
- `docs/migration-runbook.md` for batch operations and error handling.

## Prioritized Delivery Order
1. Lock down target architecture and module boundaries.
2. Define the canonical domain model.
3. Build the secure EPCIS 1.2 parser.
4. Implement mapping from 1.2 → Canonical.
5. Implement rendering from Canonical → EPCIS 2.0 JSON/JSON-LD.
6. Integrate official schema / SHACL / CBV validation.
7. Build golden fixtures and negative tests.
8. Add `MigrationBatch`, quarantine, reconciliation, and reporting.
9. Complete observability and the operational runbook.

## Decision Rules Under Uncertainty
- Standards conformance over convenience.
- Business traceability over silent data rescue.
- Canonical domain clarity over direct transport-format optimization.
- Document assumptiaons, write tests, and mark uncertainty explicitly.