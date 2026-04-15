# EPCIS Phase 4 — CBV Validator + GS1 Digital Link
## Implementierungsspezifikation für Claude Code

---

## KONTEXT

**Projekt:** epcis-event-handler (Spring Boot 4.x, Java 17+, PostgreSQL 18, Maven)
**Phase 1:** Capture Service + Query API ✅
**Phase 2:** Inventory Service ✅
**Phase 3:** Downstream Integrations ✅
**Phase 4 Ziel:** CBV Conformance Validation + GS1 Digital Link Resolver

**Standards:**
- CBV 2.0 (Core Business Vocabulary) — Jun 2022, GS1 AISBL
- GS1 Digital Link URI Syntax 1.1.2 — Nov 2022, GS1 AISBL

---

## PHASE 4 SCOPE — 2 BLÖCKE

### Block A — CBV Validator
Validiert `bizStep`, `disposition`, `bizTransactionType`, `sourceDestType` in jedem
eingehenden Event gegen die offizielle GS1 CBV 2.0 Vokabular-Liste.
Ungültige Events landen in Quarantäne mit maschinenlesbarem Fehlercode.

### Block B — GS1 Digital Link Resolver
Löst GS1 Digital Link URLs auf und verknüpft sie mit EPCIS Event History und
Inventory-Daten aus Phase 2. Ermöglicht QR-Code → Track & Trace.

---

## PROJEKTSTRUKTUR — ERWEITERUNG

```
com.example.epcis/
├── api/
│   ├── capture/                           # BESTEHEND
│   ├── query/                             # BESTEHEND
│   ├── inventory/                         # BESTEHEND
│   ├── subscription/                      # BESTEHEND
│   ├── cbv/                               # NEU Block A
│   │   └── CbvController.java
│   └── digitallink/                       # NEU Block B
│       └── DigitalLinkController.java
├── application/
│   ├── capture/                           # BESTEHEND
│   ├── cbv/                               # NEU Block A
│   │   ├── CbvValidationService.java
│   │   └── CbvVocabularyLoader.java
│   └── digitallink/                       # NEU Block B
│       ├── DigitalLinkParser.java
│       └── DigitalLinkResolverService.java
├── domain/
│   └── model/
│       ├── cbv/                           # NEU
│       │   ├── CbvVocabularyType.java
│       │   └── CbvValidationResult.java
│       └── digitallink/                   # NEU
│           ├── DigitalLinkUri.java
│           └── ResolverResponse.java
├── infrastructure/
│   └── persistence/
│       ├── cbv/                           # NEU
│       │   ├── CbvVocabularyEntity.java
│       │   └── CbvVocabularyRepository.java
│       └── quarantine/                    # NEU
│           ├── QuarantineEntity.java
│           └── QuarantineRepository.java
└── config/
    └── CbvConfig.java                     # NEU
```

---

## BLOCK A — CBV VALIDATOR

### A.1 Domain Model

#### CbvVocabularyType.java
**Package:** `com.example.epcis.domain.model.cbv`

```java
/**
 * Die vier Standard-Vokabular-Typen des GS1 CBV 2.0.
 * Jeder Typ hat ein eigenes URI-Präfix und eine eigene Werteliste.
 */
public enum CbvVocabularyType {
    BIZ_STEP(
        "urn:epcglobal:cbv:bizstep:",
        "https://ref.gs1.org/cbv/BizStep-"
    ),
    DISPOSITION(
        "urn:epcglobal:cbv:disp:",
        "https://ref.gs1.org/cbv/Disp-"
    ),
    BIZ_TRANSACTION_TYPE(
        "urn:epcglobal:cbv:btt:",
        "https://ref.gs1.org/cbv/BTT-"
    ),
    SOURCE_DEST_TYPE(
        "urn:epcglobal:cbv:sdt:",
        "https://ref.gs1.org/cbv/SDT-"
    );

    private final String urnPrefix;
    private final String httpsPrefix;

    CbvVocabularyType(String urnPrefix, String httpsPrefix) {
        this.urnPrefix = urnPrefix;
        this.httpsPrefix = httpsPrefix;
    }

    public String getUrnPrefix() { return urnPrefix; }
    public String getHttpsPrefix() { return httpsPrefix; }

    /**
     * Extrahiert den Payload-Wert aus einer CBV URI.
     * Beispiel: "urn:epcglobal:cbv:bizstep:shipping" → "shipping"
     */
    public Optional<String> extractPayload(String uri) {
        if (uri == null) return Optional.empty();
        if (uri.startsWith(urnPrefix))
            return Optional.of(uri.substring(urnPrefix.length()));
        if (uri.startsWith(httpsPrefix))
            return Optional.of(uri.substring(httpsPrefix.length()));
        return Optional.empty();
    }
}
```

