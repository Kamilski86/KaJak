# EPCIS Inventory Service — Phase 2 Implementierungsspezifikation
## Für Claude Code

---

## KONTEXT

**Projekt:** epcis-event-handler (Spring Boot 4.x, Java 17+, PostgreSQL 18, Maven)
**Phase 1 Status:** Abgeschlossen — Capture Service + Query API laufen stabil.
**Phase 2 Ziel:** EPC State DB + Inventory Service implementieren.

**Fachlicher Hintergrund (REQ401):**
Das System soll den letzten bekannten Zustand jeder SGTIN speichern und abfragen.
Grundlage sind die EPCIS Events die über den Capture Service reinkommen.
Der Inventory Service liest aus `epcis_event` und schreibt in `epc_state` + `sscc_content`.

**Was nach Phase 2 möglich ist:**
- Wo ist eine bestimmte SGTIN gerade? (aktueller Standort + bizStep + disposition)
- Was liegt an einem bestimmten Standort (GLN)?
- Welche SGTINs sind auf einer bestimmten Palette (SSCC)?
- Vollständige Bewegungshistorie einer SGTIN
- Wie viele Einheiten einer GTIN sind vorhanden?
- Rebuild: Bestand aus allen Events neu berechnen (Idempotenz)

**Wichtig — Was Phase 2 NICHT implementiert:**
- SGTIN State Matrix (REQ201) — Phase 3
- Automatische Disaggregation bei Store-Receiving (REQ208) — Phase 3
- Push-Benachrichtigungen / Subscriptions (REQ310 ff.) — Phase 3
- Integration mit SAP/DWH/GSPM — Phase 3

---

## PHASE 2 SCOPE

1. EPC State DB (REQ401) — `epc_state` Tabelle mit letztem bekannten Zustand pro SGTIN
2. SSCC Content Tracking — `sscc_content` Tabelle: welche SGTINs auf welcher SSCC
3. Inventory Processor — liest neue Events aus `epcis_event` und aktualisiert State
4. Inventory Query API — REST Endpunkte für alle Inventory-Abfragen
5. Rebuild Endpoint — Bestand aus allen Events neu berechnen (Idempotenz)
6. Movement History — vollständige Bewegungshistorie pro EPC

---

## PROJEKTSTRUKTUR — ERWEITERUNG

Bestehende Klassen NICHT ändern. Nur neue Packages + Klassen.

```
com.example.epcis/
├── api/
│   ├── capture/                           # BESTEHEND Phase 1
│   ├── query/                             # BESTEHEND Phase 1
│   └── inventory/                         # NEU Phase 2
│       ├── InventoryController.java
│       ├── EpcStateResponse.java
│       ├── StockResponse.java
│       ├── PalletResponse.java
│       ├── MovementHistoryResponse.java
│       └── RebuildResponse.java
├── application/
│   ├── capture/                           # BESTEHEND Phase 1
│   ├── query/                             # BESTEHEND Phase 1
│   └── inventory/                         # NEU Phase 2
│       ├── InventoryProcessorService.java
│       ├── InventoryQueryService.java
│       └── InventoryRebuildService.java
├── domain/
│   └── model/                             # BESTEHEND + Erweiterungen
│       ├── EpcisEvent.java                # BESTEHEND
│       ├── ObjectEvent.java               # BESTEHEND
│       ├── AggregationEvent.java          # BESTEHEND
│       ├── EpcState.java                  # NEU — Domain-Objekt für EPC Zustand
│       └── SsccContent.java               # NEU — Domain-Objekt für Palette
├── infrastructure/
│   └── persistence/
│       ├── audit/                         # BESTEHEND Phase 1
│       └── inventory/                     # NEU Phase 2
│           ├── EpcStateEntity.java
│           ├── EpcStateRepository.java
│           ├── SsccContentEntity.java
│           ├── SsccContentRepository.java
│           ├── MovementHistoryEntity.java
│           └── MovementHistoryRepository.java
└── config/
    └── InventoryConfig.java               # NEU Phase 2
```

---

## 1. DOMAIN MODEL — NEUE KLASSEN

### EpcState.java
**Package:** `com.example.epcis.domain.model`

```java
/**
 * Letzter bekannter Zustand einer SGTIN.
 * Entspricht REQ401 — EPC State DB.
 *
 * Felder gemäß REQ401:
 * - epcUrn       : SGTIN als URN
 * - currentStatus: letzter bizStep-basierter Status
 * - bizLocation  : letzter bekannter Standort (GLN)
 * - lastEventId  : eventID des letzten verarbeiteten Events
 * - sscc         : SSCC aus erstem Packing AggregationEvent (falls vorhanden)
 *
 * Timestamp-Felder (REQ401):
 * - Jeder Disposition-Wert bekommt seinen eigenen Timestamp
 * - updateDate: technischer Wert — wird bei jeder Änderung auf now() gesetzt
 * - createDate: wird einmalig beim ersten Erfassen gesetzt
 *
 * Update-Regel (REQ401 + REQ217):
 * Ein Event aktualisiert bizLocation und lastEventId NUR wenn eventTime
 * des neuen Events NEUER ist als das letzte bereits verarbeitete Event.
 * Out-of-Order Events werden gespeichert aber ändern den aktuellen Status NICHT.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpcState {
    private String epcUrn;
    private String currentStatus;         // z.B. "in_transit", "in_progress", "encoded"
    private String bizLocation;           // SGLN URI
    private String lastEventId;
    private String sscc;                  // SSCC URI, optional
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;
    private OffsetDateTime lastEventTime;

    // Disposition-spezifische Timestamps (REQ401)
    // Nur der Timestamp der Disposition wird gesetzt wenn das Event diese Disposition hat
    // und das Event neuer ist als der bestehende Timestamp für diese Disposition
    private OffsetDateTime encodedAt;
    private OffsetDateTime inProgressAt;
    private OffsetDateTime inTransitAt;
    private OffsetDateTime accessibleForCustomerAt;
    private OffsetDateTime availableNotAccessibleForCustomerAt;
    private OffsetDateTime retiredAt;
    private OffsetDateTime soldAt;
}
```

