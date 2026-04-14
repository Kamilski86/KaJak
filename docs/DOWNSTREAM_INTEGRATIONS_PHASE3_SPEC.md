# EPCIS Downstream Integrations — Phase 3 Implementierungsspezifikation
## Für Claude Code

---

## KONTEXT

**Projekt:** epcis-event-handler (Spring Boot 4.x, Java 17+, PostgreSQL 18, Maven)
**Phase 1:** Capture Service + Query API ✅
**Phase 2:** Inventory Service (EPC State DB) ✅
**Phase 3 Ziel:** Downstream-Systeme mit Daten versorgen.

**Was Phase 3 ist:**
Das System empfängt und speichert Events (Phase 1) und berechnet den Bestand (Phase 2).
Phase 3 liefert diese Daten an die Systeme die sie brauchen:
GSPM, SAP ERP, DWH, SAP EWM, Halo, ItemOptix, Mule Integration Layer.

**Wichtige Designentscheidung:**
Kein Kafka in Phase 3. Alle Downstream-Integrationen laufen über:
- **Scheduled Jobs** (Spring `@Scheduled`) für periodische Lieferungen
- **Event-Triggered HTTP Calls** für near-realtime Lieferungen
- **Outbox Pattern** für Zuverlässigkeit — nie direkt aus dem Capture-Pfad in externe Systeme schreiben

**State Matrix:** Nicht in Phase 3. Jedes Event wird so verarbeitet wie es reinkommt.

---

## PHASE 3 SCOPE — 3 BLÖCKE

### Block A — GSPM Integrationen (Priorität: HOCH)
- REQ301: RFID Inventory → GSPM (jede Minute, konfigurierbar)
- REQ302: RFID Shipping → GSPM (near-realtime, alle 5 Minuten, konfigurierbar)
- REQ303: RFID Arrival → GSPM (near-realtime, event-triggered)

### Block B — ERP + DWH + EWM Integrationen (Priorität: MITTEL)
- REQ308: Goods Receipt → SAP ERP via Mule (event-triggered bei Store Receiving)
- REQ305: Sales Information → DWH (periodisch)
- REQ306: Returns Information → DWH (periodisch)
- REQ309: Trolley SSCCs → SAP EWM (near-realtime, < 10 Sekunden)

### Block C — Subscription Service (Priorität: MITTEL)
- REQ310: EPCIS Subscription → Halo
- REQ311: EPCIS Subscription → Mule (Branch Transfer / RSTO)
- REQ314: EPCIS Subscription → ItemOptix (Long-Lived Bearer Token)
- REQ315: EPCIS Subscription → Mule (Receiving Events)
- REQ316: EPCIS Subscription → Mule (Stock-Loss Events)

---

## PROJEKTSTRUKTUR — ERWEITERUNG

```
com.example.epcis/
├── api/                               # BESTEHEND
├── application/
│   ├── capture/                       # BESTEHEND
│   ├── query/                         # BESTEHEND
│   ├── inventory/                     # BESTEHEND
│   └── downstream/                    # NEU Phase 3
│       ├── gspm/
│       │   ├── InventoryNotificationService.java   (REQ301)
│       │   ├── ShippingNotificationService.java    (REQ302)
│       │   └── ArrivalNotificationService.java     (REQ303)
│       ├── erp/
│       │   └── GoodsReceiptNotificationService.java (REQ308)
│       ├── dwh/
│       │   ├── SalesNotificationService.java       (REQ305)
│       │   └── ReturnsNotificationService.java     (REQ306)
│       ├── ewm/
│       │   └── TrolleyNotificationService.java     (REQ309)
│       └── subscription/
│           ├── SubscriptionService.java
│           ├── SubscriptionRegistry.java
│           └── SubscriptionDispatcher.java
├── domain/
│   └── model/
│       └── outbox/                    # NEU — Outbox Pattern
│           └── OutboxMessage.java
├── infrastructure/
│   └── persistence/
│       ├── outbox/                    # NEU
│       │   ├── OutboxEntity.java
│       │   └── OutboxRepository.java
│       └── subscription/              # NEU
│           ├── SubscriptionEntity.java
│           └── SubscriptionRepository.java
└── config/
    └── DownstreamConfig.java          # NEU
```

