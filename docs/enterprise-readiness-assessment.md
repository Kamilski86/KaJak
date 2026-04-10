# Enterprise Readiness Assessment — EPCIS Event Handler
**Target deployment: C&A | Assessment date: 2026-04-10**

---

## What Currently Exists

A working Spring Boot 4.0.5 / Java 17 proof-of-concept with:
- XXE-hardened XML parser + EPCIS 1.2 XSD validation
- Domain model: `EpcisEvent`, `ObjectEvent`, `AggregationEvent`
- JSON renderer producing EPCIS 2.0 JSON envelope
- Single `ConvertEventUseCase`, REST API (convert + search), PostgreSQL + JSONB persistence, file output
- 3 test classes (basic happy-path + a few negative cases)

This is a solid foundation. Nothing below is a criticism of the code quality — the code is clean. It is a gap list against enterprise deployment at C&A.

---

## 1. Senior Software Developer

### Architecture Violations

**Hexagonal architecture is declared but not enforced.**
- `ConvertEventUseCase` (`application/ConvertEventUseCase.java:8–11`) directly imports infrastructure classes (`EpcisXmlParser`, `EpcisXmlValidator`, `Epcis2JsonRenderer`, `JsonFileWriter`, `JsonDatabaseWriter`). The application layer must only speak to ports (interfaces); adapters implement them.
- `EpcisEventController` (`api/EpcisEventController.java:6`) imports `EpcisEventRepository` and `EpcisEventEntity` — the API adapter is bypassing the application layer and directly hitting the persistence adapter.

**No Anti-Corruption Layer and no LegacyEvent transport model.**
`EpcisXmlParser` maps directly from DOM to the canonical domain model. The CLAUDE.md explicitly requires a `LegacyEvent` transport model between parser output and the canonical domain. There is no separate `LegacyObjectEvent` / `LegacyAggregationEvent` that gets translated by `LegacyToCanonicalMapper`. The parser, mapper, and canonical model are fused into one step.

### Domain Model Incompleteness

The following are defined in the domain model spec but missing from the code:

| Missing | Where Needed |
|---|---|
| `sourceList` / `destinationList` on `EpcisEvent` | Mandatory EPCIS fields |
| `ErrorDeclaration` value object | Correction events |
| `IlmdPayload` value object | Instance-level master data |
| `ExtensionPayload` value object | Namespace-safe extensions |
| `MigrationBatch` aggregate | Batch orchestration |
| `ValidationResult` / `ConversionReport` aggregates | Audit trail |
| `QuarantineItem` entity | Error routing |
| `MappingDecision` entity | Documented mapping rules |

All domain fields (`bizStep`, `disposition`, `readPoint`, `bizLocation`, `eventId`) are raw `String` — no typed value objects with validation. Violations are silently accepted (e.g. an empty `bizStep` string is structurally valid).

### Silent Failures

- `EpcisXmlParser.parseTime()` (`xml/EpcisXmlParser.java:185–190`): parses timestamp, logs a warning on failure, returns `null`. An event with a null `eventTime` is accepted and stored. This is a silent, lossy conversion — violates the non-negotiable rules.
- `Epcis2JsonRenderer.commonFields()` (`json/Epcis2JsonRenderer.java:65`): when `eventId` is null, a `UUID` is generated and emitted as the EPCIS `eventID`. CLAUDE.md forbids this: a generated ID must never be emitted as a business `eventID` without explicit justification.
- Unsupported event types in the parser (`EpcisXmlParser.java:71`) are silently skipped with `log.warn`. They should be routed to quarantine.

### Infrastructure / Configuration

- `ddl-auto: update` in `application.yml:12` — must never run in production. Requires Flyway or Liquibase with explicit versioned migrations.
- `com.example` package naming — must be replaced with the enterprise namespace (e.g. `de.c-and-a.epcis` or equivalent).
- No `Flyway`/`Liquibase` dependency.
- No `spring-boot-starter-actuator`.
- `EpcisEventEntity` has no `event_id` (business UUID) column, no `action`, no `biz_step`, no `disposition` — all search is done via JSONB expressions at query time. This works but breaks indexed search at scale and prevents reconciliation queries.
- The JSONB search query (`EpcisEventRepository.java:15–52`) contains a native SQL query that will not be type-safe at refactoring time and is untested in isolation.

### Missing Use Cases

- No `ProcessBatchUseCase`
- No `QuarantineEventUseCase`
- No `ReconcileUseCase`
- No `ValidateCanonicalEventUseCase`
- No idempotency check (re-submitting the same event creates a duplicate row)

---

## 2. Senior Business Architect

### Bounded Contexts Not Implemented

The CLAUDE.md defines six bounded contexts. Only two are partially implemented:

| Bounded Context | Status |
|---|---|
| Intake & Parsing | Partial — parser exists, no separate LegacyEvent model |
| Canonical Event Domain | Partial — domain model exists but missing ~8 entities/VOs |
| Conversion & Rendering | Partial — renderer exists but no mapper layer |
| Validation & Conformance | Minimal — only XSD stage 1; stages 2–6 are absent |
| Migration Orchestration & Reconciliation | Not started |
| Observability & Audit | Not started |

### Module Structure

There are no enforced module boundaries. Everything lives in a single Maven module. At enterprise scale with multiple teams, the bounded contexts should be enforced either as Maven modules or via ArchUnit rules. Without this, drift is inevitable.

### Missing Operational Concerns

- No batch job interface. Currently only synchronous REST. For a full C&A migration (volume will be high), a batch processing interface (file drop, queue consumer) is required.
- No restart/resume capability. A failed half-processed batch leaves no cursor.
- No dead-letter / quarantine storage modeled in the persistence layer.
- No reconciliation report output (input count = processed + quarantined + rejected).
- The system has no way to answer: "Was event X from partner Y successfully migrated?"

### Deliverable Traceability

Per CLAUDE.md, the following documents are mandatory and do not exist:
- `docs/mapping-matrix.md`
- `docs/validation-strategy.md`
- `docs/migration-runbook.md`
- `docs/architecture.md`
- `docs/adr/` (any ADR)

---

## 3. Senior Security Specialist

### Critical: No Authentication or Authorization

The REST API has zero authentication. `POST /api/events/convert` accepts arbitrary XML from any caller. At C&A, this must be protected with mTLS or OAuth 2.0 / API key with role-based access before any deployment in a connected network.

### Information Disclosure

`GlobalExceptionHandler.java:31–34` returns `"Internal error: " + ex.getMessage()` for all unhandled exceptions. In production, internal exception messages (including stack trace fragments, DB errors, file paths) must never be returned to callers. Replace with a generic error reference ID; log the details internally.

### XML Bomb / Payload Size

No maximum request size is configured. A `spring.servlet.multipart.max-file-size` or `server.tomcat.max-http-form-post-size` limit is absent from `application.yml`. A billion-laughs attack or very large XML document will consume all memory.

### Secrets in Configuration

`application.yml:6–9` contains a hardcoded DB URL with the developer's personal username. Production credentials must be injected via environment variables or a secrets manager (HashiCorp Vault / Kubernetes secrets). This file is committed to git.

### Schema Access

`EpcisXmlValidator.java:27`: `ACCESS_EXTERNAL_SCHEMA` is set to `"file"` — this permits local filesystem reads. In a containerized deployment, confirm no file-system path traversal is possible through schema imports in attacker-controlled XSD references.

### Dependency Supply Chain

No SBOM generation, no `dependency-check` (OWASP) or Dependabot integration in the build. `pom.xml` has no `<licenses>` section and no dependency audit plugin. Spring Boot 4.0.5 dependencies need a scheduled CVE scan.

### Audit Trail Integrity

Events are stored in PostgreSQL. There is no append-only guarantee, no row-level audit log, and no tamper detection. For a regulated supply chain (C&A tracks EPCIS for compliance), the audit trail must be tamper-evident. Consider an append-only write model or a separate audit table with an immutable insert trigger.

### Missing Controls

- No rate limiting on the convert endpoint
- No TLS configuration documented
- No CORS policy defined
- No `Content-Security-Policy` / security headers on responses
- No container image scanning in CI

---

## 4. Senior Tester

### Coverage Is a Proof-of-Concept Baseline

Current test count: 3 classes, ~10 test cases. For enterprise deployment, the following are required:

**Unit tests missing:**
- All value object validation (what does an invalid `bizStep` URI do?)
- `EpcisXmlParser` negative: missing `eventTime`, missing `action`, missing `epcList` on ObjectEvent, empty parentID on AggregationEvent
- `Epcis2JsonRenderer`: null field propagation, missing mandatory fields, event with ILMD
- `ConvertEventUseCase` unit tests with mocked ports (currently untestable because it directly instantiates infrastructure)

**Golden-master / fixture tests missing:**
There are no fixture XML files in `src/test/resources`. There should be at minimum:
- A representative ObjectEvent ADD/OBSERVE/DELETE fixture
- A representative AggregationEvent ADD/DELETE (with empty childEPCs) fixture
- A fixture with `sourceList`, `destinationList`, `errorDeclaration`, ILMD
- Partner-specific C&A examples

**Conformance tests missing (Stage 4–5):**
- No GS1 EPCIS 2.0 JSON Schema validation test (the rendered output is never validated against the official schema)
- No CBV vocabulary validation tests (`bizStep`, `disposition` URIs are passed through unchecked)
- No SHACL validation tests for JSON-LD

**Negative / quarantine tests missing:**
- Event with unrecognized event type → must quarantine, not silently skip
- Event with null `eventTime` → must fail loudly, not null-store
- Event with invalid `epcClass` URI in `quantityList` → must fail
- Duplicate event re-submission → must be idempotent, not insert a second row