### SsccContent.java
**Package:** `com.example.epcis.domain.model`

```java
/**
 * Inhalt einer SSCC (Palette) — welche SGTINs sind aktuell zugeordnet.
 * Wird aus AggregationEvents berechnet (REQ205).
 *
 * ADD  → SGTINs zur SSCC hinzufügen
 * DELETE → SGTINs von SSCC entfernen
 *        → wenn childEPCs leer → alle SGTINs entfernen (REQ215)
 */
@Getter
@Builder
public class SsccContent {
    private final String ssccUrn;
    private final List<String> childEpcs;
    private final String bizLocation;      // Standort der SSCC (von letztem Event)
    private final OffsetDateTime lastUpdated;
}
```

---

## 2. INFRASTRUCTURE — PERSISTENCE

### EpcStateEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.inventory`
**Annotation:** `@Entity`, `@Table(name = "epc_state")`

**Felder:**

| Spalte | Typ | Nullable | Beschreibung |
|---|---|---|---|
| id | BIGINT IDENTITY | NOT NULL | PK |
| epc_urn | VARCHAR(255) UNIQUE | NOT NULL | SGTIN URN — eindeutiger Key |
| current_status | VARCHAR(100) | NULL | letzter bizStep/disposition Wert |
| biz_location | VARCHAR(255) | NULL | SGLN URI — aktueller Standort |
| last_event_id | VARCHAR(255) | NULL | eventID des letzten Events |
| last_event_time | TIMESTAMPTZ | NULL | eventTime des letzten Events |
| sscc | VARCHAR(255) | NULL | SSCC aus erstem Packing Event |
| create_date | TIMESTAMPTZ | NOT NULL | erste Erfassung |
| update_date | TIMESTAMPTZ | NOT NULL | letzte Änderung (technisch) |
| encoded_at | TIMESTAMPTZ | NULL | Timestamp für encoded |
| in_progress_at | TIMESTAMPTZ | NULL | Timestamp für in_progress |
| in_transit_at | TIMESTAMPTZ | NULL | Timestamp für in_transit |
| accessible_for_customer_at | TIMESTAMPTZ | NULL | Timestamp für accessible_for_customer |
| available_not_accessible_at | TIMESTAMPTZ | NULL | Timestamp für available_not_accessible |
| retired_at | TIMESTAMPTZ | NULL | Timestamp für retired/multipleRetired |
| sold_at | TIMESTAMPTZ | NULL | Timestamp für sold |

**Lombok:** `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

### EpcStateRepository.java
**Package:** `com.example.epcis.infrastructure.persistence.inventory`
**Annotation:** `@Repository`
**Extends:** `JpaRepository<EpcStateEntity, Long>`

```java
Optional<EpcStateEntity> findByEpcUrn(String epcUrn);

// Alle SGTINs an einem bestimmten Standort
List<EpcStateEntity> findByBizLocation(String bizLocation);

// Alle SGTINs die einer bestimmten SSCC zugeordnet sind
List<EpcStateEntity> findBySscc(String sscc);

// Anzahl SGTINs pro Standort (für Quantity-Abfrage)
long countByBizLocation(String bizLocation);

// Suche nach GTIN-Prefix (alle Serialnummern einer GTIN)
// GTIN = ersten Teil der SGTIN vor dem letzten Punkt
@Query("SELECT e FROM EpcStateEntity e WHERE e.epcUrn LIKE :gtinPrefix%")
List<EpcStateEntity> findByGtinPrefix(@Param("gtinPrefix") String gtinPrefix);

// Existenz-Prüfung für Idempotenz
boolean existsByEpcUrnAndLastEventId(String epcUrn, String eventId);
```

### SsccContentEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.inventory`
**Annotation:** `@Entity`, `@Table(name = "sscc_content")`

**Felder:**

| Spalte | Typ | Nullable | Beschreibung |
|---|---|---|---|
| id | BIGINT IDENTITY | NOT NULL | PK |
| sscc_urn | VARCHAR(255) | NOT NULL | SSCC URN |
| child_epc | VARCHAR(255) | NOT NULL | SGTIN URN |
| biz_location | VARCHAR(255) | NULL | Standort der SSCC |
| added_at | TIMESTAMPTZ | NOT NULL | Zeitpunkt der Zuordnung |

**Unique Constraint:** `(sscc_urn, child_epc)` — eine SGTIN kann nicht zweimal derselben SSCC zugeordnet sein.

**Lombok:** `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

### SsccContentRepository.java
**Package:** `com.example.epcis.infrastructure.persistence.inventory`

```java
List<SsccContentEntity> findBySsccUrn(String ssccUrn);
List<SsccContentEntity> findByChildEpc(String childEpc);
void deleteBySsccUrnAndChildEpcIn(String ssccUrn, List<String> childEpcs);
void deleteBySsccUrn(String ssccUrn); // für DELETE ohne childEPCs = alle löschen
boolean existsBySsccUrnAndChildEpc(String ssccUrn, String childEpc);
long countBySsccUrn(String ssccUrn);
```

### MovementHistoryEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.inventory`
**Annotation:** `@Entity`, `@Table(name = "movement_history")`

**Felder:**

| Spalte | Typ | Nullable | Beschreibung |
|---|---|---|---|
| id | BIGINT IDENTITY | NOT NULL | PK |
| epc_urn | VARCHAR(255) | NOT NULL | SGTIN oder SSCC URN |
| event_id | VARCHAR(255) | NOT NULL | eventID — Deduplizierungsschlüssel |
| event_type | VARCHAR(50) | NOT NULL | ObjectEvent / AggregationEvent |
| action | VARCHAR(20) | NOT NULL | ADD / OBSERVE / DELETE |
| biz_step | VARCHAR(255) | NULL | bizStep URI |
| disposition | VARCHAR(255) | NULL | disposition URI |
| biz_location | VARCHAR(255) | NULL | bizLocation SGLN |
| read_point | VARCHAR(255) | NULL | readPoint SGLN |
| event_time | TIMESTAMPTZ | NOT NULL | Zeitstempel des Events |
| recorded_at | TIMESTAMPTZ | NOT NULL | Zeitpunkt der Verarbeitung |