---

## 1. OUTBOX PATTERN — FUNDAMENT FÜR ALLE DOWNSTREAM

**Warum Outbox:**
Ohne Outbox-Pattern passiert folgendes:
1. Event wird in DB gespeichert
2. HTTP-Call an GSPM schlägt fehl
3. Event ist in DB aber GSPM hat es nicht → Daten-Inkonsistenz

Mit Outbox:
1. Event wird in DB gespeichert
2. Outbox-Eintrag wird in derselben Transaktion gespeichert
3. Separater Job liest Outbox und sendet → bei Fehler: Retry
4. Bei Erfolg: Outbox-Eintrag als gesendet markieren

### OutboxEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.outbox`
**Annotation:** `@Entity`, `@Table(name = "outbox_message")`

**Felder:**

| Spalte | Typ | Beschreibung |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| message_id | VARCHAR(36) UNIQUE | UUID — Idempotenz |
| message_type | VARCHAR(100) | z.B. "RFID_INVENTORY", "RFID_SHIPPING", "GOODS_RECEIPT" |
| target_system | VARCHAR(50) | z.B. "GSPM", "ERP", "DWH", "EWM" |
| payload | TEXT | JSON-Payload der zu sendenden Nachricht |
| status | VARCHAR(20) | PENDING / SENT / FAILED |
| created_at | TIMESTAMPTZ | Erstellungszeitpunkt |
| sent_at | TIMESTAMPTZ | NULL solange nicht gesendet |
| retry_count | INT | Anzahl Sendeversuche |
| last_error | TEXT | Letzter Fehlertext |
| correlation_id | VARCHAR(255) | z.B. SSCC-URN für Shipping, eventId |

### OutboxRepository.java

```java
List<OutboxEntity> findByStatusAndTargetSystemOrderByCreatedAtAsc(
    String status, String targetSystem);

List<OutboxEntity> findByStatusOrderByCreatedAtAsc(String status);

// Für Idempotenz — wurde diese Nachricht bereits gesendet?
boolean existsByMessageId(String messageId);

// Cleanup — gesendete Messages älter als X Tage
void deleteByStatusAndSentAtBefore(String status, OffsetDateTime before);
```

---

## 2. BLOCK A — GSPM INTEGRATIONEN

### 2.1 REQ301 — RFID Inventory → GSPM

**Trigger:** Spring `@Scheduled` — jede Minute (konfigurierbar)
**Logik:** Liest aktuellen Bestand aus `epc_state`, aggregiert auf GTIN-13 + SGLN-Ebene,
sendet nur GTINs deren Menge sich seit letzter Sendung geändert hat.

**Nachrichtenformat (DM RFIDInventory2GSPM):**
```json
{
  "timestamp": "2024-01-15T10:30:00+01:00",
  "message_id": "3963059d-1733-4474-8177-3e0a13ae572d",
  "locations": [
    {
      "sgln": "urn:epc:id:sgln:4056019.00033.SALESFLOOR",
      "items": [
        { "gtin": "4060983645083", "qty": 1 },
        { "gtin": "4056019010499", "qty": 7 }
      ]
    }
  ]
}
```

**Regeln:**
- Nur SGTINs in "Available Merchandise" Dispositionen (aus Phase 2 InventoryConfig)
- Nur SALESFLOOR und BACKROOM SGLNs (keine übergeordneten GLNs)
- Max 8000 GTINs pro Message — bei mehr: splitten in mehrere Messages
- Änderungsbasiert: nur GTINs die sich seit letztem Run geändert haben
- GTINs mit qty=0 werden ebenfalls gesendet (Bestand auf 0 gefallen)