**Integration tests missing:**
- No Spring Boot test (`@SpringBootTest`) exercising the full pipeline end-to-end against a real (Testcontainers) PostgreSQL
- No test asserting that a stored JSONB payload can be queried back by EPC

**Batch / resilience tests missing:**
- Large XML document with 10,000 events → memory behavior
- Partial failure mid-batch → is the transaction rolled back cleanly?

---

## 5. Senior Business Analyst

### Mapping Completeness

The EPCIS 1.2 → EPCIS 2.0 mapping is incomplete. The following fields are parsed by the standard but not handled:

| EPCIS 1.2 Field | Status in Code |
|---|---|
| `sourceList` / `source` | Not parsed, not mapped, not rendered |
| `destinationList` / `destination` | Not parsed, not mapped, not rendered |
| `errorDeclaration` | Not parsed, not mapped, not rendered |
| `ilmd` | Not parsed, not mapped, not rendered |
| Extensions (custom namespaces) | Silently dropped |
| `eventID` (absent) | UUID silently generated and emitted as business ID |
| `recordTime` | Parsed but never validated for temporal consistency with `eventTime` |

There is no `docs/mapping-matrix.md` documenting:
- Which fields are mapped, with what transformation rule
- Which fields are dropped (and why)
- Which fields cause quarantine
- Which EPCIS 2.0 field names differ from EPCIS 1.2 (e.g. `bizTransactionList` type attribute vs 2.0 object structure)

### CBV / Vocabulary Validation

No CBV validation is implemented (Stage 5). The allowed values for `bizStep`, `disposition`, `errorDeclaration.reason`, `source.type`, `destination.type` are not checked. An event with `bizStep: "urn:epcglobal:cbv:bizstep:MADE_UP_VALUE"` passes through undetected.

### C&A Partner Rules

No partner-specific extension handling or business rules for C&A are documented or implemented. If C&A's EPCIS 1.2 feed uses custom extensions or non-standard namespace prefixes, these will be silently dropped.

---

## 6. Senior Product Manager

### No Operational Readiness

- No `/actuator/health`, `/actuator/info`, `/actuator/metrics` endpoints. The system cannot be wired into C&A's monitoring infrastructure without this.
- No Micrometer metrics. There are no counters for `events.received`, `events.converted`, `events.quarantined`, `events.failed`. No SLA measurement is possible.
- No structured logging with correlation IDs. Individual event processing cannot be traced through logs.
- No distributed tracing (OpenTelemetry / Zipkin / Jaeger).

### No Batch Interface

The product only exposes a synchronous REST endpoint. For a migration of a full EPCIS history from a partner, a file-drop or message-queue consumer is required. There is no `MigrationBatch` concept, no progress reporting, no partial-success resume, and no final reconciliation report that C&A's project manager can sign off on.

### No Versioning

The API has no version (`/api/v1/events`). When the EPCIS 2.0 output structure changes (e.g. adding `sourceList` support), there is no version contract that downstream consumers can pin to.

### Definition of Done Not Met

Per CLAUDE.md, "Definition of Done" requires:
- Tests added and green ✗ (incomplete)
- Mapping rules documented ✗
- Validation on all relevant levels ✗ (stages 2–6 missing)
- Failure cases explicitly handled ✗ (silent failures exist)
- No silent data corruption possible ✗ (null `eventTime` accepted; UUID injected as business `eventID`)

---

## Priority Order for Production Readiness at C&A

| Priority | Area | Blocking? |
|---|---|---|
| P0 | Authentication on the REST API | Yes — cannot expose to any network without it |
| P0 | Fix silent failures (null eventTime, UUID eventID injection, unsupported types skipped) | Yes — data integrity |
| P0 | Flyway migrations + remove `ddl-auto: update` | Yes — DB safety in production |
| P1 | Introduce port interfaces in the application layer (hexagonal enforcement) | Yes — testability and team scalability |
| P1 | Add `sourceList`, `destinationList`, `errorDeclaration` to domain model and pipeline | Yes — mapping completeness |
| P1 | GS1 EPCIS 2.0 JSON Schema validation on rendered output | Yes — conformance |
| P1 | CBV vocabulary validation (bizStep, disposition) | Yes — business correctness |
| P1 | Quarantine routing for unparseable / unmappable events | Yes — no silent data loss |
| P2 | Golden-master fixture tests + integration tests with Testcontainers | Yes — production confidence |
| P2 | Spring Actuator + Micrometer metrics | Yes — operational monitoring |
| P2 | `MigrationBatch` + reconciliation report | Yes — C&A sign-off capability |
| P2 | Input size limits + rate limiting | Yes — basic hardening |
| P3 | Structured logging with correlation IDs | For observability SLA |
| P3 | Secrets externalization (Vault / env vars) | For security posture |
| P3 | Batch / async ingestion interface | For full history migration |
| P3 | `docs/mapping-matrix.md`, `docs/validation-strategy.md`, `docs/migration-runbook.md` | For handover and audit |