**Index:** `(epc_urn, event_time DESC)` — für performante History-Abfragen
**Unique:** `(epc_urn, event_id)` — Idempotenz: dasselbe Event nur einmal in History

### MovementHistoryRepository.java

```java
List<MovementHistoryEntity> findByEpcUrnOrderByEventTimeDesc(String epcUrn);

List<MovementHistoryEntity> findByEpcUrnAndEventTimeBetweenOrderByEventTimeDesc(
    String epcUrn, OffsetDateTime from, OffsetDateTime to);

// Idempotenz-Check: wurde dieses Event für diese EPC schon verarbeitet?
boolean existsByEpcUrnAndEventId(String epcUrn, String eventId);

List<MovementHistoryEntity> findByBizLocationAndEventTimeBetween(
    String bizLocation, OffsetDateTime from, OffsetDateTime to);
```

---

## 3. APPLICATION LAYER

### InventoryProcessorService.java
**Package:** `com.example.epcis.application.inventory`
**Annotation:** `@Service`, `@Transactional`

**Zweck:** Verarbeitet ein einzelnes EPCIS Event und aktualisiert EPC State + SSCC Content + Movement History.

**Abhängigkeiten (Constructor Injection):**
- `EpcStateRepository`
- `SsccContentRepository`
- `MovementHistoryRepository`

**Methoden:**

```java
/**
 * Verarbeitet ein einzelnes EPCIS Event und aktualisiert den Inventory-State.
 *
 * Idempotenz: Wenn eventId bereits in movement_history vorhanden → skip, kein Fehler.
 *
 * ObjectEvent Verarbeitung:
 * - Für jede EPC in epcList:
 *   - MovementHistory Eintrag anlegen (wenn nicht schon vorhanden)
 *   - EpcState anlegen oder aktualisieren (REQ401 Update-Regel beachten)
 *
 * AggregationEvent Verarbeitung:
 * - ADD:    SGTINs der SSCC in sscc_content hinzufügen
 *           EpcState der SGTINs: sscc-Feld setzen (nur beim ersten Mal — REQ401)
 *           MovementHistory für jede SGTIN anlegen
 * - DELETE: SGTINs aus sscc_content entfernen
 *           Wenn childEPCs leer → ALLE SGTINs dieser SSCC entfernen (REQ215)
 *           MovementHistory für jede betroffene SGTIN anlegen
 * - OBSERVE: nur MovementHistory + EpcState-Update, kein sscc_content-Änderung
 *
 * REQ217 Out-of-Order Events:
 * - Out-of-Order Event = eventTime < last_event_time in epc_state
 * - IMMER in movement_history speichern
 * - ABER: bizLocation und lastEventId in epc_state NICHT aktualisieren
 * - Disposition-Timestamps NUR aktualisieren wenn neuer als bestehender Timestamp
 *
 * @param event verarbeitetes Domain-Event (bereits gefiltert durch Phase 1)
 */
public void process(EpcisEvent event);

/**
 * Aktualisiert EpcState für eine einzelne EPC.
 * Private Hilfsmethode — wird von process() aufgerufen.
 *
 * Update-Logik (REQ401):
 * 1. EpcState existiert nicht → neu anlegen mit allen Feldern
 * 2. EpcState existiert + event ist NEUER → bizLocation, lastEventId, currentStatus updaten
 * 3. EpcState existiert + event ist ÄLTER (out-of-order) → nur Disposition-Timestamp
 *    updaten wenn neuer als bestehender Wert für diese Disposition
 */
private void updateEpcState(String epcUrn, EpcisEvent event, boolean isOutOfOrder);

/**
 * Berechnet den currentStatus aus bizStep und disposition eines Events.
 * Disposition hat Vorrang wenn gesetzt, sonst bizStep.
 *
 * Mapping:
 * disposition "in_transit"                    → "in_transit"
 * disposition "in_progress"                   → "in_progress"
 * disposition "encoded"                       → "encoded"
 * disposition "accessible_for_customer"       → "accessible_for_customer"
 * disposition "available_not_accessible..."   → "available_not_accessible_for_customer"
 * bizStep "shipping"                          → "in_transit" (wenn kein disposition)
 * bizStep "receiving"                         → "in_progress" (wenn kein disposition)
 * bizStep "encoding"                          → "encoded" (wenn kein disposition)
 */
private String resolveCurrentStatus(EpcisEvent event);

/**
 * Prüft ob ein Event out-of-order ist für eine bestimmte EPC.
 * Out-of-order = eventTime des neuen Events < last_event_time in epc_state
 */
private boolean isOutOfOrder(String epcUrn, OffsetDateTime eventTime);
```

### InventoryQueryService.java
**Package:** `com.example.epcis.application.inventory`
**Annotation:** `@Service`

**Methoden:**

```java
/**
 * Aktuellen Zustand einer SGTIN abfragen.
 * @throws EpcNotFoundException wenn EPC nicht bekannt (→ HTTP 404)
 */
public EpcState getEpcState(String epcUrn);

/**
 * Alle SGTINs an einem bestimmten Standort.
 * @param gln SGLN URI des Standorts
 * @return Liste der EpcState-Objekte — leer wenn keine SGTINs am Standort
 */
public List<EpcState> getStockAtLocation(String gln);

/**
 * Anzahl Einheiten einer GTIN (alle Serialnummern).
 * GTIN = SGTIN ohne letzte Stelle (Seriennummer).
 * Beispiel: gtin="urn:epc:id:sgtin:4056019.010532"
 *           → sucht alle EPCs die mit diesem Prefix beginnen
 * @param gtin GTIN-Prefix (ohne Seriennummer)
 * @param gln  Optional — nur an diesem Standort zählen
 */
public long getQuantityByGtin(String gtin, String gln);

/**
 * Inhalt einer SSCC (Palette).
 * @throws EpcNotFoundException wenn SSCC nicht bekannt (→ HTTP 404)
 */
public SsccContent getPalletContent(String ssccUrn);

/**
 * Vollständige Bewegungshistorie einer EPC.
 * @param epcUrn  SGTIN oder SSCC URI
 * @param from    Optional — Zeitraum von
 * @param to      Optional — Zeitraum bis
 * @param limit   Max Anzahl Einträge (default 100, max 1000)
 */
public List<MovementHistoryEntity> getMovementHistory(
    String epcUrn, OffsetDateTime from, OffsetDateTime to, int limit);
```