**Tabelle für Änderungs-Tracking:**
```sql
CREATE TABLE inventory_snapshot (
    gtin          VARCHAR(50)  NOT NULL,
    sgln          VARCHAR(255) NOT NULL,
    last_qty      INT          NOT NULL DEFAULT 0,
    last_sent_at  TIMESTAMPTZ,
    PRIMARY KEY (gtin, sgln)
);
```

### InventoryNotificationService.java
**Package:** `com.example.epcis.application.downstream.gspm`
**Annotation:** `@Service`

```java
/**
 * REQ301: Sendet Inventory-Änderungen an GSPM.
 *
 * Ablauf:
 * 1. Aktuellen Bestand aus epc_state berechnen (Available Merchandise)
 * 2. Mit inventory_snapshot vergleichen → Änderungen ermitteln
 * 3. Outbox-Einträge für geänderte GTINs erstellen (max 8000 pro Message)
 * 4. inventory_snapshot aktualisieren
 *
 * Der eigentliche HTTP-Call erfolgt durch OutboxProcessor.
 */
@Scheduled(fixedRateString = "${epcis.downstream.gspm.inventory-interval-ms:60000}")
public void scheduleInventoryUpdate();

// Wird auch direkt nach Capture aufgerufen für near-realtime Updates
@Transactional
public void processInventoryChange(String sgln, String gtin);
```

---

### 2.2 REQ302 — RFID Shipping → GSPM

**Trigger:** Spring `@Scheduled` — alle 5 Minuten (konfigurierbar)
**Auslöser:** Departing Event (bizStep=departing) oder Loading Event (bizStep=loading)
+ Branch Transfer Event (bizStep=shipping)

**Nachrichtenformat (DM RFIDShipping2GSPM):**
```json
{
  "rfidShipping": {
    "messageTimestamp": "2021-07-09T12:20:00.033+02:00",
    "messageId": "a27fc072-dd58-4691-9034-84950e80ae89",
    "source": "urn:epc:id:sgln:4062847.00022.0",
    "destination": "urn:epc:id:sgln:4056019.00044.0",
    "shippingTimestamp": "2021-07-09T10:14:26.235Z",
    "sscc": "urn:epc:id:sscc:4711000.0000000082",
    "items": [
      { "gtin": "04063958855012", "qty": 1 }
    ]
  }
}
```

**Regeln:**
- Trigger: Departing Event oder Loading Event mit SSCC in epcList/parentID
- SGTINs werden auf GTIN-13 + qty aggregiert
- source = readPoint des Events
- destination = destinationList des Events
- Wenn zweites Event für dieselbe SSCC mit GLEICHER destination → KEINE neue Message
- Wenn zweites Event für dieselbe SSCC mit ANDERER destination → neue Message

**Tabelle für Shipping-Tracking:**
```sql
CREATE TABLE shipping_sent (
    sscc            VARCHAR(255) PRIMARY KEY,
    last_destination VARCHAR(255),
    last_sent_at    TIMESTAMPTZ
);
```

---

### 2.3 REQ303 — RFID Arrival → GSPM

**Trigger:** Event-getriggert — direkt nach Receiving Event in Store
**Bedingung:** NUR senden wenn ALLE SGTINs der advisierten SSCC empfangen wurden

**Nachrichtenformat (DM RFIDArrival2GSPM):**
```json
{
  "rfidArrival": {
    "messageTimestamp": "2021-07-09T12:28:35+02:00",
    "messageId": "39d6efcc-8c4c-406d-9d03-7bac480c33f9",
    "destination": "urn:epc:id:sgln:4056019.00033.SALESFLOOR",
    "receivingTimestamp": "2021-07-09T10:28:29.208Z",
    "sscc": "urn:epc:id:sscc:4711000.0000000082",
    "items": [
      { "gtin": "04063958855012", "qty": 1 }
    ]
  }
}
```