#### CbvValidationResult.java
**Package:** `com.example.epcis.domain.model.cbv`

```java
/**
 * Ergebnis der CBV-Validierung für ein einzelnes Event.
 * Immutable Value Object.
 */
@Getter
@Builder
public class CbvValidationResult {
    private final boolean valid;
    private final List<CbvViolation> violations;

    @Getter
    @Builder
    public static class CbvViolation {
        private final String field;        // z.B. "bizStep"
        private final String value;        // z.B. "urn:epcglobal:cbv:bizstep:typo"
        private final String errorCode;    // z.B. "CBV_UNKNOWN_BIZ_STEP"
        private final String message;      // Human-readable Beschreibung
    }
}
```

---

### A.2 Infrastructure — Persistence

#### CbvVocabularyEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.cbv`
**Annotation:** `@Entity`, `@Table(name = "cbv_vocabulary")`

**Felder:**

| Spalte | Typ | Beschreibung |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| vocabulary_type | VARCHAR(50) NOT NULL | BIZ_STEP / DISPOSITION / BIZ_TRANSACTION_TYPE / SOURCE_DEST_TYPE |
| payload | VARCHAR(100) NOT NULL | z.B. "shipping", "in_transit" |
| urn_uri | VARCHAR(255) NOT NULL | z.B. "urn:epcglobal:cbv:bizstep:shipping" |
| https_uri | VARCHAR(255) NOT NULL | z.B. "https://ref.gs1.org/cbv/BizStep-shipping" |
| deprecated | BOOLEAN DEFAULT false | veraltete Werte werden geloggt aber nicht blockiert |

**Unique Constraint:** `(vocabulary_type, payload)`

#### CbvVocabularyRepository.java

```java
Optional<CbvVocabularyEntity> findByVocabularyTypeAndPayload(
    String vocabularyType, String payload);

boolean existsByVocabularyTypeAndUrnUri(String vocabularyType, String urnUri);
boolean existsByVocabularyTypeAndHttpsUri(String vocabularyType, String httpsUri);

List<CbvVocabularyEntity> findByVocabularyType(String vocabularyType);
List<CbvVocabularyEntity> findByVocabularyTypeAndDeprecatedFalse(String vocabularyType);
```

---

#### QuarantineEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.quarantine`
**Annotation:** `@Entity`, `@Table(name = "quarantine_event")`

**Felder:**

| Spalte | Typ | Beschreibung |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| event_id | VARCHAR(255) | eventID aus dem Event |
| event_type | VARCHAR(50) | ObjectEvent / AggregationEvent |
| source_id | VARCHAR(100) | X-EPCIS-Source-ID Header |
| reason_code | VARCHAR(100) | z.B. "CBV_UNKNOWN_BIZ_STEP" |
| reason_detail | TEXT | Details welches Feld, welcher Wert |
| raw_payload | TEXT | Original XML-Payload |
| quarantined_at | TIMESTAMPTZ | Zeitpunkt |
| resolved | BOOLEAN DEFAULT false | manuell behoben? |
| resolved_at | TIMESTAMPTZ | NULL solange nicht behoben |

---

### A.3 Application — CBV Validation Service

#### CbvVocabularyLoader.java
**Package:** `com.example.epcis.application.cbv`
**Annotation:** `@Component`

**Zweck:** Lädt die vollständige CBV 2.0 Vokabular-Liste beim App-Start in die DB.
Verwendet `@PostConstruct` + Check ob Tabelle schon befüllt ist (Idempotenz).