**Exception:**
```java
// Neue Exception in infrastructure/xml oder eigenes Package:
public class EpcNotFoundException extends RuntimeException {
    public EpcNotFoundException(String epcUrn) {
        super("EPC not found: " + epcUrn);
    }
}
// → GlobalExceptionHandler fängt diese und gibt HTTP 404 zurück
```

### InventoryRebuildService.java
**Package:** `com.example.epcis.application.inventory`
**Annotation:** `@Service`

**Zweck:** Berechnet den gesamten Inventory-State neu aus allen gespeicherten Events.
Wird benötigt wenn der State inkonsistent ist oder nach einem Reset.

```java
/**
 * Leert epc_state, sscc_content und movement_history komplett
 * und verarbeitet alle Events aus epcis_event neu — in chronologischer Reihenfolge.
 *
 * Wichtig:
 * - Events werden nach event_time ASC verarbeitet (älteste zuerst)
 * - Idempotent — kann mehrfach aufgerufen werden
 * - Läuft synchron — bei großen Datenmengen kann das lange dauern
 * - Gibt RebuildResult zurück mit Statistiken
 *
 * @return RebuildResult mit processed, skipped, errors, durationMs
 */
@Transactional
public RebuildResult rebuild();

@Getter @Builder
public static class RebuildResult {
    private final int eventsProcessed;
    private final int eventsSkipped;
    private final int errors;
    private final long durationMs;
    private final OffsetDateTime completedAt;
}
```

---

## 4. API LAYER

### InventoryController.java
**Package:** `com.example.epcis.api.inventory`
**Annotation:** `@RestController`, `@RequestMapping("/inventory")`

**Alle Endpunkte:**

```
GET  /inventory/epc/{epcUrn}
     → EpcStateResponse — aktueller Zustand der SGTIN
     → 404 wenn nicht bekannt

GET  /inventory/stock
     ?gln=urn:epc:id:sgln:4056019.00033.0   ← Pflicht
     → StockResponse — alle SGTINs an diesem Standort

GET  /inventory/quantity
     ?gtin=urn:epc:id:sgtin:4056019.010532  ← Pflicht (ohne Seriennummer)
     ?gln=urn:epc:id:sgln:...               ← Optional — nur an diesem Standort
     → { "gtin": "...", "gln": "...", "quantity": 42 }

GET  /inventory/pallet/{ssccUrn}
     → PalletResponse — Inhalt der SSCC
     → 404 wenn nicht bekannt

GET  /inventory/history/{epcUrn}
     ?from=2024-01-01T00:00:00+01:00        ← Optional
     ?to=2024-12-31T23:59:59+01:00          ← Optional
     ?limit=100                              ← Optional, default 100, max 1000
     → MovementHistoryResponse — Bewegungshistorie

POST /inventory/rebuild
     → RebuildResponse — Statistiken des Rebuild-Vorgangs
     → Achtung: kann bei großen Datenmengen lange dauern
```

**epcUrn und ssccUrn in URL-Pfaden:**
URNs enthalten Doppelpunkte — Tomcat muss so konfiguriert werden dass Slashes und
Sonderzeichen in Pfadparametern erlaubt sind, oder alternativ als Query-Parameter übergeben:

```java
// EMPFEHLUNG: epc als Query-Parameter statt Pfadparameter
GET /inventory/epc?epc=urn:epc:id:sgtin:...
GET /inventory/pallet?sscc=urn:epc:id:sscc:...
GET /inventory/history?epc=urn:epc:id:sgtin:...
```

**Methoden:**

```java
@GetMapping("/epc")
public ResponseEntity<EpcStateResponse> getEpcState(
    @RequestParam String epc);

@GetMapping("/stock")
public ResponseEntity<StockResponse> getStock(
    @RequestParam String gln);

@GetMapping("/quantity")
public ResponseEntity<QuantityResponse> getQuantity(
    @RequestParam String gtin,
    @RequestParam(required = false) String gln);

@GetMapping("/pallet")
public ResponseEntity<PalletResponse> getPallet(
    @RequestParam String sscc);

@GetMapping("/history")
public ResponseEntity<MovementHistoryResponse> getHistory(
    @RequestParam String epc,
    @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
    @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
    @RequestParam(defaultValue = "100") int limit);

@PostMapping("/rebuild")
public ResponseEntity<RebuildResponse> rebuild();
```

### Response DTOs

**EpcStateResponse.java**
```java
@Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class EpcStateResponse {
    private final String epcUrn;
    private final String currentStatus;
    private final String bizLocation;
    private final String lastEventId;
    private final String sscc;
    private final OffsetDateTime lastEventTime;
    private final OffsetDateTime createDate;
    private final OffsetDateTime updateDate;
    // Disposition-Timestamps
    private final OffsetDateTime encodedAt;
    private final OffsetDateTime inProgressAt;
    private final OffsetDateTime inTransitAt;
    private final OffsetDateTime accessibleForCustomerAt;
    private final OffsetDateTime availableNotAccessibleForCustomerAt;
    private final OffsetDateTime retiredAt;
    private final OffsetDateTime soldAt;
}
```

**StockResponse.java**
```java
@Getter @Builder
public class StockResponse {
    private final String gln;
    private final int totalCount;
    private final List<EpcStateResponse> items;
}
```

