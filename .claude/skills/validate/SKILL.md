# /validate — EPCIS Full Validation Check

Führe einen vollständigen Qualitäts- und Konformitäts-Check für das EPCIS Repository durch.
Führe alle Schritte sequenziell aus und berichte das Ergebnis als strukturierte Zusammenfassung.

## Schritt 1 — Kompilierung

Führe aus:
```
./mvnw compile -q --no-transfer-progress 2>&1
```
Bewerte: Gibt es Compile-Fehler? Wenn ja, liste sie auf und stoppe hier.

## Schritt 2 — Test-Suite

Führe aus:
```
./mvnw test -q --no-transfer-progress 2>&1
```
Bewerte: Wie viele Tests laufen? Wie viele schlagen fehl? Zeige fehlgeschlagene Tests mit Ursache.

## Schritt 3 — Architektur-Layer-Check

Prüfe ob `infrastructure`-Imports in `domain/` oder `application/` vorhanden sind:
```
grep -rn 'import com.canda.epcis.infrastructure' \
  src/main/java/com/canda/epcis/domain/ \
  src/main/java/com/canda/epcis/application/ 2>/dev/null
```
Bewerte: Neue Verletzungen seit dem letzten bekannten Stand? Bekannte Verletzungen aus dem Enterprise Readiness Assessment sind: CaptureEventUseCase, ConvertEventUseCase, CbvValidationService, CbvVocabularyLoader, DigitalLinkResolverService, SubscriptionDispatcher, SubscriptionService, InventoryRebuildService, InventoryQueryService, InventoryProcessorService, QueryEventUseCase, EventRenderer port.

## Schritt 4 — Stille Fehler Check

Suche nach potenziell stillen Fehlern im Code:
```
grep -rn 'catch.*Exception.*{' src/main/java/ 2>/dev/null | grep -v '//.*catch' | head -20
grep -rn 'return null' src/main/java/com/canda/epcis/domain/ src/main/java/com/canda/epcis/application/ 2>/dev/null | head -10
```
Bewerte: Gibt es catch-Blöcke die nur loggen aber weiterlaufen? Gibt es `return null` in Domain- oder Application-Klassen?

## Abschlussbericht

Gib eine strukturierte Zusammenfassung aus:

```
## EPCIS Validation Report

### Compile       ✅/❌
### Tests         ✅/❌  (X passed, Y failed)
### Architektur   ✅/⚠️  (neue Verletzungen: ja/nein)
### Stille Fehler ✅/⚠️

### Kritische Punkte (falls vorhanden)
...

### Empfehlung
BEREIT FÜR COMMIT / NICHT BEREIT — [Begründung]
```
