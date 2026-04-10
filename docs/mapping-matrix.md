# Field Mapping Matrix: EPCIS 1.2 → Canonical → EPCIS 2.0

This matrix documents every field mapping decision. The canonical model is the
authoritative intermediate representation; no direct XML-to-JSON mapping is performed.

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | Fully mapped and validated |
| ⚠️ | Mapped with documented constraints |
| ❌ | Explicitly not mapped (documented reason) |
| 🚫 | Rejected at validation; routes to quarantine |

---

## Common Fields (all event types)

| EPCIS 1.2 XML element | Canonical model field | EPCIS 2.0 JSON field | Status | Notes |
|---|---|---|---|---|
| `eventTime` | `eventTime` | `eventTime` | ✅ | Mandatory. Parsed as `OffsetDateTime`. Missing value → `EpcisValidationException`. |
| `eventTimeZoneOffset` | `eventTimeZoneOffset` | `eventTimeZoneOffset` | ✅ | Preserved as-is (string, e.g. `+02:00`). |
| `recordTime` | `recordTime` | `recordTime` | ✅ | Optional. Null is permitted. |
| `eventID` | `eventId` | `eventID` | ⚠️ | Optional in 1.2; if absent, NOT fabricated and NOT emitted. See ADR-002. |
| `action` | `action` | `action` | ✅ | Parsed as `Action` enum (`ADD`, `OBSERVE`, `DELETE`). |
| `bizStep` | `bizStep` | `bizStep` | ✅ | Validated against CBV 2.0 allowlist (45 values). Non-CBV user extensions accepted with warning. |
| `disposition` | `disposition` | `disposition` | ✅ | Validated against CBV 2.0 allowlist (29 values). |
| `readPoint/id` | `readPoint` | `readPoint.id` | ✅ | Nested `id` element in 1.2 maps to object `{"id": "..."}` in 2.0. |
| `bizLocation/id` | `bizLocation` | `bizLocation.id` | ✅ | Same pattern as `readPoint`. |
| `bizTransactionList/bizTransaction[@type]` | `bizTransactionList` (`BusinessTransaction`) | `bizTransactionList[].bizTransaction + type` | ✅ | Type attribute preserved. |
| `sourceList/source[@type]` | `sourceList` (`Source`) | `sourceList[].source + type` | ✅ | Type validated against CBV SDT allowlist. |
| `destinationList/destination[@type]` | `destinationList` (`Destination`) | `destinationList[].destination + type` | ✅ | Type validated against CBV SDT allowlist. |
| `errorDeclaration` | `errorDeclaration` (`ErrorDeclaration`) | `errorDeclaration` | ✅ | `declaringTime`, `reason`, `correctingEventIDs` all mapped. Reason validated (CBV ER allowlist). |
| `extension` | `extension` (`ExtensionPayload`) | `extension` (flat map) | ⚠️ | Flat leaf-text extraction only. Nested extension structures are captured by local name only. Deeply nested or namespace-qualified structures may lose precision — route to quarantine if semantics unclear. |

---

## ObjectEvent-specific Fields

| EPCIS 1.2 XML element | Canonical model field | EPCIS 2.0 JSON field | Status | Notes |
|---|---|---|---|---|
| `epcList/epc` | `epcList` (`List<String>`) | `epcList` | ✅ | Instance-level identifiers. |
| `quantityList/quantityElement` | `quantityList` (`List<QuantityElement>`) | `quantityList[].{epcClass, quantity, uom}` | ✅ | Class-level identifiers. `epcClass`, `quantity`, `uom` all mapped. |
| `ilmd/*` | `ilmd` (`IlmdPayload`) | `ilmd` (flat map) | ⚠️ | Flat leaf-text extraction. Standard ILMD fields (lotNumber, itemExpirationDate) are preserved by local name. ILMD on DELETE events is rejected (CBV violation). |

---

## AggregationEvent-specific Fields

| EPCIS 1.2 XML element | Canonical model field | EPCIS 2.0 JSON field | Status | Notes |
|---|---|---|---|---|
| `parentID` | `parentId` | `parentID` | ✅ | Required for ADD/OBSERVE. Allowed to be absent only per standard rules. |
| `childEPCs/epc` | `childEpcs` (`List<String>`) | `childEPCs` | ✅ | Empty list on DELETE is semantically valid (full disaggregation per EPCIS standard). |
| `childQuantityList/quantityElement` | `childQuantityList` (`List<QuantityElement>`) | `childQuantityList` | ✅ | Class-level children. |

---

## Explicitly Not Mapped

| EPCIS 1.2 / EPCIS 2.0 concept | Decision | Reason |
|---|---|---|
| `TransactionEvent` | 🚫 Routes to quarantine | Not in scope for initial release. Requires separate business validation. |
| `TransformationEvent` | 🚫 Routes to quarantine | EPCIS 2.0 only concept; no equivalent in 1.2 source data. |
| `AssociationEvent` | 🚫 Routes to quarantine | EPCIS 2.0 only; cannot be inferred from 1.2 input. |
| `persistentDisposition` | ❌ Not generated | Must not be inferred from 1.2 input without explicit business rule. |
| `sensorElementList` | ❌ Not mapped | Out of scope for this migration. Sensor data requires separate handling. |
| Namespace-qualified extension attributes | ⚠️ Partially captured | Only local name used; namespace URI dropped. Acceptable for known partner extensions; document any known gaps. |
| `schemaVersion`, `creationDate` on document envelope | ❌ Not forwarded to individual events | Document-level attributes are not semantically equivalent to event-level fields. |

---

## CBV Validation Reference

### Allowed `bizStep` Values (45)
Full GS1 CBV 2.0 list — see `CbvVocabularyValidator.ALLOWED_BIZ_STEPS`.

### Allowed `disposition` Values (29)
Full GS1 CBV 2.0 list — see `CbvVocabularyValidator.ALLOWED_DISPOSITIONS`.

### Allowed `errorDeclaration.reason` Values
- `urn:epcglobal:cbv:er:did_not_occur`
- `urn:epcglobal:cbv:er:incorrect_data`

### Allowed Source/Destination Type Values (SDT)
- `urn:epcglobal:cbv:sdt:owning_party`
- `urn:epcglobal:cbv:sdt:possessing_party`
- `urn:epcglobal:cbv:sdt:location`

---

## Output Envelope

| EPCIS 2.0 JSON-LD field | Value | Notes |
|---|---|---|
| `@context` | `https://ref.gs1.org/standards/epcis/epcis-context.jsonld` | GS1 official context; present only on document envelope, not on individual events. |
| `type` | `EPCISDocument` | Fixed. |
| `epcisBody.eventList` | `[...]` | Array of converted events. |
| `schemaVersion` | Not emitted | Not stored in canonical model. Added if required by downstream consumers. |
| `creationDate` | Not emitted | Not stored in canonical model. |