**PalletResponse.java**
```java
@Getter @Builder
public class PalletResponse {
    private final String ssccUrn;
    private final String bizLocation;
    private final int childCount;
    private final List<String> childEpcs;
    private final OffsetDateTime lastUpdated;
}
```

**MovementHistoryResponse.java**
```java
@Getter @Builder
public class MovementHistoryResponse {
    private final String epcUrn;
    private final int totalCount;
    private final List<MovementEntry> movements;

    @Getter @Builder
    public static class MovementEntry {
        private final String eventId;
        private final String eventType;
        private final String action;
        private final String bizStep;
        private final String disposition;
        private final String bizLocation;
        private final String readPoint;
        private final OffsetDateTime eventTime;
    }
}
```

**RebuildResponse.java**
```java
@Getter @Builder
public class RebuildResponse {
    private final int eventsProcessed;
    private final int eventsSkipped;
    private final int errors;
    private final long durationMs;
    private final OffsetDateTime completedAt;
}
```

---

## 5. GLOBALE FEHLERBEHANDLUNG — ERGÄNZUNG

In `GlobalExceptionHandler.java` muss eine neue Handler-Methode ergänzt werden:

```java
@ExceptionHandler(EpcNotFoundException.class)
public ResponseEntity<Map<String, String>> handleEpcNotFoundException(EpcNotFoundException ex) {
    log.warn("EPC not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
}
```

---

## 6. KONFIGURATION

### InventoryConfig.java
**Package:** `com.example.epcis.config`
**Annotation:** `@Configuration`, `@ConfigurationProperties(prefix = "epcis.inventory")`

```java
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.inventory")
public class InventoryConfig {
    private boolean enabled = true;
    private int maxHistoryLimit = 1000;
    private int defaultHistoryLimit = 100;
    private boolean rebuildEnabled = true;
}
```

### application.yml — Ergänzungen
```yaml
epcis:
  output:
    directory: ./output/events       # BESTEHEND
  schema:
    path: classpath:xsd/EPCglobal-epcis-1_2.xsd  # BESTEHEND
  capture:                           # BESTEHEND Phase 1
    max-events-per-request: 1000
    max-xml-size-kb: 10240
    audit-enabled: true
    file-writer-enabled: true
  inventory:                         # NEU Phase 2
    enabled: true
    max-history-limit: 1000
    default-history-limit: 100
    rebuild-enabled: true
```

---

## 7. DATENBANK — NEUE TABELLEN

Hibernate erstellt diese automatisch durch `ddl-auto: update`.

```sql
-- EPC State Tabelle (REQ401)
CREATE TABLE epc_state (
    id                           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    epc_urn                      VARCHAR(255) NOT NULL UNIQUE,
    current_status               VARCHAR(100),
    biz_location                 VARCHAR(255),
    last_event_id                VARCHAR(255),
    last_event_time              TIMESTAMPTZ,
    sscc                         VARCHAR(255),
    create_date                  TIMESTAMPTZ NOT NULL,
    update_date                  TIMESTAMPTZ NOT NULL,
    encoded_at                   TIMESTAMPTZ,
    in_progress_at               TIMESTAMPTZ,
    in_transit_at                TIMESTAMPTZ,
    accessible_for_customer_at   TIMESTAMPTZ,
    available_not_accessible_at  TIMESTAMPTZ,
    retired_at                   TIMESTAMPTZ,
    sold_at                      TIMESTAMPTZ
);

CREATE INDEX idx_epc_state_biz_location ON epc_state(biz_location);
CREATE INDEX idx_epc_state_sscc         ON epc_state(sscc);
CREATE INDEX idx_epc_state_status       ON epc_state(current_status);

-- SSCC Content Tabelle
CREATE TABLE sscc_content (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    sscc_urn     VARCHAR(255) NOT NULL,
    child_epc    VARCHAR(255) NOT NULL,
    biz_location VARCHAR(255),
    added_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (sscc_urn, child_epc)
);

CREATE INDEX idx_sscc_content_sscc      ON sscc_content(sscc_urn);
CREATE INDEX idx_sscc_content_child_epc ON sscc_content(child_epc);

-- Movement History Tabelle
CREATE TABLE movement_history (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    epc_urn      VARCHAR(255) NOT NULL,
    event_id     VARCHAR(255) NOT NULL,
    event_type   VARCHAR(50)  NOT NULL,
    action       VARCHAR(20)  NOT NULL,
    biz_step     VARCHAR(255),
    disposition  VARCHAR(255),
    biz_location VARCHAR(255),
    read_point   VARCHAR(255),
    event_time   TIMESTAMPTZ  NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (epc_urn, event_id)
);

CREATE INDEX idx_movement_history_epc_time
    ON movement_history(epc_urn, event_time DESC);
CREATE INDEX idx_movement_history_biz_location
    ON movement_history(biz_location);
```

---

## 8. WIE WIRD DER INVENTORY PROCESSOR GETRIGGERT?

**Phase 2 — Kein Kafka, kein Event Listener.**
Der Processor wird direkt aus dem `CaptureEventUseCase` aufgerufen nach erfolgreicher Persistenz.

**Ergänzung in CaptureEventUseCase.java** (einzige Änderung an Phase-1-Code):

```java
// CaptureEventUseCase.java — nach dem databaseWriter.write() Aufruf:
inventoryProcessorService.process(filteredEvent);
```

Der `InventoryProcessorService` wird per Constructor Injection in `CaptureEventUseCase` eingebunden.

**Warum direkt statt Event-Listener:**
- Einfacher, weniger Infrastruktur
- Transaktionssicher — Capture + Inventory in derselben DB-Transaktion
- In Phase 3 kann das durch Kafka ersetzt werden ohne den Capture-Code zu ändern

---

## 9. VERARBEITUNGSLOGIK DETAIL — WICHTIGE EDGE CASES