**Vollständige bizStep-Werte (CBV 2.0 Section 7.1.3):**
```
accepting, arriving, assembling, collecting, commissioning, consigning,
creating_class_instance, cycle_counting, decommissioning, departing,
destroying, dispensing, encoding, entering_exiting, holding, inspecting,
installing, killing, loading, other, packing, picking, receiving,
removing, repackaging, repairing, replacing, reserving, retail_selling,
sampling, sensor_reporting, shipping, staging_outbound, stock_taking,
stocking, storing, transporting, unloading, unpacking, void_shipping
```

**Vollständige disposition-Werte (CBV 2.0 Section 7.2.3):**
```
active, available, completeness_inferred, completeness_verified,
conformant, container_closed, container_open, damaged, destroyed,
dispensed, disposed, encoded, expired, in_progress, in_transit,
inactive, mismatch_class, mismatch_instance, mismatch_quantity,
needs_replacement, non_sellable_other, partially_dispensed, recalled,
reserved, retail_sold, returned, sellable_accessible,
sellable_not_accessible, stolen, unavailable, unknown
```

**Vollständige bizTransactionType-Werte (CBV 2.0 Section 7.3.3):**
```
bol, desadv, inv, pedigree, po, poc, prodorder, recadv, rma, testprd,
testres, upevt
```

**Vollständige sourceDestType-Werte (CBV 2.0 Section 7.4.3):**
```
owning_party, possessing_party, location
```

**Implementierung:**
```java
@PostConstruct
public void loadIfEmpty() {
    // Wenn Tabelle leer → alle Werte einfügen
    // Wenn bereits befüllt → skip (Idempotenz)
    if (repository.count() == 0) {
        insertAllBizSteps();
        insertAllDispositions();
        insertAllBizTransactionTypes();
        insertAllSourceDestTypes();
        log.info("CBV vocabulary loaded: {} entries", repository.count());
    }
}
```

#### CbvValidationService.java
**Package:** `com.example.epcis.application.cbv`
**Annotation:** `@Service`

```java
/**
 * Validiert EPCIS Events gegen CBV 2.0 Vokabular.
 *
 * Validierungsregeln:
 * 1. bizStep: wenn gesetzt, muss ein gültiger CBV 2.0 bizStep URI sein
 * 2. disposition: wenn gesetzt, muss ein gültiger CBV 2.0 disposition URI sein
 * 3. bizTransactionType: jeder type-Wert in bizTransactionList muss gültig sein
 * 4. sourceDestType: jeder type-Wert in sourceList/destinationList muss gültig sein
 *
 * Verhalten bei Nicht-CBV URIs:
 * - URIs die NICHT mit urn:epcglobal:cbv: oder https://ref.gs1.org/cbv/ beginnen
 *   → erlaubt (Custom/Partner-Vokabular ist CBV-Compatible)
 * - URIs die mit urn:epcglobal:cbv: beginnen aber kein bekannter Wert sind
 *   → Verletzung, Event in Quarantäne
 *
 * Deprecated Werte:
 * - Werden akzeptiert aber als WARN geloggt
 *
 * @param event das zu validierende Domain-Event
 * @return CbvValidationResult mit allen Verletzungen
 */
public CbvValidationResult validate(EpcisEvent event);

/**
 * Validiert einen einzelnen URI-Wert gegen einen CBV-Vokabular-Typ.
 * Gibt Optional.empty() zurück wenn gültig.
 * Gibt Optional<CbvViolation> zurück wenn ungültig.
 */
public Optional<CbvValidationResult.CbvViolation> validateUri(
    String uri, CbvVocabularyType type, String fieldName);

/**
 * Prüft ob ein URI ein bekannter CBV-Wert ist.
 * Akzeptiert sowohl URN- als auch HTTPS-Form.
 */
public boolean isKnownCbvValue(String uri, CbvVocabularyType type);
```