**Regeln:**
- Nur bei Store Receiving Event (bizStep=receiving, Standort=Store-GLN)
- arrival location = bizLocation des Receiving Events
- SGTINs aus der SSCC-Aggregation (sscc_content Tabelle aus Phase 2)
- SGTINs → GTIN-13 + qty aggregiert
- NUR senden wenn Vollständigkeit bestätigt (vereinfacht in Phase 3:
  bei jedem Receiving Event senden — vollständige Threshold-Logik ist Phase 5)

---

## 3. BLOCK B — ERP + DWH + EWM

### 3.1 REQ308 — Goods Receipt → SAP ERP via Mule

**Trigger:** Event-getriggert — direkt nach Store Receiving Event
**Bedingung:** Wenn SSCC in Store empfangen wird

**Nachrichtenformat (DM GoodsReceipt2ERP):**
```json
{
  "sscc": "123456789123456789",
  "receivingTime": "2020-09-09T12:11:10.123Z",
  "receivingTimeZoneOffset": "UTC+01:00",
  "messageId": "2963059d-1733-4474-8177-3e0a13ae5721"
}
```

**Regeln:**
- SSCC im numerischen Format (nicht URN) — aus SSCC-URN konvertieren
- Trigger: AggregationEvent ADD mit bizStep=receiving in Store
- Pro SSCC nur einmal senden

---

### 3.2 REQ305 — Sales Information → DWH

**Trigger:** Spring `@Scheduled` — periodisch (konfigurierbar, default: alle 5 Minuten)
**Auslöser:** Selling Event (bizStep=retail_selling, disposition=retail_sold)

**Nachrichtenformat (DM RFIDSales2DWH):**
```json
{
  "messageTimestamp": "2020-07-03T09:44:05.180+01:00",
  "messageId": "befa348a-4a92-4334-841b-4457d59987b8",
  "gln": "urn:epc:id:sgln:4056019.00001.0",
  "soldTimestamp": "2020-07-03T09:44:02.071Z",
  "soldItems": ["4061506549017", "4061506549018"]
}
```

**Regeln:**
- Pro verkaufter SGTIN ein GTIN-13 Eintrag
- GLN aus readPoint des Selling Events
- Void Sale Events (disposition=retail_sold mit errorDeclaration) ignorieren

---

### 3.3 REQ306 — Returns Information → DWH

**Trigger:** Spring `@Scheduled` — periodisch
**Auslöser:** Returning Event (bizStep=accepting) + Retagging Event mit retagReason=customerReturn

**Nachrichtenformat (DM RFIDReturns2DWH):**
```json
{
  "messageTimestamp": "2020-07-03T09:44:05.180+01:00",
  "messageId": "befa348a-4a92-4334-841b-4457d59987b8",
  "gln": "urn:epc:id:sgln:4056019.00001.0",
  "returnTimestamp": "2020-07-03T09:44:02.071Z",
  "returnedItems": ["4061506549017"]
}
```

---

### 3.4 REQ309 — Trolley SSCCs → SAP EWM

**Trigger:** Event-getriggert — direkt nach DC Trolley Read Event
**SLA:** < 10 Sekunden (REQ309 explizit)
**Auslöser:** TrolleyReadEvent (bizStep=staging_outbound)

**Logik:**
1. Trolley Read Event kommt rein mit SGTINs
2. Lookup: Welche SSCCs enthalten diese SGTINs? (aus sscc_content Tabelle)
3. SSCCs + TrolleyID → an EWM senden

**Wegen 10-Sekunden SLA:** Kein Scheduled Job — direkter HTTP Call im Capture-Pfad
via Outbox mit sehr kurzem Poll-Intervall (1 Sekunde).

---

## 4. BLOCK C — SUBSCRIPTION SERVICE

**Was ein Subscription ist:**
Ein externer Client registriert sich für bestimmte Event-Typen.
Wenn ein passendes Event reinkommt, wird es an die registrierte URL gepusht.