### ObjectEvent — ADD
```
Für jede EPC in epcList:
  1. MovementHistory anlegen (wenn eventId noch nicht für diese EPC vorhanden)
  2. EpcState anlegen oder aktualisieren:
     - Wenn nicht existiert → CREATE mit allen Feldern
     - Wenn existiert und event.eventTime > epc_state.last_event_time:
         → UPDATE: biz_location, last_event_id, last_event_time, current_status
         → UPDATE: Disposition-Timestamp wenn Disposition des Events gesetzt
     - Wenn existiert und event.eventTime <= epc_state.last_event_time (out-of-order):
         → NUR: Disposition-Timestamp updaten wenn neuer als bestehender Wert
         → NICHT: biz_location, last_event_id, current_status ändern
```

### ObjectEvent — OBSERVE
```
Identisch zu ADD — OBSERVE ändert den Bestand nicht aber aktualisiert den Status.
```

### ObjectEvent — DELETE
```
Für jede EPC in epcList:
  1. MovementHistory anlegen
  2. EpcState: current_status auf "deleted" setzen, biz_location löschen (null)
```

### AggregationEvent — ADD
```
Für jede EPC in childEPCs:
  1. MovementHistory anlegen
  2. sscc_content: Eintrag anlegen (sscc_urn=parentID, child_epc=EPC)
     - Wenn bereits in anderer SSCC → erst aus alter SSCC entfernen (REQ210)
  3. EpcState:
     - sscc-Feld setzen NUR wenn noch kein SSCC gesetzt (erster Packing Event — REQ401)
     - biz_location, current_status, last_event_id updaten (falls neuer)
```

### AggregationEvent — DELETE
```
Wenn childEPCs leer:
  - ALLE SGTINs dieser SSCC aus sscc_content entfernen (REQ215)
  - MovementHistory für jede entfernte SGTIN anlegen
Wenn childEPCs nicht leer:
  - Nur die genannten SGTINs aus sscc_content entfernen
  - MovementHistory für jede entfernte SGTIN anlegen
Wenn nach Entfernung SSCC leer → SSCC hat keine Einträge mehr (REQ215 — SSCC löschen ist implizit)
```

### AggregationEvent — OBSERVE
```
Keine sscc_content Änderungen.
Nur MovementHistory + EpcState Update für alle childEPCs (wie ObjectEvent OBSERVE).
```

---

## 10. TESTS — PFLICHT FÜR PHASE 2

### InventoryProcessorServiceTest.java
```
✅ ObjectEvent ADD → epc_state angelegt mit korrekten Feldern
✅ ObjectEvent ADD → movement_history Eintrag angelegt
✅ ObjectEvent ADD zweimal mit gleicher eventId → idempotent, kein Duplikat
✅ ObjectEvent OBSERVE → epc_state aktualisiert, kein neuer Eintrag wenn älter
✅ ObjectEvent mit out-of-order eventTime → biz_location NICHT geändert
✅ AggregationEvent ADD → sscc_content Einträge angelegt
✅ AggregationEvent ADD → epc_state.sscc gesetzt (erster Packing Event)
✅ AggregationEvent ADD → epc_state.sscc NICHT überschrieben bei zweitem Packing
✅ AggregationEvent DELETE mit childEPCs → nur diese aus sscc_content entfernt
✅ AggregationEvent DELETE ohne childEPCs → ALLE aus sscc_content entfernt (REQ215)
✅ SGTIN in zwei AggregationEvent ADD → aus erster SSCC entfernt (REQ210)
✅ Disposition-Timestamp: in_transit_at gesetzt wenn disposition=in_transit
✅ Disposition-Timestamp: NICHT überschrieben wenn neuer Event älter (out-of-order)
```

### InventoryQueryServiceTest.java
```
✅ getEpcState — bekannte EPC → EpcState zurückgegeben
✅ getEpcState — unbekannte EPC → EpcNotFoundException
✅ getStockAtLocation — GLN mit SGTINs → korrekte Liste
✅ getStockAtLocation — GLN ohne SGTINs → leere Liste
✅ getQuantityByGtin — GTIN mit 3 Seriennummern → quantity=3
✅ getQuantityByGtin — mit GLN-Filter → nur SGTINs an diesem Standort
✅ getPalletContent — SSCC mit 5 SGTINs → PalletResponse.childCount=5
✅ getPalletContent — unbekannte SSCC → EpcNotFoundException
✅ getMovementHistory — EPC mit 3 Movements → Liste mit 3 Einträgen
✅ getMovementHistory — mit Zeitraum-Filter
```

### InventoryRebuildServiceTest.java
```
✅ rebuild — 3 Events in DB → 3 verarbeitet, epc_state korrekt
✅ rebuild — zweimal aufgerufen → gleicher Endzustand (Idempotenz)
✅ rebuild — out-of-order Events → korrekter Endzustand
```

### InventoryControllerIntegrationTest.java
```
✅ GET /inventory/epc?epc=... → 200 + EpcStateResponse
✅ GET /inventory/epc?epc=UNBEKANNT → 404
✅ GET /inventory/stock?gln=... → 200 + StockResponse
✅ GET /inventory/quantity?gtin=... → 200 + Anzahl
✅ GET /inventory/pallet?sscc=... → 200 + PalletResponse
✅ GET /inventory/pallet?sscc=UNBEKANNT → 404
✅ GET /inventory/history?epc=... → 200 + MovementHistoryResponse
✅ POST /inventory/rebuild → 200 + RebuildResponse
```

---

## 11. NICHT IN PHASE 2 — EXPLIZIT AUSGESCHLOSSEN

| Requirement | Beschreibung | Phase |
|---|---|---|
| REQ201 | SGTIN State Matrix — erlaubte/verbotene Statusübergänge | Phase 3 |
| REQ202 | Store Receiving Threshold | Phase 3 |
| REQ205 | Aggregationsregeln komplex (nur Basis in Phase 2) | Phase 3 |
| REQ208 | Auto-Disaggregation bei vollständigem Store-Receiving | Phase 3 |
| REQ209 | Void Sale Events | Phase 3 |
| REQ210 | Vollständige SGTIN-Eindeutigkeitsregel (Basis in Phase 2) | Phase 3 |
| REQ212 | Departing Event erzeugen | Phase 3 |
| REQ301–316 | Alle Downstream-Integrationen (GSPM, DWH, ERP, EWM) | Phase 3 |
| Kafka | Event Streaming | Phase 3 |