**Fehler-Codes:**
```
CBV_UNKNOWN_BIZ_STEP           — bizStep URI nicht in CBV 2.0 Liste
CBV_UNKNOWN_DISPOSITION        — disposition URI nicht in CBV 2.0 Liste
CBV_UNKNOWN_BIZ_TRANSACTION_TYPE — bizTransactionType nicht in CBV 2.0 Liste
CBV_UNKNOWN_SOURCE_DEST_TYPE   — sourceDestType nicht in CBV 2.0 Liste
CBV_DEPRECATED_VALUE           — Wert ist deprecated (WARN, kein Fehler)
```

---

### A.4 Integration in Capture-Pfad

**Einzige Änderung an bestehendem Code:**

In `CaptureEventUseCase.java` — nach XSD-Validierung, vor Persistenz:

```java
// Nach validator.validate(xml) und parser.parse(xml):
// Schritt 3: CBV-Validierung
for (EpcisEvent event : events) {
    CbvValidationResult cbvResult = cbvValidationService.validate(event);
    if (!cbvResult.isValid() && cbvConfig.isStrictMode()) {
        // Strict Mode: Event in Quarantäne
        quarantineService.quarantine(event, xml, sourceId, cbvResult);
        continue; // Event nicht weiter verarbeiten
    } else if (!cbvResult.isValid()) {
        // Lenient Mode: loggen aber weiter verarbeiten
        log.warn("CBV_VIOLATION eventId={} violations={}",
            event.getEventId(), cbvResult.getViolations());
    }
    // ... normale Verarbeitung
}
```

**Konfiguration:**
```yaml
epcis:
  cbv:
    strict-mode: false   # false = loggen + weiterverarbeiten
                         # true  = in Quarantäne
    load-vocabulary: true
```

---

### A.5 API — CBV Endpoints

#### CbvController.java
**Package:** `com.example.epcis.api.cbv`
**Annotation:** `@RestController`, `@RequestMapping("/cbv")`

```
GET /cbv/vocabulary/{type}
    ?deprecated=false
    → Liste aller gültigen Werte für diesen Typ
    Erlaubte Typen: BIZ_STEP, DISPOSITION, BIZ_TRANSACTION_TYPE, SOURCE_DEST_TYPE

GET /cbv/validate?uri=urn:epcglobal:cbv:bizstep:shipping&type=BIZ_STEP
    → { "valid": true, "uri": "...", "type": "..." }

GET /cbv/quarantine
    ?resolved=false
    ?from=2024-01-01T00:00:00Z
    → Liste quarantänierter Events

PUT /cbv/quarantine/{id}/resolve
    → Event als manuell behoben markieren
```

---

## BLOCK B — GS1 DIGITAL LINK

### B.1 Domain Model

#### DigitalLinkUri.java
**Package:** `com.example.epcis.domain.model.digitallink`

```java
/**
 * Repräsentiert eine geparste GS1 Digital Link URI.
 * Immutable Value Object.
 *
 * Beispiele (GS1 Digital Link 1.1.2):
 *   https://id.example.com/01/04012345999990/21/ABC123
 *   → primaryKey=GTIN, primaryValue=04012345999990, qualifier=ser, qualifierValue=ABC123
 *
 *   https://id.example.com/00/340123451111111111
 *   → primaryKey=SSCC, primaryValue=340123451111111111
 *
 *   https://id.example.com/414/9521141111116
 *   → primaryKey=GLN, primaryValue=9521141111116
 *
 * Application Identifiers (AI):
 *   01 = GTIN
 *   00 = SSCC
 *   414 = GLN (location)
 *   21 = Serial Number (qualifier für GTIN)
 *   10 = Batch/Lot (qualifier für GTIN)
 */
@Getter
@Builder
public class DigitalLinkUri {
    private final String originalUri;
    private final String scheme;        // https
    private final String host;          // id.example.com
    private final String primaryAi;    // "01", "00", "414"
    private final String primaryKey;   // "gtin", "sscc", "gln"
    private final String primaryValue; // z.B. "04012345999990"
    private final Map<String, String> qualifiers; // z.B. {"21": "ABC123"}
    private final Map<String, String> dataAttributes; // Query-Parameter

    /**
     * Konvertiert den Digital Link zurück in EPCIS Pure Identity URN.
     *
     * 01/GTIN + 21/Serial → urn:epc:id:sgtin:...
     * 00/SSCC             → urn:epc:id:sscc:...
     * 414/GLN             → urn:epc:id:sgln:...
     */
    public Optional<String> toEpcUrn();
}
```