### 4.1 Subscription Datenmodell

### SubscriptionEntity.java
**Package:** `com.example.epcis.infrastructure.persistence.subscription`
**Annotation:** `@Entity`, `@Table(name = "epcis_subscription")`

**Felder:**

| Spalte | Typ | Beschreibung |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| subscription_id | VARCHAR(100) UNIQUE | fachliche ID z.B. "HALO-GENERAL" |
| target_system | VARCHAR(100) | z.B. "HALO", "ITEMOPTIX", "MULE" |
| callback_url | VARCHAR(500) | HTTP Endpoint der Subscriber |
| auth_type | VARCHAR(20) | NONE / BEARER / BASIC |
| auth_token | VARCHAR(500) | Bearer Token (encrypted) |
| event_types | VARCHAR(500) | Komma-sep. Liste: "ObjectEvent,AggregationEvent" |
| biz_steps | TEXT | Filter: komma-sep. bizStep URIs, NULL = alle |
| dispositions | TEXT | Filter: komma-sep. disposition URIs, NULL = alle |
| biz_locations | TEXT | Filter: komma-sep. SGLN URIs, NULL = alle |
| read_points | TEXT | Filter: komma-sep. SGLN URIs, NULL = alle |
| active | BOOLEAN | true = aktiv |
| created_at | TIMESTAMPTZ | Erstellungszeitpunkt |
| last_triggered_at | TIMESTAMPTZ | letzter Trigger |

### 4.2 Subscription Dispatch

**Ablauf:**
1. Event wird captured und in `epcis_event` gespeichert
2. `SubscriptionDispatcher` prüft alle aktiven Subscriptions gegen das Event
3. Matching Subscriptions → Outbox-Eintrag erstellen (ein Eintrag pro Subscription)
4. `OutboxProcessor` sendet HTTP POST an callback_url

### SubscriptionService.java
```java
/**
 * Verwaltet Subscriptions.
 * CRUD-Operationen für Subscriptions.
 */
public SubscriptionEntity register(SubscriptionRegistration request);
public void deactivate(String subscriptionId);
public List<SubscriptionEntity> listAll();
public SubscriptionEntity getById(String subscriptionId);
```

### SubscriptionDispatcher.java
```java
/**
 * Wird nach jedem Capture aufgerufen.
 * Prüft alle aktiven Subscriptions gegen das Event und erstellt Outbox-Einträge.
 *
 * Filter-Logik:
 * - eventType muss passen (wenn gesetzt)
 * - bizStep muss in der Liste sein (wenn gesetzt)
 * - disposition muss passen (wenn gesetzt)
 * - bizLocation muss passen (wenn gesetzt) — SGLN-Prefix-Match
 * - readPoint muss passen (wenn gesetzt) — SGLN-Prefix-Match
 */
@Transactional
public void dispatch(EpcisEvent event, String eventJson);
```

### 4.3 Vordefinierte Subscriptions (werden beim App-Start angelegt)

```yaml
epcis:
  downstream:
    subscriptions:
      - id: HALO-GENERAL
        target: HALO
        callback-url: ${HALO_CALLBACK_URL:http://localhost:9001/epcis/events}
        auth-type: BEARER
        event-types: ObjectEvent,AggregationEvent
        biz-steps: "receiving,storing,cycle_counting,inspecting,entering_exiting,
                    retail_selling,accepting,staging_outbound,loading,departing,
                    shipping,commissioning"
        active: true
      - id: MULE-RSTO
        target: MULE
        callback-url: ${MULE_RSTO_URL:http://localhost:9002/rsto}
        auth-type: BEARER
        event-types: ObjectEvent
        biz-steps: "shipping"
        active: true
      - id: MULE-RECEIVING
        target: MULE
        callback-url: ${MULE_RECEIVING_URL:http://localhost:9002/receiving}
        auth-type: BEARER
        event-types: ObjectEvent
        biz-steps: "receiving"
        active: true
```

