---
title: "Arbeitsauftrag: Snow-Growth-Deadlock – Lücken, Pflanzen & Case-0-Endlosschleife"
quelle: "Server-Live-Test 2025-06-19, aufgabe-1a11-fixes Folgeprobleme"
created: "2025-06-19"
status: offen
---

# Arbeitsauftrag: Snow-Growth-Deadlock

**Quelle:** Server-Live-Test vom 2025-06-19 nach deploytem Fix für aufgabe-1a11-fixes

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
Snow-Akkumulation bleibt nach initialer Platzierung stehen. Kein Wachstum, sichtbare Lücken (Grasblöcke ohne Schnee), Pflanzen werden teilweise nicht ersetzt. Analyse und Behebung der drei Teilprobleme, die gemeinsam den Wachstums-Stillstand verursachen.

## Aktuelles Ergebnis
- Summary: `placed=2674 grown=0 | cache: 585 hits, 23 misses, 43 fullyGrown`
- Danach alle Summaries: `placed=0 grown=0 | cache: 0 hits, 0 misses, 0 fullyGrown`
- Sichtbare Lücken: Grasblöcke ohne jeglichen Schnee in ansonsten verschneiten Chunks
- Pflanzen (Gras, Blumen) bleiben teilweise stehen, statt durch Schnee ersetzt zu werden
- Server-Log zeigt `noGround` und `notCapable` in einigen Chunks, aber auch viele Chunks mit `placed=8` (Maximum pro Tick)
- Nach initialer Platzierung werden keine Chunks mehr von processChunk bearbeitet

## Ursachenverdacht (drei Teilprobleme)

### Teilproblem 1: `tryPlaceColumn()` returned 0 ohne Log/Fehlerbehandlung
- `processChunk()` switch behandelt nur cases 1, -1, -2, -3
- Case 0 (kein Platzieren, aber kein Fehler) wird ignoriert
- Spalte bleibt `pluginSnowHeight=0, naturalSnowHeight=0` im Cache
- Beim nächsten Scan wieder eligible → Endlosschleife ohne Fortschritt
- **Ursache für 0:** `!above.isEmpty() && !isReplaceablePlant(aboveMat)` – ein Block über dem Ground ist weder Luft noch als Pflanze ersetzbar (z.B. unbekannte Pflanze, Slab, Torch, Teppich)

### Teilproblem 2: Lücken werden nie geschlossen
- Sobald `snowCovered >= snowCapable`, gilt Chunk als gesättigt → `processChunk()` wird nie wieder aufgerufen
- Spalten ohne jeglichen Schnee (die aus Teilproblem 1 oder weil sie zufällig nie an der Reihe waren) bleiben für immer leer
- `growSnowInChunk()` ignoriert Spalten mit `totalCurrent == 0`

### Teilproblem 3: Unbekannte Pflanzen blockieren Schnee-Platzierung
- Trotz Erweiterung von `REPLACEABLE_PLANTS` um 1.21.5-Materialien bleiben Pflanzen stehen
- Möglicherweise weitere unbekannte Materialien oder Sonderfälle (z.B. Pflanzen in Blumentöpfen, auf Halbstufen)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `weather/SnowAccumulator.java` | `processChunk()`, `tryPlaceColumn()`, `isReplaceablePlant()`, `REPLACEABLE_PLANTS` |
| `weather/ChunkCacheEntry.java` | Cache-Datenmodell, ggf. neues Feld für blockierte Spalten |

## Erbetene Hilfe (ToDo)

### Schritt 1: Diagnose-Logging einbauen
- [ ] In `processChunk()`: Case 0 (result==0) mitloggen – Welche Weltkoordinaten? Welches Material steht über dem Ground?
- [ ] Zähler `blocked` einführen, der im Chunk-Log mit ausgegeben wird
- [ ] Build & Deploy, 5 Minuten laufen lassen, Logs analysieren

### Schritt 2: Case-0-Behandlung implementieren
- [ ] Entscheidung: Sollen blockierte Spalten aus `snowCapable` rausgerechnet werden, oder in einem separaten Array markiert und von der Eligibility ausgeschlossen werden?
- [ ] Spalten die beim ersten Versuch blockiert sind, beim nächsten TTL-Rescan erneut prüfen (falls sich der Block geändert hat – z.B. Pflanze wurde von Hand entfernt)
- [ ] Alternativ: Beim Scan alle nicht belegbaren Spalten erkennen und von `snowCapable` ausschließen (dann stimmt `snowCovered >= snowCapable` wieder)

### Schritt 3: Pflanzen-Erkennung vervollständigen
- [ ] Aus dem Diagnose-Log die blockierenden Materialien identifizieren
- [ ] Fehlende Materialien zu `REPLACEABLE_PLANTS` hinzufügen
- [ ] Prüfen ob `isReplaceablePlant()` auch Sonderfälle abdeckt (z.B. `SHORT_GRASS` auf nicht-vollem Block)

### Schritt 4: Lücken-Schließung garantieren
- [ ] Sicherstellen dass `processChunk()` so lange läuft, bis WIRKLICH alle Snow-Capable-Spalten mindestens 1 Schnee-Layer haben
- [ ] `isSaturated()` prüft bereits `snowCovered >= snowCapable` (Plugin+Natural). Wenn `snowCapable` um dauerhaft blockierte Spalten reduziert wird, passt das.
- [ ] Build, Deploy, Test: Summary muss nach Sättigung aller Chunks `grown > 0` zeigen

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\\gradlew.bat compileJava`, dann `.\\gradlew.bat shadowJar`
- **Deploy:**
  1. `scp build\\libs\\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

## Sync nach Abschluss
- `Plannung/bugfixes/aufgabe-1a11-fixes.md` → auf diese Karte verweisen
- `docs/handover.md` → Status aktualisieren
- `Plannung/roadmap.md` → Bugfixes vermerken