#### ResolverResponse.java
**Package:** `com.example.epcis.domain.model.digitallink`

```java
/**
 * Antwort des Digital Link Resolvers.
 * Enthält Links zu allen verfügbaren Datendiensten für den identifizierten Gegenstand.
 *
 * Format entspricht GS1 Digital Link 1.1.2 Resolver Response.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolverResponse {
    private final String identifier;       // der aufgelöste EPC URN
    private final String identifierType;   // SGTIN, SSCC, GLN
    private final List<LinkSet> linkSets;

    @Getter
    @Builder
    public static class LinkSet {
        private final String context;      // z.B. "https://id.example.com"
        private final List<Link> links;
    }

    @Getter
    @Builder
    public static class Link {
        private final String linkType;     // z.B. "gs1:epcis", "gs1:productInfo"
        private final String href;         // URL zu dem Dienst
        private final String title;        // Human-readable Beschreibung
        private final String type;         // MIME-Type z.B. "application/json"
    }
}
```

---

### B.2 Application — Digital Link Parser

#### DigitalLinkParser.java
**Package:** `com.example.epcis.application.digitallink`
**Annotation:** `@Service`

```java
/**
 * Parst GS1 Digital Link URIs nach dem GS1 Digital Link Standard 1.1.2.
 *
 * Unterstützte Primary Keys (Application Identifiers):
 *
 * AI  | Kurzname | Beschreibung
 * ----|----------|---------------------------
 * 01  | gtin     | Global Trade Item Number
 * 00  | sscc     | Serial Shipping Container Code
 * 414 | gln      | Global Location Number
 * 253 | gdti     | Global Document/Transaction Identifier
 * 255 | gcn      | Global Coupon Number
 * 401 | ginc     | Global Identification of a Consignment
 * 402 | gsin     | Global Shipment Identification Number
 * 8003| grai     | Global Returnable Asset Identifier
 * 8004| giai     | Global Individual Asset Identifier
 *
 * Unterstützte Key Qualifiers:
 * AI  | Kurzname | Beschreibung
 * ----|----------|---------------------------
 * 21  | ser      | Serial Number (für GTIN)
 * 10  | lot      | Batch/Lot Number (für GTIN)
 * 22  | cpv      | Consumer Product Variant (für GTIN)
 * 254 | glnx     | GLN Extension (für GLN)
 *
 * @param uri die zu parsende Digital Link URI
 * @return geparste DigitalLinkUri oder Exception wenn ungültig
 * @throws DigitalLinkParseException wenn die URI nicht dem Standard entspricht
 */
public DigitalLinkUri parse(String uri);

/**
 * Prüft ob eine URI eine gültige GS1 Digital Link URI ist.
 */
public boolean isDigitalLinkUri(String uri);

/**
 * Konvertiert einen EPC URN in eine Digital Link URI.
 * Inverse Operation zu parse().
 *
 * Beispiel:
 * urn:epc:id:sgtin:4056019.010532.12345 →
 * https://id.canda.com/01/04056019105326/21/12345
 */
public String toDigitalLink(String epcUrn, String baseUrl);
```

**Konvertierungslogik EPC URN → Digital Link AI:**
```
urn:epc:id:sgtin:{company}.{item}.{serial}
  → AI 01 (GTIN) + AI 21 (Serial)
  → GTIN = company + item + check digit (13 Stellen)

urn:epc:id:sscc:{company}.{serial}
  → AI 00 (SSCC)
  → SSCC = company + serial (18 Stellen)

urn:epc:id:sgln:{company}.{location}.{extension}
  → AI 414 (GLN) + AI 254 (Extension wenn != 0)
  → GLN = company + location + check digit (13 Stellen)
```

---

#### DigitalLinkResolverService.java
**Package:** `com.example.epcis.application.digitallink`
**Annotation:** `@Service`