---

## 5. OUTBOX PROCESSOR

### OutboxProcessor.java
**Package:** `com.example.epcis.infrastructure`
**Annotation:** `@Component`

```java
/**
 * Liest PENDING Outbox-Einträge und sendet sie an die Zielsysteme.
 *
 * Läuft als Scheduled Job:
 * - Normal: alle 30 Sekunden
 * - EWM (Trolley): alle 1 Sekunde (wegen 10s SLA)
 *
 * Retry-Logik:
 * - Max 3 Retries
 * - Bei Fehler: status=FAILED, last_error setzen
 * - Nach max Retries: Alert loggen, manueller Eingriff nötig
 *
 * HTTP-Client: Spring's RestClient (Spring Boot 4.x)
 */
@Scheduled(fixedRateString = "${epcis.downstream.outbox.poll-interval-ms:30000}")
public void processOutbox();

@Scheduled(fixedRateString = "1000")  // 1 Sekunde für EWM
public void processEwmOutbox();
```

---

## 6. KONFIGURATION

### DownstreamConfig.java
**Package:** `com.example.epcis.config`

```java
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "epcis.downstream")
public class DownstreamConfig {

    private Gspm gspm = new Gspm();
    private Erp erp = new Erp();
    private Dwh dwh = new Dwh();
    private Ewm ewm = new Ewm();
    private Outbox outbox = new Outbox();

    @Getter @Setter
    public static class Gspm {
        private String baseUrl = "http://localhost:9010";
        private String inventoryPath = "/api/inventory";
        private String shippingPath = "/api/shipping";
        private String arrivalPath = "/api/arrival";
        private long inventoryIntervalMs = 60000;   // 1 Minute
        private long shippingIntervalMs = 300000;   // 5 Minuten
        private boolean enabled = false;             // default OFF — erst aktivieren wenn URL konfiguriert
    }

    @Getter @Setter
    public static class Erp {
        private String baseUrl = "http://localhost:9011";
        private String goodsReceiptPath = "/api/goodsreceipt";
        private boolean enabled = false;
    }

    @Getter @Setter
    public static class Dwh {
        private String baseUrl = "http://localhost:9012";
        private String salesPath = "/api/sales";
        private String returnsPath = "/api/returns";
        private long intervalMs = 300000;            // 5 Minuten
        private boolean enabled = false;
    }

    @Getter @Setter
    public static class Ewm {
        private String baseUrl = "http://localhost:9013";
        private String trolleyPath = "/api/trolley";
        private boolean enabled = false;
    }

    @Getter @Setter
    public static class Outbox {
        private long pollIntervalMs = 30000;
        private int maxRetries = 3;
        private int cleanupDays = 30;
    }
}
```

### application.yml — Ergänzungen
```yaml
epcis:
  downstream:
    gspm:
      base-url: ${GSPM_BASE_URL:http://localhost:9010}
      inventory-interval-ms: 60000
      shipping-interval-ms: 300000
      enabled: false   # true setzen wenn GSPM-URL konfiguriert
    erp:
      base-url: ${ERP_BASE_URL:http://localhost:9011}
      enabled: false
    dwh:
      base-url: ${DWH_BASE_URL:http://localhost:9012}
      interval-ms: 300000
      enabled: false
    ewm:
      base-url: ${EWM_BASE_URL:http://localhost:9013}
      enabled: false
    outbox:
      poll-interval-ms: 30000
      max-retries: 3
      cleanup-days: 30
```

**Wichtig:** Alle Downstream-Services sind default `enabled: false`.
Sie werden erst aktiviert wenn die URL des Zielsystems konfiguriert ist.
So startet die App auch ohne externe Systeme fehlerfrei.

---

## 7. DATENBANK — NEUE TABELLEN

