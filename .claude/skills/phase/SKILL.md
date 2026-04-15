# /phase — Neue Implementierungsphase starten

Erstelle ein vollständiges Spec-Dokument für die nächste Implementierungsphase des EPCIS Repository.
Dieses Dokument ist die Grundlage für die Implementierung — es wird VOR dem Code geschrieben.

## Was du vom Nutzer brauchst

Frage nach:
1. **Phasennummer und Name** (z.B. "Phase 5 — SGTIN State Matrix")
2. **Ziel in einem Satz** (Was kann das System nach dieser Phase, was es vorher nicht konnte?)
3. **Business-Anforderungen** (REQ-Nummern oder Beschreibungen)
4. **Was NICHT in diese Phase gehört** (Negativ-Scope)

## Schritt 1 — Aktuellen Stand ermitteln

Lies `docs/event-handler-overview.md` um den aktuellen Phasen-Status zu sehen.
Lies den letzten Phasen-Spec (z.B. `docs/CBV_DIGITALLINK_PHASE4_SPEC.md`) als Vorlage.

## Schritt 2 — Spec-Dokument erstellen

Erstelle `docs/<NAME>_PHASE<N>_SPEC.md` mit folgendem Aufbau:

```markdown
# EPCIS <Name> — Phase <N> Implementierungsspezifikation
## Für Claude Code

---

## KONTEXT

**Projekt:** epcis-repository (Spring Boot 4.x, Java 17+, PostgreSQL, Maven)
**Vorherige Phasen:** [Liste mit Status ✅]
**Phase <N> Ziel:** [Ein Satz]

**Was nach Phase <N> möglich ist:**
- [Business-Capability 1]
- [Business-Capability 2]

**Was Phase <N> NICHT implementiert:**
- [Expliziter Negativ-Scope 1] — Phase X
- [Expliziter Negativ-Scope 2] — Phase Y

---

## PHASE <N> SCOPE

### Block A — [Name] (Priorität: HOCH)
[Anforderungen]

### Block B — [Name] (Priorität: MITTEL)
[Anforderungen]

---

## PROJEKTSTRUKTUR — ERWEITERUNG

Bestehende Klassen NICHT anfassen — nur erweitern.

\`\`\`
com.canda.epcis/
├── application/
│   └── <neu>/          # NEU Phase <N>
│       └── ...
├── domain/
│   └── model/
│       └── <neu>/      # NEU
└── infrastructure/
    └── persistence/
        └── <neu>/      # NEU
\`\`\`

---

## DATENBANKSCHEMA

### Neue Tabellen (Flyway V<N>)

\`\`\`sql
CREATE TABLE <tabellenname> (
    id BIGSERIAL PRIMARY KEY,
    -- Spalten
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
\`\`\`

---

## API-ENDPUNKTE

| Methode | Pfad | Request | Response | Beschreibung |
|---|---|---|---|---|
| GET | /api/... | — | ... | ... |

---

## TEST-ANFORDERUNGEN

### Unit Tests
- [ ] [Klasse]Test: [Was getestet wird]

### Integration Tests
- [ ] [Klasse]IntegrationTest: [Was getestet wird]

### Fixture Tests
- [ ] `src/test/resources/fixtures/<name>.xml`

---

## IMPLEMENTIERUNGSREIHENFOLGE

1. Flyway Migration V<N>
2. Domain Model / Value Objects
3. Repository Interfaces
4. Application Service
5. REST Controller
6. Tests
7. Dokumentation aktualisieren
```

## Schritt 3 — Roadmap-Tabelle aktualisieren

Öffne `docs/event-handler-overview.md` und aktualisiere die Phasen-Roadmap-Tabelle:
- Neue Phase hinzufügen mit Status "🔄 In Arbeit"

## Schritt 4 — Bestätigung

Zeige dem Nutzer:
- Den Pfad des erstellten Spec-Dokuments
- Eine kurze Zusammenfassung von Scope und Negativ-Scope
- Die vorgeschlagene Implementierungsreihenfolge

Frage: "Soll ich mit der Implementierung beginnen?"