```java
/**
 * Löst GS1 Digital Link URIs auf und liefert alle verfügbaren Datendienste.
 *
 * Verknüpft Digital Link mit:
 * 1. EPCIS Event History (aus Phase 1 Query API)
 * 2. Aktueller Inventory Status (aus Phase 2 Inventory Service)
 * 3. SSCC-Inhalt wenn der identifier eine SSCC ist (aus Phase 2)
 *
 * Link Types (GS1 Web Vocabulary):
 * - gs1:epcis       → EPCIS Event History Endpoint
 * - gs1:pip         → Product Information Page
 * - gs1:location    → Aktueller Standort (aus Inventory)
 * - gs1:certificationInfo → Produktzertifizierungen (Placeholder)
 */
public ResolverResponse resolve(String digitalLinkUri);

/**
 * Direkt auflösen per EPC URN (interne Verwendung).
 */
public ResolverResponse resolveByEpcUrn(String epcUrn);

/**
 * Liefert alle Events für einen Digital Link URI.
 * Wrapper um Query API.
 */
public List<EpcisEventEntity> getEventHistory(DigitalLinkUri dlUri);

/**
 * Liefert den aktuellen Inventory-Status für einen Digital Link URI.
 * Wrapper um Inventory Service.
 */
public Optional<EpcState> getCurrentLocation(DigitalLinkUri dlUri);
```

---

### B.3 API — Digital Link Endpoints

#### DigitalLinkController.java
**Package:** `com.example.epcis.api.digitallink`
**Annotation:** `@RestController`, `@RequestMapping("/digitallink")`

```
GET /digitallink/resolve
    ?uri=https://id.example.com/01/04012345999990/21/ABC123
    → ResolverResponse — alle verfügbaren Links für diesen Identifier

GET /digitallink/resolve/{ai}/{value}
    → Kurzform: /digitallink/resolve/01/04012345999990
    → Direkt per AI + Wert auflösen ohne vollständige URL

GET /digitallink/history
    ?uri=https://id.example.com/01/04012345999990/21/ABC123
    → Vollständige EPCIS Event History für diesen Identifier

GET /digitallink/location
    ?uri=https://id.example.com/01/04012345999990/21/ABC123
    → Aktueller Standort (aus Inventory Service)

GET /digitallink/parse
    ?uri=https://id.example.com/01/04012345999990/21/ABC123
    → Gibt die geparste DigitalLinkUri zurück (Debug/Testing)

GET /digitallink/convert
    ?epc=urn:epc:id:sgtin:4056019.010532.12345
    ?baseUrl=https://id.canda.com
    → Konvertiert EPC URN zu Digital Link URL
```

**Beispiel Response für `/digitallink/resolve`:**
```json
{
  "identifier": "urn:epc:id:sgtin:4056019.010532.12345",
  "identifierType": "SGTIN",
  "linkSets": [
    {
      "context": "https://id.canda.com",
      "links": [
        {
          "linkType": "gs1:epcis",
          "href": "https://id.canda.com/epcis/query/events?epcMatch=urn:epc:id:sgtin:4056019.010532.12345",
          "title": "EPCIS Event History",
          "type": "application/json"
        },
        {
          "linkType": "gs1:location",
          "href": "https://id.canda.com/inventory/epc?epc=urn:epc:id:sgtin:4056019.010532.12345",
          "title": "Current Location",
          "type": "application/json"
        }
      ]
    }
  ]
}
```

---

### B.4 Konfiguration

#### CbvConfig.java
**Package:** `com.example.epcis.config`

```java
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.cbv")
public class CbvConfig {
    private boolean strictMode = false;     // false = warn only, true = quarantine
    private boolean loadVocabulary = true;  // CBV beim Start laden
    private boolean allowDeprecated = true; // deprecated Werte erlauben
}
```

**application.yml — Ergänzungen:**
```yaml
epcis:
  cbv:
    strict-mode: false
    load-vocabulary: true
    allow-deprecated: true
  digitallink:
    base-url: ${DIGITAL_LINK_BASE_URL:https://id.canda.com}
    enabled: true
```

---

## DATENBANK — NEUE TABELLEN