```sql
-- Outbox Tabelle
CREATE TABLE outbox_message (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    message_id     VARCHAR(36)  NOT NULL UNIQUE,
    message_type   VARCHAR(100) NOT NULL,
    target_system  VARCHAR(50)  NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    correlation_id VARCHAR(255)
);
CREATE INDEX idx_outbox_status_target ON outbox_message(status, target_system);
CREATE INDEX idx_outbox_created       ON outbox_message(created_at);

-- Subscription Tabelle
CREATE TABLE epcis_subscription (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subscription_id     VARCHAR(100) NOT NULL UNIQUE,
    target_system       VARCHAR(100) NOT NULL,
    callback_url        VARCHAR(500) NOT NULL,
    auth_type           VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    auth_token          VARCHAR(500),
    event_types         VARCHAR(500),
    biz_steps           TEXT,
    dispositions        TEXT,
    biz_locations       TEXT,
    read_points         TEXT,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL,
    last_triggered_at   TIMESTAMPTZ
);

-- Inventory Snapshot (für Änderungs-Tracking REQ301)
CREATE TABLE inventory_snapshot (
    gtin         VARCHAR(50)  NOT NULL,
    sgln         VARCHAR(255) NOT NULL,
    last_qty     INT          NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ,
    PRIMARY KEY (gtin, sgln)
);

-- Shipping Sent (für Deduplication REQ302)
CREATE TABLE shipping_sent (
    sscc             VARCHAR(255) PRIMARY KEY,
    last_destination VARCHAR(255),
    last_sent_at     TIMESTAMPTZ
);
```

---

## 8. API LAYER — NEUE ENDPUNKTE

### SubscriptionController.java
**Package:** `com.example.epcis.api`
**Annotation:** `@RestController`, `@RequestMapping("/epcis/subscriptions")`

```
GET  /epcis/subscriptions              → alle Subscriptions auflisten
GET  /epcis/subscriptions/{id}         → einzelne Subscription
POST /epcis/subscriptions              → neue Subscription registrieren
PUT  /epcis/subscriptions/{id}/active  → aktivieren/deaktivieren
DELETE /epcis/subscriptions/{id}       → löschen
```

### OutboxController.java (Operations/Monitoring)
**Package:** `com.example.epcis.api`

```
GET  /ops/outbox/pending    → alle PENDING Messages
GET  /ops/outbox/failed     → alle FAILED Messages
POST /ops/outbox/{id}/retry → einzelne Message manuell retrigern
GET  /ops/outbox/stats      → Statistiken (pending/sent/failed pro target)
```

---

## 9. INTEGRATION MIT BESTEHENDEM CODE

**Einzige Änderung an Phase-1/2-Code:**

In `CaptureEventUseCase.java` — nach `inventoryProcessorService.process(event)`:

```java
// Nach inventoryProcessorService.process(event):
subscriptionDispatcher.dispatch(filteredEvent, json);
// Für event-getriggerte Downstream-Services:
if (isStoreReceivingEvent(filteredEvent)) {
    arrivalNotificationService.onReceivingEvent(filteredEvent);
    goodsReceiptNotificationService.onReceivingEvent(filteredEvent);
}
if (isTrolleyReadEvent(filteredEvent)) {
    trolleyNotificationService.onTrolleyReadEvent(filteredEvent);
}
```

---

## 10. TESTS — PFLICHT FÜR PHASE 3

### OutboxProcessorTest.java
```
✅ PENDING Message → HTTP Call → status=SENT
✅ HTTP Call schlägt fehl → retry_count erhöht
✅ Max Retries erreicht → status=FAILED, last_error gesetzt
✅ Bereits gesendete Message nicht nochmal senden
✅ EWM-Outbox wird separat und häufiger verarbeitet
```

### InventoryNotificationServiceTest.java
```
✅ Bestandsänderung → Outbox-Eintrag erstellt
✅ Keine Änderung → kein Outbox-Eintrag
✅ >8000 GTINs → Splitting in mehrere Messages
✅ GTIN mit qty=0 wird gesendet
✅ Nur Available-Merchandise Dispositionen
```