---

## 12. DEFINITION OF DONE PHASE 2

```
☐ epc_state Tabelle wird korrekt befüllt bei jedem Capture
☐ sscc_content Tabelle wird korrekt befüllt/geleert bei AggregationEvents
☐ movement_history Tabelle enthält vollständige Bewegungshistorie
☐ Idempotenz bestätigt — dasselbe Event zweimal senden → gleicher Endzustand
☐ Out-of-Order Events: biz_location wird nicht überschrieben (REQ217)
☐ GET /inventory/epc — korrekte EpcStateResponse inkl. Disposition-Timestamps
☐ GET /inventory/stock — korrekte Liste aller SGTINs an GLN
☐ GET /inventory/pallet — korrekte Liste aller childEPCs
☐ GET /inventory/history — vollständige Bewegungshistorie
☐ POST /inventory/rebuild — Rebuild läuft durch, Ergebnis konsistent
☐ Alle Pflicht-Tests grün
☐ Phase-1-Tests weiterhin grün (keine Regression)
☐ GlobalExceptionHandler um EpcNotFoundException erweitert
☐ application.yml um epcis.inventory Block ergänzt
```

---

## 13. SMOKE-TEST NACH PHASE 2

```bash
# 1. AggregationEvent senden (packen)
curl -X POST http://localhost:8080/epcis/capture/events \
  -H "Content-Type: application/xml" \
  -H "X-EPCIS-Source-ID: DC-HAMBURG" \
  -d '<epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" schemaVersion="1.2" creationDate="2024-01-15T09:00:00+02:00">
    <EPCISBody><EventList>
      <AggregationEvent>
        <eventTime>2024-01-15T09:00:00+02:00</eventTime>
        <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
        <parentID>urn:epc:id:sscc:4290025.0111111122</parentID>
        <childEPCs>
          <epc>urn:epc:id:sgtin:4290025.077551.1</epc>
          <epc>urn:epc:id:sgtin:4290025.077551.2</epc>
        </childEPCs>
        <action>ADD</action>
        <bizStep>urn:epcglobal:cbv:bizstep:packing</bizStep>
        <disposition>urn:epcglobal:cbv:disp:in_progress</disposition>
        <readPoint><id>urn:epc:id:sgln:4290025.00009.0</id></readPoint>
      </AggregationEvent>
    </EventList></EPCISBody>
  </epcis:EPCISDocument>'

# 2. EPC State abfragen
curl "http://localhost:8080/inventory/epc?epc=urn:epc:id:sgtin:4290025.077551.1"
# Erwartung: currentStatus=in_progress, sscc=urn:epc:id:sscc:4290025.0111111122

# 3. Pallet abfragen
curl "http://localhost:8080/inventory/pallet?sscc=urn:epc:id:sscc:4290025.0111111122"
# Erwartung: childCount=2

# 4. Bewegungshistorie abfragen
curl "http://localhost:8080/inventory/history?epc=urn:epc:id:sgtin:4290025.077551.1"
# Erwartung: 1 Eintrag mit action=ADD, bizStep=packing
```

---

## 14. ERGÄNZUNG — AVAILABLE MERCHANDISE (REQ301)

### Konzept "Available Merchandise"

**Definition gemäß REQ301:**
Verfügbare Ware = SGTINs in folgenden Dispositionen:
- `urn:epcglobal:cbv:disp:sellable_accessible`
- `urn:epcglobal:cbv:disp:sellable_not_accessible`

**Nicht verfügbare Ware** (explizit ausgeschlossen):
- `retail_sold`
- `stolen`
- `unknown`
- `damaged`
- `destroyed`
- `in_progress`
- `non_sellable_other`
- `in_transit`

**Wichtig (REQ301):** Die Liste der "Available Merchandise" Dispositionen muss
konfigurierbar sein — Low Effort Configuration on Demand.

---

### 14.1 Ergänzung InventoryConfig.java

```java
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.inventory")
public class InventoryConfig {
    private boolean enabled = true;
    private int maxHistoryLimit = 1000;
    private int defaultHistoryLimit = 100;
    private boolean rebuildEnabled = true;

    /**
     * REQ301: Konfigurierbare Liste der "Available Merchandise" Dispositionen.
     * Standardwerte gemäß C&A Definition.
     * Kann ohne Code-Änderung in application.yml überschrieben werden.
     */
    private List<String> availableMerchandiseDispositions = List.of(
        "urn:epcglobal:cbv:disp:sellable_accessible",
        "urn:epcglobal:cbv:disp:sellable_not_accessible"
    );
}
```

### 14.2 Ergänzung application.yml

```yaml
epcis:
  inventory:
    enabled: true
    max-history-limit: 1000
    default-history-limit: 100
    rebuild-enabled: true
    available-merchandise-dispositions:
      - urn:epcglobal:cbv:disp:sellable_accessible
      - urn:epcglobal:cbv:disp:sellable_not_accessible
```

---

### 14.3 Ergänzung GET /inventory/stock

```
GET /inventory/stock
    ?gln=urn:epc:id:sgln:4056019.00033.SALESFLOOR   ← Pflicht
    ?availableOnly=false                              ← Optional, default: false
                                                         true = nur verfügbare Ware

Wenn availableOnly=true:
  → nur SGTINs deren current_status in available-merchandise-dispositions Liste ist
Wenn availableOnly=false (default):
  → alle SGTINs am Standort unabhängig vom Status
```

### 14.4 Ergänzung GET /inventory/quantity