```sql
-- CBV Vokabular-Tabelle
CREATE TABLE cbv_vocabulary (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    vocabulary_type VARCHAR(50)  NOT NULL,
    payload         VARCHAR(100) NOT NULL,
    urn_uri         VARCHAR(255) NOT NULL,
    https_uri       VARCHAR(255) NOT NULL,
    deprecated      BOOLEAN      NOT NULL DEFAULT false,
    UNIQUE (vocabulary_type, payload)
);
CREATE INDEX idx_cbv_vocabulary_type ON cbv_vocabulary(vocabulary_type);
CREATE INDEX idx_cbv_urn_uri         ON cbv_vocabulary(urn_uri);

-- Quarantäne-Tabelle
CREATE TABLE quarantine_event (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id      VARCHAR(255),
    event_type    VARCHAR(50),
    source_id     VARCHAR(100),
    reason_code   VARCHAR(100) NOT NULL,
    reason_detail TEXT,
    raw_payload   TEXT,
    quarantined_at TIMESTAMPTZ NOT NULL,
    resolved      BOOLEAN      NOT NULL DEFAULT false,
    resolved_at   TIMESTAMPTZ
);
CREATE INDEX idx_quarantine_resolved     ON quarantine_event(resolved);
CREATE INDEX idx_quarantine_reason_code  ON quarantine_event(reason_code);
CREATE INDEX idx_quarantine_quarantined  ON quarantine_event(quarantined_at);
```

---

## ÄNDERUNGEN AN BESTEHENDEN KLASSEN

**1. CaptureEventUseCase.java** — CBV-Validierung einbinden:
```java
// Nach XSD-Validierung, vor Persistenz:
CbvValidationResult cbvResult = cbvValidationService.validate(event);
if (!cbvResult.isValid()) {
    if (cbvConfig.isStrictMode()) {
        quarantineService.quarantine(event, xml, sourceId, cbvResult);
        continue;
    }
    log.warn("CBV_VIOLATION eventId={} violations={}",
        event.getEventId(), cbvResult.getViolations());
}
```

**2. GlobalExceptionHandler.java** — neue Exceptions:
```java
@ExceptionHandler(DigitalLinkParseException.class)
public ResponseEntity<Map<String, String>> handleDigitalLinkParseException(
        DigitalLinkParseException ex) {
    log.warn("Digital Link parse error: {}", ex.getMessage());
    return ResponseEntity.badRequest()
        .body(Map.of("error", ex.getMessage()));
}
```

**3. application.yml** — neue Blöcke ergänzen.

---

## TESTS — PFLICHT FÜR PHASE 4

### CbvValidationServiceTest.java
```
✅ valide bizStep URI → kein Fehler
✅ unbekannter bizStep URI mit urn:epcglobal:cbv:bizstep: Präfix → CBV_UNKNOWN_BIZ_STEP
✅ Custom URI ohne urn:epcglobal:cbv: Präfix → gültig (CBV-Compatible)
✅ valide disposition URI → kein Fehler
✅ unbekannte disposition URI → CBV_UNKNOWN_DISPOSITION
✅ deprecated Wert → WARN geloggt, kein Fehler
✅ null bizStep → kein Fehler (optional)
✅ HTTPS-Form URI → erkannt als gültig
✅ URN-Form URI → erkannt als gültig
✅ Event mit mehreren Verletzungen → alle Verletzungen gesammelt
```

### CbvVocabularyLoaderTest.java
```
✅ Loader befüllt alle 4 Vokabular-Typen beim Start
✅ Loader ist idempotent — zweiter Aufruf ändert nichts
✅ Alle 40 bizStep-Werte vorhanden
✅ Alle 31 disposition-Werte vorhanden
✅ Alle 12 bizTransactionType-Werte vorhanden
✅ Alle 3 sourceDestType-Werte vorhanden
```