### ShippingNotificationServiceTest.java
```
✅ Departing Event → Outbox-Eintrag erstellt
✅ Zweites Event gleiche SSCC + gleiche Destination → KEIN neuer Eintrag
✅ Zweites Event gleiche SSCC + andere Destination → neuer Eintrag
✅ SGTINs korrekt zu GTIN-13 + qty aggregiert
```

### SubscriptionDispatcherTest.java
```
✅ Event matched Subscription → Outbox-Eintrag erstellt
✅ Event matched nicht → kein Outbox-Eintrag
✅ bizStep-Filter: Event mit passendem bizStep → match
✅ bizStep-Filter: Event mit anderem bizStep → kein match
✅ bizLocation-Filter: SGLN-Prefix-Match funktioniert
✅ Mehrere Subscriptions → ein Outbox-Eintrag pro Subscription
```

### SubscriptionControllerIntegrationTest.java
```
✅ POST /epcis/subscriptions → 201 + Subscription angelegt
✅ GET /epcis/subscriptions → alle Subscriptions
✅ PUT /epcis/subscriptions/{id}/active → deaktiviert
✅ DELETE /epcis/subscriptions/{id} → gelöscht
```

---

## 11. NICHT IN PHASE 3 — EXPLIZIT AUSGESCHLOSSEN

| Requirement | Beschreibung | Phase |
|---|---|---|
| REQ201 | SGTIN State Matrix | Phase 5 |
| REQ202 | Store Receiving Threshold (vollständig) | Phase 5 |
| REQ208 | Auto-Disaggregation bei Store-Receiving | Phase 5 |
| REQ312 | Tunnel Scan Information → DWH | Optional Phase 3b |
| REQ313 | Potentially Stolen Merchandise → DWH | Optional Phase 3b |
| Kafka | Event Streaming | Optional Phase 4 |
| GDSN | Master Data Integration | Phase 4 |
| Digital Link | GS1 Digital Link Resolver | Phase 4 |

---

## 12. REIHENFOLGE INNERHALB PHASE 3

**Empfehlung — in dieser Reihenfolge:**

1. **Outbox + OutboxProcessor** — Fundament, alles andere baut darauf auf
2. **SubscriptionDispatcher + SubscriptionService** — schnellster Business-Value für Halo
3. **REQ301 Inventory → GSPM** — bereits in Phase 2 vorbereitet, fast fertig
4. **REQ308 Goods Receipt → ERP** — kleinstes Interface, schnell umsetzbar
5. **REQ302 Shipping + REQ303 Arrival → GSPM** — komplexer wegen Aggregationslogik
6. **REQ305 Sales + REQ306 Returns → DWH** — periodisch, unkritisch
7. **REQ309 Trolley → EWM** — wegen 10s SLA separat behandeln

---

## 13. DEFINITION OF DONE PHASE 3

```
☐ Outbox-Tabelle wird korrekt befüllt bei jedem relevanten Event
☐ OutboxProcessor sendet erfolgreich an Mock-Endpunkte
☐ Retry-Logik funktioniert bei HTTP-Fehlern
☐ SubscriptionDispatcher erstellt Outbox-Einträge für passende Events
☐ Subscription CRUD API funktioniert
☐ Halo-Subscription empfängt alle konfigurierten Event-Typen
☐ REQ301: Inventory-Message mit korrektem Format an GSPM-Mock
☐ REQ302: Shipping-Message nach Departing Event
☐ REQ303: Arrival-Message nach Receiving Event in Store
☐ REQ308: GoodsReceipt nach Store Receiving
☐ REQ305/306: Sales/Returns-Messages nach Selling/Returning Events
☐ Alle downstream.enabled=false startet App ohne Fehler (kein Connection Error)
☐ Alle Pflicht-Tests grün
☐ Phase-1/2-Tests weiterhin grün (keine Regression)
```