```
GET /inventory/quantity
    ?gtin=urn:epc:id:sgtin:4056019.010532   ← Pflicht
    ?gln=urn:epc:id:sgln:...                ← Optional
    ?availableOnly=true                      ← Optional, default: TRUE
                                               (Quantity = typischerweise nur verfügbare Ware)
```

**Warum default=true für quantity:** Die Quantity-Abfrage wird primär für
Bestandsabfragen verwendet (wieviel verfügbare Ware). Für Gesamt-Inventory
kann auf `availableOnly=false` umgestellt werden.

---

### 14.5 Neuer Endpunkt: GET /inventory/available-quantity

Aggregiert verfügbare Mengen pro GTIN und SGLN — exakt das Format das GSPM benötigt
(REQ301, DM RFIDInventory2GSPM):

```
GET /inventory/available-quantity
    ?gln=urn:epc:id:sgln:4056019.00033.0   ← Optional — nur für diesen Store
    → AvailableQuantityResponse
```

**Response Format (entspricht DM RFIDInventory2GSPM):**
```json
{
  "timestamp": "2024-01-15T10:30:00+01:00",
  "messageId": "3963059d-1733-4474-8177-3e0a13ae572d",
  "locations": [
    {
      "sgln": "urn:epc:id:sgln:4056019.00033.SALESFLOOR",
      "items": [
        { "gtin": "4060983645083", "qty": 1 },
        { "gtin": "4056019010499", "qty": 7 }
      ]
    },
    {
      "sgln": "urn:epc:id:sgln:4056019.00033.BACKROOM",
      "items": [
        { "gtin": "4060983645083", "qty": 3 }
      ]
    }
  ]
}
```

**SGTIN → GTIN-13 Konvertierung:**
SGTIN URN: `urn:epc:id:sgtin:4056019.010532.4293918790`
GTIN-13:   `4056019010532` (Company Prefix + Item Reference, ohne Seriennummer)
Conversion: den mittleren Teil extrahieren und zu GTIN-13 formatieren.

**AvailableQuantityResponse.java:**
```java
@Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailableQuantityResponse {
    private final OffsetDateTime timestamp;
    private final String messageId;
    private final List<LocationQuantity> locations;

    @Getter @Builder
    public static class LocationQuantity {
        private final String sgln;
        private final List<GtinQuantity> items;
    }

    @Getter @Builder
    public static class GtinQuantity {
        private final String gtin;   // GTIN-13 numerisch
        private final int qty;
    }
}
```

---

### 14.6 SGTIN zu GTIN-13 Konvertierung — Hilfsmethode

**Package:** `com.example.epcis.application.inventory`

```java
/**
 * Konvertiert eine SGTIN URN zu einer GTIN-13.
 *
 * Eingabe:  urn:epc:id:sgtin:4056019.010532.4293918790
 * Ausgabe:  4056019010532
 *
 * Algorithmus:
 * 1. Prefix "urn:epc:id:sgtin:" entfernen
 * 2. String an "." splitten → [companyPrefix, itemReference, serial]
 * 3. companyPrefix + itemReference zusammenführen → GTIN ohne Prüfziffer
 * 4. GTIN-13 = companyPrefix + itemReference (13 Stellen, mit führenden Nullen)
 *
 * Ungültige GTINs (die sich nicht konvertieren lassen) werden verworfen
 * gemäß eecc-canda-inventory v1.0.0 Release Notes.
 *
 * @param sgtinUrn SGTIN als URN
 * @return GTIN-13 als String, oder empty() wenn ungültig
 */
public static Optional<String> sgtinToGtin13(String sgtinUrn);
```

---

### 14.7 Ergänzung EpcStateRepository — für Available Merchandise Abfragen

```java
/**
 * Alle SGTINs an einem Standort mit bestimmten Dispositionen (Available Merchandise).
 * Wird für /inventory/stock?availableOnly=true verwendet.
 */
@Query("SELECT e FROM EpcStateEntity e " +
       "WHERE e.bizLocation LIKE :glnPrefix% " +
       "AND e.currentStatus IN :dispositions")
List<EpcStateEntity> findByBizLocationPrefixAndCurrentStatusIn(
    @Param("glnPrefix") String glnPrefix,
    @Param("dispositions") List<String> dispositions);

/**
 * Anzahl verfügbarer SGTINs pro SGLN — für Available Quantity Aggregation.
 * Gibt Map<SGLN, List<SGTIN>> zurück für GTIN-Aggregation.
 */
@Query("SELECT e FROM EpcStateEntity e " +
       "WHERE e.currentStatus IN :dispositions " +
       "AND (:gln IS NULL OR e.bizLocation LIKE :gln%)")
List<EpcStateEntity> findAvailableMerchandise(
    @Param("dispositions") List<String> dispositions,
    @Param("gln") String gln);
```

---

### 14.8 Ergänzung Tests für Available Merchandise

```
✅ getQuantityByGtin — availableOnly=true → nur sellable_accessible + sellable_not_accessible
✅ getQuantityByGtin — availableOnly=false → alle Dispositionen
✅ getStockAtLocation — availableOnly=true → nur verfügbare SGTINs
✅ getAvailableQuantity — GTIN-13 Konvertierung korrekt
✅ getAvailableQuantity — 2 SGTINs gleiche GTIN → qty=2
✅ sgtinToGtin13 — valide SGTIN → korrekte GTIN-13
✅ sgtinToGtin13 — ungültige SGTIN → empty()
✅ Konfiguration: available-merchandise-dispositions überschreibbar in application.yml
```

---

### 14.9 Ergänzung Definition of Done

```
☐ GET /inventory/stock?availableOnly=true filtert korrekt nach Available Merchandise
☐ GET /inventory/quantity gibt standardmäßig nur verfügbare Ware zurück
☐ GET /inventory/available-quantity gibt GSPM-kompatibles Format zurück
☐ SGTIN → GTIN-13 Konvertierung funktioniert für Standard-SGTINs
☐ available-merchandise-dispositions in application.yml konfigurierbar
☐ Alle neuen Tests grün
```