### DigitalLinkParserTest.java
```
✅ GTIN + Serial → korrekt geparst (AI 01 + AI 21)
✅ SSCC → korrekt geparst (AI 00)
✅ GLN → korrekt geparst (AI 414)
✅ GTIN + Lot → korrekt geparst (AI 01 + AI 10)
✅ Ungültige URI → DigitalLinkParseException
✅ EPC URN SGTIN → Digital Link URL konvertiert
✅ EPC URN SSCC → Digital Link URL konvertiert
✅ Digital Link → EPC URN SGTIN konvertiert
✅ Digital Link → EPC URN SSCC konvertiert
✅ Kurzname "gtin" und numerisch "01" beide erkannt
```

### DigitalLinkResolverServiceTest.java
```
✅ SGTIN Digital Link → ResolverResponse mit gs1:epcis Link
✅ SGTIN Digital Link → ResolverResponse mit gs1:location Link
✅ Unbekannte SGTIN → ResolverResponse ohne gs1:location (404 intern)
✅ SSCC Digital Link → ResolverResponse mit Pallet-Link
✅ Ungültige URI → DigitalLinkParseException propagiert
```

### DigitalLinkControllerIntegrationTest.java
```
✅ GET /digitallink/resolve?uri=... → 200 + ResolverResponse
✅ GET /digitallink/resolve?uri=UNGÜLTIG → 400
✅ GET /digitallink/history?uri=... → 200 + Event-Liste
✅ GET /digitallink/location?uri=... → 200 + EpcState
✅ GET /digitallink/parse?uri=... → 200 + DigitalLinkUri
✅ GET /digitallink/convert?epc=... → 200 + Digital Link URL
```

---

## DEFINITION OF DONE PHASE 4

```
☐ CBV Vokabular-Tabelle beim App-Start korrekt befüllt (40+31+12+3 Einträge)
☐ Events mit ungültigen CBV URIs werden erkannt und geloggt
☐ strict-mode=true → ungültige Events in quarantine_event Tabelle
☐ GET /cbv/vocabulary/BIZ_STEP → alle 40 bizStep-Werte
☐ GET /cbv/validate?uri=...&type=... → korrekte Validierung
☐ Custom URIs (nicht urn:epcglobal:cbv:) werden nicht blockiert
☐ Digital Link URI Parser: GTIN/SSCC/GLN korrekt geparst
☐ EPC URN ↔ Digital Link URL Konvertierung bidirektional
☐ GET /digitallink/resolve → ResolverResponse mit korrekten Links
☐ GET /digitallink/history → verknüpft mit EPCIS Event History
☐ GET /digitallink/location → verknüpft mit Inventory Service
☐ Alle Pflicht-Tests grün
☐ Phase 1-3 Tests weiterhin grün (keine Regression)
☐ application.yml um cbv + digitallink Blöcke ergänzt
```

---

## SMOKE-TEST NACH PHASE 4

```bash
# 1. CBV Vokabular prüfen
curl "http://localhost:8080/cbv/vocabulary/BIZ_STEP" | jq '. | length'
# Erwartung: 40

# 2. Gültige bizStep validieren
curl "http://localhost:8080/cbv/validate?uri=urn:epcglobal:cbv:bizstep:shipping&type=BIZ_STEP"
# Erwartung: { "valid": true }

# 3. Ungültige bizStep validieren
curl "http://localhost:8080/cbv/validate?uri=urn:epcglobal:cbv:bizstep:typo&type=BIZ_STEP"
# Erwartung: { "valid": false, "errorCode": "CBV_UNKNOWN_BIZ_STEP" }

# 4. Digital Link parsen
curl "http://localhost:8080/digitallink/parse?uri=https://id.example.com/01/04012345999990/21/ABC123"
# Erwartung: primaryAi=01, primaryValue=04012345999990, qualifiers={21: ABC123}

# 5. EPC URN zu Digital Link konvertieren
curl "http://localhost:8080/digitallink/convert?epc=urn:epc:id:sgtin:4290025.077551.1&baseUrl=https://id.canda.com"
# Erwartung: https://id.canda.com/01/.../21/1

# 6. Digital Link auflösen (SGTIN aus Phase 2 Smoke-Test)
curl "http://localhost:8080/digitallink/resolve?uri=https://id.canda.com/01/.../21/1"
# Erwartung: ResolverResponse mit gs1:epcis + gs1:location Links
```
