# EPCIS Repository — User Stories (Rückwirkende Dokumentation)

**Projekt:** EPCIS 2.0 Repository — C&A
**Stand:** 2026-04-16
**Version:** 1.0
**Status:** Rückwirkend dokumentiert (alle Stories: Done)

---

## Inhaltsverzeichnis

1. [Epic 1 — EPCIS Query API](#epic-1--epcis-query-api)
2. [Epic 2 — Legacy Query API](#epic-2--legacy-query-api)
3. [Epic 3 — Inventory Service](#epic-3--inventory-service)
4. [Epic 4 — CBV Vocabulary & Validierung](#epic-4--cbv-vocabulary--validierung)
5. [Epic 5 — GS1 Digital Link](#epic-5--gs1-digital-link)
6. [Epic 6 — Subscriptions & Webhooks](#epic-6--subscriptions--webhooks)
7. [Epic 7 — Outbox & Reliable Delivery](#epic-7--outbox--reliable-delivery)
8. [Epic 8 — Health, Metrics & Betrieb](#epic-8--health-metrics--betrieb)

---

## Epic 1 — EPCIS Query API

**Epic-Ziel:** Logistik-Analysten und Supply-Chain-Manager können EPCIS 2.0-konforme Events gezielt abfragen, filtern und für Analysen verwenden — ohne direkten Datenbankzugriff und ohne Kenntnisse des internen Datenmodells.

---

### US-1.1 — Alle Events abrufen

**Als** Logistik-Analyst **möchte ich** alle im System gespeicherten EPCIS Events über eine standardisierte Query-Schnittstelle abrufen, **damit** ich einen vollständigen Überblick über alle erfassten Ereignisse erhalte und daraus Auswertungen erstellen kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/epcis/query/events` gibt eine valide EPCIS 2.0 JSON-Antwort zurück
- Die Antwort enthält alle gespeicherten Events in korrekter EPCIS 2.0-Struktur
- Das Response-Format entspricht dem offiziellen GS1 EPCIS 2.0 JSON Schema
- Bei leerem Repository wird eine leere, aber strukturell korrekte Event-Liste zurückgegeben
- Der HTTP-Statuscode ist 200 OK bei Erfolg

---

### US-1.2 — Events nach Event-Typ filtern

**Als** Logistik-Analyst **möchte ich** Events nach `eventType` (z.B. `ObjectEvent`, `AggregationEvent`) filtern, **damit** ich gezielt nur die für meinen Anwendungsfall relevanten Event-Typen analysieren kann, ohne unnötige Daten zu verarbeiten.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `eventType` wird am Endpoint `/epcis/query/events` akzeptiert
- Valide Werte sind mindestens `ObjectEvent` und `AggregationEvent`
- Die Antwort enthält ausschließlich Events des angegebenen Typs
- Bei einem ungültigen `eventType`-Wert wird HTTP 400 mit einem beschreibenden Fehler zurückgegeben
- Der Filter ist kombinierbar mit anderen Query-Parametern

---

### US-1.3 — Events nach Action filtern

**Als** Logistik-Analyst **möchte ich** Events nach `action` (`ADD`, `OBSERVE`, `DELETE`) filtern, **damit** ich z.B. nur Wareneingänge (`ADD`) oder Bestandsbeobachtungen (`OBSERVE`) separat auswerten kann.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `action` wird akzeptiert und korrekt ausgewertet
- Valide Werte sind `ADD`, `OBSERVE` und `DELETE`
- Events mit anderem Action-Wert werden nicht in der Antwort zurückgegeben
- Bei ungültigem Wert wird HTTP 400 zurückgegeben
- Der Filter ist mit `eventType` und weiteren Parametern kombinierbar

---

### US-1.4 — Events nach Business Step filtern

**Als** Supply-Chain-Manager **möchte ich** Events nach `bizStep` (z.B. `shipping`, `receiving`, `packing`, `retail_selling`) filtern, **damit** ich Warenbewegungen auf Prozessebene verfolgen und Prozessschritte gezielt auswerten kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `bizStep` wird am EPCIS Query Endpoint akzeptiert
- Unterstützte Werte sind mindestens: `shipping`, `receiving`, `packing`, `retail_selling`
- CBV-konforme URN- und HTTPS-Formen werden gleichermaßen unterstützt
- Die zurückgegebenen Events weisen ausschließlich den gefilterten `bizStep` auf
- Kombinationsfilter mit `eventType` und `action` funktionieren korrekt

---

### US-1.5 — Events nach Disposition filtern

**Als** Supply-Chain-Manager **möchte ich** Events nach `disposition` (z.B. `in_transit`, `sellable_accessible`) filtern, **damit** ich den Zustand von Waren zum Zeitpunkt eines Ereignisses gezielt auswerten und Bestände nach Status klassifizieren kann.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `disposition` wird korrekt ausgewertet
- Unterstützte Werte umfassen mindestens `in_transit` und `sellable_accessible`
- Nur Events mit der passenden Disposition werden zurückgegeben
- Kombination mit `bizStep` liefert korrekt gefilterte Ergebnisse
- Falsche oder nicht-CBV-konforme Disposition-Werte führen zu HTTP 400

---

### US-1.6 — Events nach EPC filtern (SGTIN / SSCC)

**Als** Logistik-Analyst **möchte ich** Events nach einem bestimmten EPC (SGTIN oder SSCC) filtern, **damit** ich die vollständige Ereignishistorie eines einzelnen Artikels oder einer Palette nachverfolgen kann.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `epcMatch` wird für exakte EPC-Suche unterstützt
- Sowohl SGTIN- als auch SSCC-Identifier werden korrekt aufgelöst
- Events werden zurückgegeben, wenn der EPC in `epcList`, `childEPCs` oder `parentID` vorkommt
- Ungültige EPC-Formate führen zu HTTP 400 mit erklärender Fehlermeldung
- Der Filter ist mit `eventType`, `bizStep` und Zeitraum-Filtern kombinierbar

---

### US-1.7 — Events nach parentId filtern (SSCC)

**Als** Logistik-Analyst **möchte ich** AggregationEvents nach `parentId` (SSCC) filtern, **damit** ich alle Ereignisse zu einer bestimmten Palette oder einem bestimmten Ladungsträger abrufen kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `parentId` wird am EPCIS Query Endpoint akzeptiert
- Die Suche trifft ausschließlich Events, in denen der SSCC als `parentID` vorkommt
- Das Ergebnis ist auf AggregationEvents beschränkt, da nur diese eine `parentID` aufweisen
- Ungültige SSCC-Formate werden mit HTTP 400 abgelehnt
- Der Filter liefert korrekte Ergebnisse in Kombination mit Zeitraum-Parametern

---

### US-1.8 — Events nach Read Point filtern (SGLN)

**Als** Supply-Chain-Manager **möchte ich** Events nach `readPoint` (SGLN) filtern, **damit** ich alle Ereignisse analysieren kann, die an einem bestimmten physischen Lesepunkt (z.B. Warehouse-Gate, Förderband) erfasst wurden.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `readPoint` wird akzeptiert
- Die Filterung erfolgt gegen das `readPoint`-Feld des EPCIS Events
- SGLN-Identifier werden in korrekter URI-Form akzeptiert
- Das Ergebnis enthält nur Events mit exakt passendem `readPoint`
- Kombination mit `bizStep` und Zeitraum-Filtern ist möglich

---

### US-1.9 — Events nach Business Location filtern (SGLN / GLN)

**Als** Supply-Chain-Manager **möchte ich** Events nach `bizLocation` oder GLN filtern, **damit** ich Warenbewegungen und -bestände für einen bestimmten Standort (z.B. Filiale, Lager) auswerten kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Die Query-Parameter `bizLocation` und `GLN` werden am Endpoint akzeptiert
- Die Suche liefert Events, die am angegebenen Standort stattfanden
- Sowohl SGLN- als auch GLN-Formate werden korrekt interpretiert
- Bei fehlerhaftem Identifier-Format wird HTTP 400 zurückgegeben
- Der Filter ist vollständig kombinierbar mit `eventType`, `bizStep`, und `disposition`

---

### US-1.10 — Events nach Zeitraum filtern

**Als** Logistik-Analyst **möchte ich** Events nach einem Zeitraum (`eventTimeGT`, `eventTimeLT`) filtern, **damit** ich Ereignisse für eine bestimmte Periode (z.B. einen Liefertag oder eine Schicht) gezielt abrufen und auswerten kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Die Query-Parameter `eventTimeGT` und `eventTimeLT` werden akzeptiert
- Zeitstempel werden im ISO 8601-Format erwartet und korrekt interpretiert
- Nur Events, deren `eventTime` innerhalb des angegebenen Intervalls liegt, werden zurückgegeben
- Die Grenzen sind beidseitig anwendbar (nur GT, nur LT, oder kombiniert)
- Ungültige Zeitstempel-Formate führen zu HTTP 400

---

### US-1.11 — Anzahl der zurückgegebenen Events begrenzen

**Als** IT-Integration-Engineer **möchte ich** die Anzahl zurückgegebener Events mit `maxEventCount` begrenzen, **damit** ich Abfragen gegen große Datenmengen sicher und performant durchführen kann, ohne den Client zu überlasten.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der Query-Parameter `maxEventCount` wird am Endpoint akzeptiert
- Die Antwort enthält nicht mehr Events als der angegebene Wert
- Ohne `maxEventCount` gilt ein serverseitiger Standardwert oder keine Begrenzung (dokumentiert)
- Nicht-numerische oder negative Werte führen zu HTTP 400
- Das Verhalten bei Überschreitung des Limits ist durch einen Response-Header oder Metadaten-Feld dokumentiert

---

### US-1.12 — Kombinierte Filter nutzen

**Als** Logistik-Analyst **möchte ich** mehrere Filter gleichzeitig kombinieren (z.B. `eventType=ObjectEvent` + `bizStep=shipping` + `action=OBSERVE`), **damit** ich präzise, mehrdimensionale Abfragen stellen kann, ohne die Ergebnisse clientseitig nachfiltern zu müssen.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- Beliebige Kombinationen der unterstützten Query-Parameter werden korrekt ausgewertet (AND-Logik)
- Die Ergebnismenge enthält nur Events, die alle gesetzten Filter erfüllen
- Leere Ergebnismengen bei gültigen Filterkombinationen geben HTTP 200 mit leerer Liste zurück
- Die Reihenfolge, in der Parameter übergeben werden, beeinflusst das Ergebnis nicht
- Performance-Tests mit kombinierten Filtern auf realistischen Datenmengen bestehen

---

### US-1.13 — Einzelnes Event per UUID abrufen

**Als** Logistik-Analyst **möchte ich** ein einzelnes EPCIS Event direkt per UUID (`/epcis/query/events/{eventId}`) abrufen, **damit** ich Details zu einem bekannten Ereignis ohne Umwege über Listabfragen einsehen kann.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/epcis/query/events/{eventId}` gibt das Event mit der angegebenen UUID zurück
- Das Antwortformat ist EPCIS 2.0-konform
- Bei nicht existierender UUID wird HTTP 404 mit beschreibender Fehlermeldung zurückgegeben
- Bei ungültigem UUID-Format wird HTTP 400 zurückgegeben
- Das zurückgegebene Event ist identisch mit dem Event, das über die Listenabfrage zurückgegeben werden würde

---

## Epic 2 — Legacy Query API

**Epic-Ziel:** IT-Integration-Engineers und Logistik-Analysten können Events über eine legacy-kompatible REST-Schnittstelle abfragen — mit internen DB-IDs und technisch-orientierten Filterparametern — während die Migration auf die EPCIS 2.0 Query API vorbereitet wird.

---

### US-2.1 — Alle Events über Legacy API abrufen

**Als** IT-Integration-Engineer **möchte ich** alle Events über die Legacy API (`/api/events`) abrufen, **damit** ich Systeme, die noch nicht auf die EPCIS 2.0 Query API migriert sind, weiterhin bedienen kann.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/api/events` gibt alle gespeicherten Events zurück
- Das Antwortformat ist konsistent und maschinenlesbar (JSON)
- Die Antwort enthält alle relevanten Felder für Legacy-Konsumenten
- Bei leerem Repository wird eine leere Liste mit HTTP 200 zurückgegeben
- Die API ist funktional stabil, auch wenn intern EPCIS 2.0-Strukturen genutzt werden

---

### US-2.2 — Legacy Events nach EPC filtern

**Als** IT-Integration-Engineer **möchte ich** Events über die Legacy API nach EPC (SGTIN oder SSCC) filtern, **damit** ich Legacy-Integrationspfade, die EPC-basierte Lookups erwarten, weiterhin bedienen kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Der Filter-Parameter für EPC wird am `/api/events` Endpoint akzeptiert
- Die Filterung funktioniert für SGTIN- und SSCC-Identifier
- Das Ergebnis enthält nur Events mit passendem EPC
- Ungültige EPC-Formate führen zu HTTP 400
- Das Verhalten ist äquivalent zum entsprechenden Filter der EPCIS 2.0 Query API

---

### US-2.3 — Legacy Events nach GLN filtern

**Als** IT-Integration-Engineer **möchte ich** Events über die Legacy API nach GLN filtern, **damit** standortbezogene Legacy-Abfragen weiterhin bedient werden können, bis die Migration auf die EPCIS 2.0 API abgeschlossen ist.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der GLN-Filter wird am Legacy-Endpoint akzeptiert
- Die Filterung trifft Events mit passendem `bizLocation` oder `readPoint`
- Gültige GLN-Formate werden korrekt verarbeitet
- Kombination mit anderen Legacy-Filtern ist möglich
- Das Ergebnis ist konsistent mit dem, was die EPCIS 2.0 API für denselben GLN liefern würde

---

### US-2.4 — Legacy Events nach Event-Typ, Business Step und Action filtern

**Als** IT-Integration-Engineer **möchte ich** Events über die Legacy API nach `eventType`, `bizStep` und `action` filtern, **damit** technische Integrationspipelines, die auf diese Parameter angewiesen sind, weiterhin funktionieren.

**Priority:** Should Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- Die Parameter `eventType`, `bizStep` und `action` werden am Legacy-Endpoint unterstützt
- Die Filterlogik ist identisch mit der der EPCIS 2.0 Query API
- Alle drei Parameter sind einzeln und kombiniert einsetzbar
- Ungültige Werte führen zu HTTP 400
- Das Response-Format ist für Legacy-Konsumenten stabil und rückwärtskompatibel

---

### US-2.5 — Legacy Events nach Zeitraum filtern

**Als** IT-Integration-Engineer **möchte ich** Events über die Legacy API nach einem Timestamp-Bereich filtern, **damit** zeitbezogene Legacy-Abfragen ohne Anpassung der Konsumenten weiter funktionieren.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Timestamp-Range-Parameter werden am Legacy-Endpoint akzeptiert
- ISO 8601-konforme Zeitstempel werden korrekt verarbeitet
- Nur Events innerhalb des angegebenen Zeitraums werden zurückgegeben
- Kombination mit anderen Legacy-Filtern funktioniert korrekt
- Das Verhalten ist äquivalent zur Zeitraum-Filterung der EPCIS 2.0 API

---

### US-2.6 — Einzelnes Event per DB-ID über Legacy API abrufen

**Als** IT-Integration-Engineer **möchte ich** ein einzelnes Event über seine interne Datenbank-ID abrufen, **damit** Legacy-Systeme, die DB-IDs als Referenz verwenden, weiterhin direkte Lookups durchführen können.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/api/events/{id}` gibt das Event mit der angegebenen DB-ID zurück
- Bei nicht existierender ID wird HTTP 404 zurückgegeben
- Das Antwortformat ist für Legacy-Konsumenten stabil
- Die DB-ID ist unveränderlich und persistiert auch nach Neustart des Systems
- Kein internes technisches Detail (z.B. JPA-Entity-Struktur) wird versehentlich exponiert

---

## Epic 3 — Inventory Service

**Epic-Ziel:** Supply-Chain-Manager und Logistik-Analysten erhalten einen berechneten, ereignisgetriebenen Lagerbestandsüberblick — auf Artikel-, Standort- und Paletten-Ebene — ohne direkte Datenbankabfragen oder manuelle Event-Auswertungen.

---

### US-3.1 — Aktuellen Status eines EPC abfragen

**Als** Logistik-Analyst **möchte ich** den aktuellen Zustand (Status) eines SGTIN oder SSCC abfragen, **damit** ich sofort erkennen kann, wo sich ein Artikel oder eine Palette im Supply-Chain-Prozess befindet und welchen Disposition-Status sie hat.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/epc` gibt den zuletzt bekannten Status des EPC zurück
- Der Status enthält mindestens: aktueller `bizStep`, `disposition`, `bizLocation`, und Zeitstempel des letzten Events
- SGTIN- und SSCC-Identifier werden korrekt unterstützt
- Bei unbekanntem EPC wird HTTP 404 zurückgegeben
- Der Status wird aus den gespeicherten EPCIS Events berechnet, nicht manuell gepflegt

---

### US-3.2 — Lagerbestand an einem Standort abfragen

**Als** Supply-Chain-Manager **möchte ich** alle am System bekannten EPCs an einem bestimmten Standort abfragen — optional gefiltert auf verfügbare Ware — **damit** ich den Lagerbestand einer Filiale oder eines Lagers ohne manuelle Zählung einsehen kann.

**Priority:** Must Have | **Story Points:** 8 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/stock` gibt alle EPCs zurück, die zuletzt an diesem Standort erfasst wurden
- Ein optionaler Filter `availableOnly` wird unterstützt
- Das Ergebnis enthält Artikel- und Mengenangaben
- Standorte werden über GLN oder SGLN identifiziert
- Bei unbekanntem Standort wird eine leere Liste mit HTTP 200 zurückgegeben

---

### US-3.3 — Menge nach GTIN abfragen (gesamt und pro Standort)

**Als** Supply-Chain-Manager **möchte ich** die Gesamtmenge eines Artikels (identifiziert per GTIN) im System abfragen — sowohl global als auch aufgeschlüsselt nach Standort — **damit** ich Bestandssummen auf Artikelebene ohne Einzelabfragen je Standort erhalte.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/quantity` gibt die Gesamtmenge des Artikels zurück
- Ein optionaler Parameter ermöglicht die Einschränkung auf einen Standort (GLN)
- Mengen werden korrekt aus `quantityList`-Feldern der EPCIS Events berechnet
- Die Berechnung schließt nur EPCs ein, die nicht als `DELETE` ausgebucht wurden
- Ergebnisse sind reproduzierbar und konsistent mit den gespeicherten Events

---

### US-3.4 — Paletteninhalt per SSCC abfragen

**Als** Logistik-Analyst **möchte ich** den aktuellen Inhalt einer Palette (SSCC) abfragen, **damit** ich sehen kann, welche Artikel (SGTINs) sich laut EPCIS-Aggregation auf einer Palette befinden.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/pallet` gibt alle aktuell aggregierten `childEPCs` des SSCC zurück
- Der Paletteninhalt wird aus den zuletzt verarbeiteten `AggregationEvent`-Daten berechnet
- Ein `DELETE`-AggregationEvent entleert den Paletteninhalt korrekt
- Bei unbekanntem SSCC wird HTTP 404 zurückgegeben
- Das Ergebnis enthält Metadaten zum letzten bekannten Status (Standort, Zeitstempel)

---

### US-3.5 — Bewegungshistorie eines EPC abfragen

**Als** Supply-Chain-Manager **möchte ich** die vollständige Bewegungshistorie eines EPC (SGTIN oder SSCC) abfragen — optional mit Zeitraum-Filter — **damit** ich die Lieferkette eines Artikels lückenlos nachvollziehen und im Streitfall belegen kann.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/history` gibt alle Events zurück, an denen der EPC beteiligt war
- Events sind chronologisch sortiert (ältestes zuerst)
- Ein optionaler Zeitraum-Filter (from/to) und ein `limit`-Parameter werden unterstützt
- Jeder Eintrag enthält mindestens: `eventTime`, `bizStep`, `disposition`, `bizLocation`
- Die Bewegungshistorie ist vollständig — kein Event wird ohne explizite fachliche Begründung ausgelassen

---

### US-3.6 — Verfügbare Menge im GSPM-Format abfragen

**Als** IT-Integration-Engineer **möchte ich** die verfügbare Menge im GSPM-Format abrufen — gesamt und pro Store — **damit** nachgelagerte Systeme (z.B. Replenishment-Tools) die Bestandsdaten in einem definierten, standardisierten Format konsumieren können.

**Priority:** Should Have | **Story Points:** 8 | **Status:** Done

**Acceptance Criteria:**
- GET `/inventory/available-quantity` gibt verfügbare Mengen im GSPM-Format zurück
- Eine standortspezifische Variante (GLN-Filter) wird unterstützt
- Nur Artikel mit `disposition=sellable_accessible` oder äquivalentem Status fließen in die Berechnung ein
- Das Format entspricht der definierten GSPM-Struktur (dokumentiert)
- Das Ergebnis ist maschinell verarbeitbar ohne manuelle Nachbearbeitung

---

### US-3.7 — Inventory neu berechnen (Rebuild)

**Als** Systemadministrator **möchte ich** den Inventory-Stand manuell neu berechnen lassen, **damit** ich nach Datenkorrekturen, Reimports oder technischen Fehlern sicherstellen kann, dass der Inventory-Service den korrekten, aktuellen Stand aus den EPCIS Events abbildet.

**Priority:** Should Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- POST `/inventory/rebuild` löst eine vollständige Neuberechnung des Inventars aus
- Die Neuberechnung verarbeitet alle gespeicherten EPCIS Events in korrekter Reihenfolge
- Nach Abschluss des Rebuilds ist der Inventory-Stand konsistent mit den gespeicherten Events
- Der Rebuild-Vorgang ist idempotent — mehrfache Ausführung führt zum gleichen Ergebnis
- Laufende Rebuild-Vorgänge sind im System erkennbar (z.B. Status-Response oder Log-Eintrag)

---

## Epic 4 — CBV Vocabulary & Validierung

**Epic-Ziel:** Daten-Qualitäts-Prüfer und IT-Integration-Engineers können GS1 CBV-Vokabular einsehen, Identifier und URIs validieren und Quarantäne-Events verwalten — um die semantische Korrektheit aller im System verarbeiteten EPCIS Events sicherzustellen.

---

### US-4.1 — BizStep-Vokabular abrufen

**Als** Daten-Qualitäts-Prüfer **möchte ich** alle gültigen `bizStep`-Werte aus dem CBV-Vokabular abrufen, **damit** ich weiß, welche Business-Step-Werte das System akzeptiert und Partner-Systeme korrekt konfiguriert werden können.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/vocabulary/BIZ_STEP` gibt alle systembekannten, CBV-konformen `bizStep`-Werte zurück
- Sowohl URN-Form als auch HTTPS-Form werden im Ergebnis ausgewiesen
- Die Liste entspricht dem offiziellen GS1 CBV-Standard (Version dokumentiert)
- Das Ergebnis ist maschinenlesbar (JSON)
- Kein proprietärer oder nicht-CBV-konformer Wert ist in der Liste enthalten

---

### US-4.2 — Disposition-Vokabular abrufen

**Als** Daten-Qualitäts-Prüfer **möchte ich** alle gültigen `disposition`-Werte abrufen, **damit** Partner-Systeme und interne Teams wissen, welche Disposition-Werte akzeptiert werden und semantisch korrekt sind.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/vocabulary/DISPOSITION` gibt alle CBV-konformen Disposition-Werte zurück
- URN- und HTTPS-Formen sind beide enthalten
- Der Inhalt entspricht dem offiziellen GS1 CBV-Standard
- Das Format ist JSON und maschinenlesbar
- Veraltete oder nicht-standardisierte Werte sind nicht enthalten

---

### US-4.3 — BizTransactionType-Vokabular abrufen

**Als** Daten-Qualitäts-Prüfer **möchte ich** die gültigen `bizTransactionType`-Werte einsehen, **damit** ich überprüfen kann, ob Geschäftstransaktions-Referenzen in EPCIS Events korrekt typisiert sind.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/vocabulary/BIZ_TRANSACTION_TYPE` gibt alle CBV-konformen Werte zurück
- Sowohl URN- als auch HTTPS-Formen werden ausgewiesen
- Der Inhalt entspricht dem offiziellen GS1 CBV-Standard
- Das Ergebnis ist JSON-formatiert
- Keine nicht-standardisierten Werte sind enthalten

---

### US-4.4 — SourceDestType-Vokabular abrufen

**Als** Daten-Qualitäts-Prüfer **möchte ich** die gültigen `sourceDest`-Typen einsehen, **damit** ich sicherstellen kann, dass Source/Destination-Einträge in EPCIS Events korrekte Typen verwenden.

**Priority:** Should Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/vocabulary/SOURCE_DEST_TYPE` gibt alle CBV-konformen Source/Dest-Typen zurück
- URN- und HTTPS-Formen sind im Ergebnis enthalten
- Der Inhalt ist GS1 CBV-konform
- Das Format ist maschinenlesbar (JSON)
- Keine proprietären Erweiterungen sind ohne Kennzeichnung enthalten

---

### US-4.5 — URI auf CBV-Konformität validieren (gültige URI)

**Als** IT-Integration-Engineer **möchte ich** eine URI gegen das CBV-Vokabular validieren, **damit** ich vor dem Senden von Events prüfen kann, ob verwendete Vocabulary-Werte vom System akzeptiert werden.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/validate` akzeptiert eine URI und einen Typ und gibt das Validierungsergebnis zurück
- Gültige CBV-URIs erhalten eine positive Rückmeldung mit dem erkannten Vocabulary-Typ
- Sowohl URN-Form als auch HTTPS-Form werden akzeptiert
- Die Antwort benennt, zu welchem CBV-Bereich die URI gehört
- HTTP 200 mit positivem Validierungsergebnis bei gültiger URI

---

### US-4.6 — Ungültige URI auf CBV-Konformität prüfen

**Als** Daten-Qualitäts-Prüfer **möchte ich** auch ungültige URIs durch den Validator schicken, **damit** ich verstehe, warum ein Event vom System abgelehnt wurde und die Fehlerursache klar kommuniziert bekomme.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der Validator gibt bei ungültiger URI eine negative Rückmeldung zurück
- Die Fehlermeldung benennt konkret, warum die URI ungültig ist
- HTTP-Statuscode ist 200 mit einem Ergebnisfeld `valid: false`
- Sowohl strukturell falsche als auch inhaltlich falsche URIs werden abgelehnt
- Die Fehlermeldung ist verständlich ohne GS1-Expertenwissen

---

### US-4.7 — Quarantäne-Events anzeigen (offene Events)

**Als** Daten-Qualitäts-Prüfer **möchte ich** alle offenen Quarantäne-Events einsehen, **damit** ich Events, die wegen Validierungsfehlern nicht automatisch verarbeitet wurden, manuell prüfen und entscheiden kann, ob sie korrigiert oder verworfen werden sollen.

**Priority:** Must Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/quarantine?resolved=false` gibt alle offenen Quarantäne-Events zurück
- Jeder Eintrag enthält: originaler Event-Payload, Quarantäne-Grund, Zeitstempel der Einreihung
- Die Liste ist nach Einreihungszeitpunkt sortiert (älteste zuerst)
- Bei leerer Quarantäne wird eine leere Liste mit HTTP 200 zurückgegeben
- Nur Einträge mit offenem Status sind sichtbar

---

### US-4.8 — Alle Quarantäne-Events anzeigen (inkl. abgeschlossene)

**Als** Daten-Qualitäts-Prüfer **möchte ich** auch bereits bearbeitete Quarantäne-Events einsehen, **damit** ich die Historie der Validierungsfehler nachvollziehen und Muster in wiederkehrenden Datenproblemen erkennen kann.

**Priority:** Should Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/cbv/quarantine` gibt alle Quarantäne-Events zurück, inklusive abgeschlossener
- Jeder Eintrag enthält einen Status-Indikator (offen / abgeschlossen / verworfen)
- Das Ergebnis ist vollständig und lückenlos
- Filter nach Status (`resolved=true/false`) werden unterstützt
- Das Format ist konsistent mit der offene-Events-Ansicht

---

## Epic 5 — GS1 Digital Link

**Epic-Ziel:** Logistik-Analysten und IT-Integration-Engineers können GS1 Digital Link URIs parsen, in EPCIS-Identifier umwandeln, auflösen und für Event-History-Abfragen und Standortbestimmung verwenden — in Übereinstimmung mit dem GS1 Digital Link Standard.

---

### US-5.1 — GTIN+Serial per Digital Link parsen

**Als** IT-Integration-Engineer **möchte ich** eine GS1 Digital Link URI mit GTIN und Seriennummer parsen lassen, **damit** ich den enthaltenen SGTIN-Identifier ohne manuelle String-Verarbeitung extrahieren und weiterverarbeiten kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/parse` akzeptiert eine GS1 Digital Link URI mit GTIN+Serial
- Das Ergebnis enthält den extrahierten SGTIN-Identifier in normierter Form
- Strukturell ungültige Digital Link URIs werden mit HTTP 400 abgelehnt
- Das Parsing ist konform zum GS1 Digital Link Standard
- GTIN-Prüfziffer wird validiert

---

### US-5.2 — SSCC per Digital Link parsen

**Als** IT-Integration-Engineer **möchte ich** eine GS1 Digital Link URI mit SSCC parsen lassen, **damit** ich Paletten-Identifier aus Digital Link URIs extrahieren und in EPCIS-Abfragen verwenden kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/parse` akzeptiert eine GS1 Digital Link URI mit SSCC (AI 00)
- Das Ergebnis enthält den SSCC in normierter EPCIS-Form
- Die SSCC-Prüfziffer wird validiert
- Ungültige SSCC-Strukturen führen zu HTTP 400
- Das Ergebnis ist direkt als EPCIS-Identifier verwendbar

---

### US-5.3 — SGTIN in Digital Link konvertieren

**Als** IT-Integration-Engineer **möchte ich** einen SGTIN-Identifier in eine GS1 Digital Link URI konvertieren, **damit** ich EPCIS-Identifier in Digital Link-fähige Formate für Downstream-Systeme überführen kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/convert` akzeptiert einen SGTIN und einen `baseUrl`-Parameter
- Das Ergebnis enthält eine valide GS1 Digital Link URI
- Die generierte URI ist konform zum GS1 Digital Link Standard
- Ungültige SGTINs werden mit HTTP 400 abgelehnt
- Roundtrip-Konsistenz ist gewährleistet (SGTIN → DL → SGTIN ergibt identischen SGTIN)

---

### US-5.4 — SSCC in Digital Link konvertieren

**Als** IT-Integration-Engineer **möchte ich** einen SSCC in eine GS1 Digital Link URI konvertieren, **damit** Paletten-Identifier standardkonform in Digital Link-Formate überführt werden können.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/convert` akzeptiert einen SSCC-Identifier
- Das Ergebnis ist eine valide GS1 Digital Link URI mit AI 00
- Die generierte URI ist GS1 Digital Link-konform
- Ungültige SSCCs werden abgelehnt
- Roundtrip-Konsistenz ist gewährleistet

---

### US-5.5 — Digital Link URI auflösen (Resolve via URI)

**Als** Logistik-Analyst **möchte ich** eine Digital Link URI auflösen lassen, **damit** ich aus einer URI direkt auf die hinterlegten EPCIS-Events zugreifen kann.

**Priority:** Should Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/resolve` akzeptiert eine Digital Link URI als Parameter
- Die Auflösung gibt den dem Identifier zugehörigen EPCIS-Datensatz zurück
- Unbekannte Identifier führen zu HTTP 404
- Ungültige URIs führen zu HTTP 400
- Das Ergebnis enthält mindestens die Event-History des zugehörigen EPC

---

### US-5.6 — Digital Link direkt per AI auflösen

**Als** IT-Integration-Engineer **möchte ich** eine Digital Link-Auflösung direkt über den Application Identifier (AI) durchführen, **damit** ich auch ohne vollständige URI-Struktur gezielt Identifier-basierte Lookups ausführen kann.

**Priority:** Could Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/resolve/{ai}/{value}` unterstützt eine direkte AI-basierte Auflösung
- Das Ergebnis ist äquivalent zur URI-basierten Auflösung
- Ungültige AI-Werte oder Identifier führen zu HTTP 400
- Das Verhalten ist konsistent mit dem GS1 Digital Link Resolver-Standard
- Unterstützte AIs sind mindestens: `01` (GTIN), `00` (SSCC)

---

### US-5.7 — Event History per Digital Link URI abrufen

**Als** Logistik-Analyst **möchte ich** die Event History eines Artikels oder einer Palette direkt über die Digital Link URI abrufen, **damit** ich Produktdaten und Bewegungshistorie über einen einzigen, standardisierten Zugangspunkt erhalte.

**Priority:** Should Have | **Story Points:** 5 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/history` akzeptiert eine Digital Link URI
- Das Ergebnis ist die vollständige, chronologisch sortierte Event-History des zugehörigen EPC
- Unbekannte Identifier liefern HTTP 404
- Das Format ist EPCIS 2.0-konform oder klar dokumentiert
- Die History ist identisch mit dem Ergebnis der direkten EPC-Bewegungshistorie-Abfrage

---

### US-5.8 — Aktuellen Standort per Digital Link URI abfragen

**Als** Supply-Chain-Manager **möchte ich** den aktuellen Standort eines Artikels oder einer Palette über die Digital Link URI abfragen, **damit** ich aus einem einzigen, standardisierten Einstiegspunkt sofort den letzten bekannten Aufenthaltsort erhalte.

**Priority:** Should Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/digitallink/location` akzeptiert eine Digital Link URI
- Das Ergebnis enthält den zuletzt bekannten `bizLocation` und Zeitstempel
- Unbekannte Identifier liefern HTTP 404
- Das Ergebnis ist konsistent mit dem Inventory-Service-Endpunkt für denselben EPC
- Der Standort wird aus dem neuesten EPCIS Event des Identifiers berechnet

---

## Epic 6 — Subscriptions & Webhooks

**Epic-Ziel:** IT-Integration-Engineers können Downstream-Systeme für Event-basierte Push-Notifications konfigurieren, damit relevante EPCIS-Events zuverlässig, gefiltert und gesichert an externe Konsumenten geliefert werden.

---

### US-6.1 — Alle Subscriptions anzeigen

**Als** IT-Integration-Engineer **möchte ich** alle konfigurierten Subscriptions anzeigen lassen, **damit** ich einen Überblick über alle aktiven und inaktiven Webhook-Verbindungen habe und deren Status überwachen kann.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/epcis/subscriptions` gibt alle konfigurierten Subscriptions zurück
- Jede Subscription enthält: ID, callbackUrl, Status (aktiv/inaktiv), konfigurierte eventTypes und bizSteps
- Bei keiner konfigurierten Subscription wird eine leere Liste mit HTTP 200 zurückgegeben
- Die Liste enthält keine sensiblen Authentifizierungsdaten im Klartext
- Das Ergebnis ist maschinenlesbar (JSON)

---

### US-6.2 — Einzelne Subscription per ID abrufen

**Als** IT-Integration-Engineer **möchte ich** eine einzelne Subscription per ID abrufen, **damit** ich Details einer spezifischen Webhook-Konfiguration prüfen kann, ohne die Gesamtliste zu laden.

**Priority:** Should Have | **Story Points:** 1 | **Status:** Done

**Acceptance Criteria:**
- GET `/epcis/subscriptions/{id}` gibt die Subscription mit der angegebenen ID zurück
- Bei unbekannter ID wird HTTP 404 zurückgegeben
- Das Ergebnis enthält alle Konfigurationsdetails der Subscription
- Sensible Daten (z.B. Auth-Token) werden maskiert
- Das Antwortformat ist konsistent mit der Listenansicht

---

### US-6.3 — Neue Subscription anlegen

**Als** IT-Integration-Engineer **möchte ich** eine neue Subscription mit `callbackUrl`, `authType`, `eventTypes` und `bizSteps` anlegen, **damit** ein Downstream-System zuverlässig und gefiltert über relevante EPCIS-Events benachrichtigt wird.

**Priority:** Must Have | **Story Points:** 8 | **Status:** Done

**Acceptance Criteria:**
- POST `/epcis/subscriptions` erstellt eine neue Subscription und gibt HTTP 201 zurück
- Pflichtfelder sind mindestens `callbackUrl` und mindestens ein `eventType`
- `authType` wird validiert (mindestens: NONE, BEARER, BASIC)
- `bizSteps` werden gegen das CBV-Vokabular validiert, ungültige Werte führen zu HTTP 400
- Doppelte Subscriptions werden erkannt und abgelehnt oder mit einer Warnung zurückgegeben

---

### US-6.4 — Subscription aktivieren

**Als** IT-Integration-Engineer **möchte ich** eine deaktivierte Subscription aktivieren, **damit** ein Downstream-System nach einer Wartungsphase wieder Events empfängt, ohne die Subscription neu anlegen zu müssen.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- PUT `/epcis/subscriptions/{id}/active?active=true` setzt den Status auf aktiv
- Bereits aktive Subscriptions antworten mit HTTP 200 ohne Fehler (idempotent)
- Nach Aktivierung werden neue Events gemäß der Subscription-Konfiguration geliefert
- Der Status-Wechsel wird geloggt (Zeitstempel, auslösende Aktion)
- Die Antwort enthält den aktualisierten Subscription-Status

---

### US-6.5 — Subscription deaktivieren

**Als** IT-Integration-Engineer **möchte ich** eine aktive Subscription deaktivieren, **damit** die Event-Lieferung an ein Downstream-System temporär gestoppt wird, ohne die Konfiguration zu verlieren.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- PUT `/epcis/subscriptions/{id}/active?active=false` setzt den Status auf inaktiv
- Bereits inaktive Subscriptions antworten mit HTTP 200 ohne Fehler (idempotent)
- Nach Deaktivierung werden keine neuen Events an die callbackUrl gesendet
- Der Status-Wechsel wird geloggt
- Das Verhalten für bereits in der Outbox befindliche Messages ist dokumentiert

---

## Epic 7 — Outbox & Reliable Delivery

**Epic-Ziel:** IT-Integration-Engineers und Systemadministratoren können den Zustand der Outbox überwachen, fehlgeschlagene Nachrichten identifizieren und gezielt neu zustellen — um eine zuverlässige, nachvollziehbare Event-Delivery an Downstream-Systeme sicherzustellen.

---

### US-7.1 — Outbox-Statistiken abrufen

**Als** Systemadministrator **möchte ich** eine Zusammenfassung der Outbox-Statistiken abrufen, **damit** ich auf einen Blick den Gesamtzustand der Downstream-Delivery erkenne.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/ops/outbox/stats` gibt aggregierte Kennzahlen zurück
- Enthaltene Metriken: Anzahl pending, versendet, fehlgeschlagen, gesamt
- Die Zahlen sind aktuell (maximal einige Sekunden alt)
- Das Format ist maschinenlesbar (JSON) und monitoring-tauglich
- Die API ist auch unter Last stabil und performant

---

### US-7.2 — Ausstehende Nachrichten in der Outbox anzeigen

**Als** IT-Integration-Engineer **möchte ich** alle noch nicht zugestellten (pending) Outbox-Nachrichten einsehen, **damit** ich erkenne, ob und wo sich Nachrichten stauen.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/ops/outbox/pending` gibt alle Nachrichten mit Status "pending" zurück
- Jeder Eintrag enthält: Nachrichten-ID, Ziel-Subscription, Erstellungszeitpunkt, Anzahl Zustellversuche
- Die Liste ist sortiert (älteste zuerst)
- Bei leerer Pending-Queue wird eine leere Liste mit HTTP 200 zurückgegeben
- Der Endpunkt ist auch bei großem Nachrichtenvolumen performant

---

### US-7.3 — Fehlgeschlagene Nachrichten anzeigen

**Als** IT-Integration-Engineer **möchte ich** alle fehlgeschlagenen Outbox-Nachrichten einsehen, **damit** ich Zustellungsprobleme gezielt diagnostizieren kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/ops/outbox/failed` gibt alle Nachrichten zurück, die den maximalen Retry-Versuch überschritten haben
- Jeder Eintrag enthält: Nachrichten-ID, Fehlerursache, letzter Versuchszeitpunkt, Ziel-Subscription
- Die Fehlermeldung ist spezifisch (z.B. HTTP-Statuscode des Fehlschlags)
- Bei keinen fehlgeschlagenen Nachrichten wird eine leere Liste zurückgegeben
- Fehlgeschlagene Nachrichten bleiben gespeichert bis sie manuell behandelt werden

---

### US-7.4 — Einzelne fehlgeschlagene Nachricht erneut zustellen

**Als** IT-Integration-Engineer **möchte ich** eine einzelne fehlgeschlagene Outbox-Nachricht manuell für einen erneuten Zustellversuch vormerken, **damit** ich temporäre Netzwerkprobleme ohne vollständige Re-Prozessierung überbrücken kann.

**Priority:** Must Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- POST `/ops/outbox/{messageId}/retry` löst einen erneuten Zustellversuch aus
- Die Nachricht wechselt von Status "failed" zurück zu "pending"
- Bei unbekannter messageId wird HTTP 404 zurückgegeben
- Der Retry-Zähler wird korrekt erhöht (kein Reset auf 0)
- Der Retry-Versuch wird geloggt (Zeitstempel, auslösende Aktion)

---

## Epic 8 — Health, Metrics & Betrieb

**Epic-Ziel:** Systemadministratoren können den Gesundheitszustand des Systems, der Datenbankverbindung und wichtiger Betriebsmetriken jederzeit abrufen — als Grundlage für Monitoring, Alerting und SLA-Überwachung.

---

### US-8.1 — Gesundheitszustand des Systems prüfen

**Als** Systemadministrator **möchte ich** den Gesamtgesundheitszustand des EPCIS Repository Services abrufen, **damit** ich erkennen kann, ob der Service ordnungsgemäß läuft und Monitoring-Systeme ihn automatisiert überwachen können.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- GET `/actuator/health` gibt HTTP 200 mit Status `UP` zurück, wenn der Service funktionsfähig ist
- Bei Problemen wird HTTP 503 mit Status `DOWN` zurückgegeben
- Der Health-Check ist synchron und innerhalb von 2 Sekunden verfügbar
- Teilkomponenten (z.B. DB) werden als Sub-Indikatoren ausgewiesen
- Das Format folgt dem Spring Boot Actuator Health-Standard

---

### US-8.2 — Datenbankverbindung separat prüfen

**Als** Systemadministrator **möchte ich** den Gesundheitszustand der Datenbankverbindung isoliert abfragen, **damit** ich bei Systemproblemen schnell unterscheiden kann, ob die Ursache im Application Layer oder im Datenbankzugriff liegt.

**Priority:** Must Have | **Story Points:** 2 | **Status:** Done

**Acceptance Criteria:**
- Der DB-Health-Indikator ist im `/actuator/health/db`-Response sichtbar
- Der Status wechselt auf `DOWN`, wenn die DB nicht erreichbar ist
- Der Check testet die Verbindung aktiv (z.B. per SELECT 1)
- Der DB-Status wird ohne manuelle Intervention automatisch aktualisiert
- Kein sensitives Datenbankdetail wird im Response exponiert

---

### US-8.3 — Service-Informationen abrufen

**Als** Systemadministrator **möchte ich** Service-Metadaten (Version, Build-Zeitpunkt, Umgebung) abrufen, **damit** ich bei Deployments und Incidents schnell feststellen kann, welche Version produktiv läuft.

**Priority:** Should Have | **Story Points:** 1 | **Status:** Done

**Acceptance Criteria:**
- GET `/actuator/info` gibt mindestens Version und Build-Zeitpunkt zurück
- Die Informationen stammen aus dem Build-Artefakt und sind nicht manuell konfiguriert
- Das Format ist JSON und maschinenlesbar
- Der Endpunkt ist auch bei laufenden Operations verfügbar
- Keine internen Systemdetails (z.B. Datenbankpasswort) werden exponiert

---

### US-8.4 — Betriebsmetriken abrufen

**Als** Systemadministrator **möchte ich** Betriebsmetriken des Services abrufen (z.B. Request-Raten, Memory-Nutzung, Datenbankverbindungen), **damit** ich Performance-Probleme frühzeitig erkennen kann.

**Priority:** Should Have | **Story Points:** 3 | **Status:** Done

**Acceptance Criteria:**
- GET `/actuator/metrics` gibt eine Liste verfügbarer Metriken zurück
- Einzelne Metriken sind unter `/actuator/metrics/{metricName}` abrufbar
- Relevante Metriken (HTTP-Response-Zeiten, JVM-Memory, DB-Pool-Auslastung) sind verfügbar
- Das Format ist Prometheus-kompatibel oder in ein standardisiertes Format exportierbar
- Metriken werden fortlaufend aktualisiert und spiegeln den aktuellen Systemzustand wider

---

## Zusammenfassung

| Epic | Stories | Story Points | Must Have | Should Have | Could Have |
|------|---------|-------------|-----------|-------------|------------|
| Epic 1 — EPCIS Query API | 13 | 39 | 10 | 2 | 1 |
| Epic 2 — Legacy Query API | 6 | 14 | 3 | 3 | 0 |
| Epic 3 — Inventory Service | 7 | 41 | 5 | 2 | 0 |
| Epic 4 — CBV Vocabulary & Validierung | 8 | 19 | 5 | 2 | 1 |
| Epic 5 — GS1 Digital Link | 8 | 27 | 4 | 3 | 1 |
| Epic 6 — Subscriptions & Webhooks | 5 | 15 | 4 | 1 | 0 |
| Epic 7 — Outbox & Reliable Delivery | 4 | 12 | 4 | 0 | 0 |
| Epic 8 — Health, Metrics & Betrieb | 4 | 8 | 2 | 2 | 0 |
| **Gesamt** | **55** | **175** | **37** | **15** | **2** |

---

*Dokument erstellt: 2026-04-16 | Autor: Senior Product Manager | Projekt: EPCIS 2.0 Repository C&A | Status: Rückwirkend dokumentiert — alle Stories Done*
