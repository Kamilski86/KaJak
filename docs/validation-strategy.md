# Validation Strategy

This document describes the six validation stages applied to every EPCIS event
as it flows through the conversion pipeline.

## Overview

```
Input XML
    │
    ▼  Stage 1 — Structural / XSD validation
    │
    ▼  Stage 2 — Legacy semantic validation (parser)
    │
    ▼  Stage 3 — Canonical domain validation  [planned]
    │
    ▼  Stage 4 — EPCIS 2.0 JSON Schema validation
    │
    ▼  Stage 5 — CBV vocabulary validation
    │
    ▼  Stage 6 — Migration reconciliation      [planned]
    │
Output JSON
```

---

## Stage 1: Structural / XSD Validation

**Component**: `EpcisXmlValidator` (`infrastructure.xml`)

**What it checks**:
- XML is well-formed (parser hardening: no XXE, DOCTYPE declarations rejected).
- Document structure matches the EPCIS 1.2 XSD (`EPCglobal-epcis-1_2.xsd`).

**On failure**: `EpcisValidationException` → HTTP 400. Document is **not** quarantined
(malformed XML has no recoverable business content).

**Parser hardening applied**:
- `disallow-doctype-decl = true`
- `external-general-entities = false`
- `external-parameter-entities = false`
- `ACCESS_EXTERNAL_DTD = ""`
- `ACCESS_EXTERNAL_SCHEMA = ""`

---

## Stage 2: Legacy Semantic Validation

**Component**: `EpcisXmlParser` (`infrastructure.xml`)

**What it checks**:
- Mandatory fields present: `eventTime` (all events).
- Field formats valid: timestamps parseable as `OffsetDateTime`, `action` is a known enum.
- Event type is supported (`ObjectEvent`, `AggregationEvent`).

**On failure**:
- Missing/invalid field → `EpcisValidationException` → HTTP 400. Not quarantined.
- Unsupported event type → `EpcisParsingException` → **QUARANTINE** (`UNSUPPORTED_EVENT_TYPE`) + HTTP 422.

**Quarantine rationale**: An unsupported event type has recoverable business content
(the raw XML is preserved). It can be reprocessed once the event type is supported.

---

## Stage 3: Canonical Domain Validation

**Status**: Planned for a future release.

**Intent**: Validate that the canonical model is internally consistent before rendering.
Examples of rules to enforce:
- `AggregationEvent` DELETE with non-empty `childEpcs` is semantically valid (partial disaggregation).
- `AggregationEvent` ADD must have a `parentId`.
- `ObjectEvent` DELETE with ILMD must be rejected (business rule; currently enforced in Stage 5).

---

## Stage 4: EPCIS 2.0 JSON Schema Validation

**Component**: `Epcis2JsonSchemaValidator` (`domain.service`)

**Schema source**: Official GS1 EPCIS 2.0 JSON Schema (Draft-07),
downloaded from `https://ref.gs1.org/standards/epcis/2.0.0/epcis-json-schema.json`
and bundled at `src/main/resources/schema/epcis-2.0-json-schema.json`.

**What it checks**:
- The rendered `EpcisDocumentDto` conforms to the official GS1 JSON Schema.
- Required fields, type constraints, and structure validated by the schema.

**On failure**: `Epcis2SchemaValidationException` → HTTP 500. This is a **renderer bug**,
not a client error. The raw fragment is not quarantined (the input was valid; the renderer
produced non-conformant output). A correlation reference ID is returned to the caller.

**Validation implementation**: `networknt/json-schema-validator` v1.5.6 (Draft-07 support).
Schema is loaded once at startup via `@PostConstruct`.

---

## Stage 5: CBV Vocabulary Validation

**Component**: `CbvVocabularyValidator` (`domain.service`)

**Reference**: GS1 Core Business Vocabulary (CBV) Standard, Release 2.0.

**What it checks**:

| Field | Validation | Source |
|---|---|---|
| `bizStep` | Allowlist of 45 GS1 CBV URIs | GS1 CBV 2.0 §4 |
| `disposition` | Allowlist of 29 GS1 CBV URIs | GS1 CBV 2.0 §5 |
| `errorDeclaration.reason` | Allowlist of 2 GS1 CBV URIs | GS1 CBV 2.0 §9 |
| `sourceList[].type` | Allowlist of 3 SDT URIs | GS1 CBV 2.0 §7 |
| `destinationList[].type` | Allowlist of 3 SDT URIs | GS1 CBV 2.0 §7 |
| ILMD on DELETE | Always rejected | CBV semantic rule |

**User-defined extensions** (URIs not starting with `urn:epcglobal:cbv:`):
Accepted with a `WARN` log. This allows partner-defined extension vocabularies without
breaking the pipeline. Partners should be asked to adopt standard CBV URIs where possible.

**On failure**: `CbvValidationException` → **QUARANTINE** (`CBV_VIOLATION`) + HTTP 422.
The full raw XML is preserved in the quarantine record.

---

## Stage 6: Migration Reconciliation

**Status**: Planned for a future release.

**Intent**: Batch-level reconciliation and quality metrics:
- Input document count = processed + rejected + quarantined.
- Per-run quality report (% quarantined, % CBV violations, unknown event types).
- Idempotency check (detect duplicate document submissions).
- Output comparable to `MigrationBatch` aggregate in the domain model.

---

## Quarantine Summary

| Error Code | Trigger Stage | Recovery Path |
|---|---|---|
| `UNSUPPORTED_EVENT_TYPE` | Stage 2 | Implement the event type, resubmit raw fragment. |
| `CBV_VIOLATION` | Stage 5 | Correct the vocabulary URI at source or add an explicit mapping rule; resubmit. |

Quarantined items are queryable via `GET /api/quarantine`.
Raw XML is always preserved for reprocessing.

---

## What is Never Silently Dropped

- A missing mandatory field is always a hard error (Stage 2).
- An unsupported event type is always quarantined (never silently skipped).
- A CBV violation is always quarantined (never auto-corrected).
- A renderer that produces non-conformant JSON is always a 500 (never silently accepted).
- ILMD on a DELETE event is always rejected (Stage 5).
