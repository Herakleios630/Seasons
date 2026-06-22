---
title: "Arbeitsauftrag: Diagnose-Logging für Snow-Placement & Growth"
quelle: "Ad-hoc – aus aktuellem Server-Log nach Fix für Stale-Cache-Reset"
created: "2025-06-19"
status: done
---

# Arbeitsauftrag: Diagnose-Logging für Snow-Placement & Growth

**Quelle:** Ad-hoc – basierend auf Server-Logs nach Deployment des Stale-Cache-Fixes

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
Systematisches Diagnose-Logging in `SnowAccumulator` einbauen, das den gesamten Prozess – vom Chunk-Scan über die Column-Auswahl bis zur Platzierung und zum Growth – detailliert nachvollziehbar macht. Keine Änderungen an der Geschäftslogik, nur zusätzliche Log-Ausgaben, um die Ursache des Snow-Growth-Stillstands zu identifizieren.

## Aktuelles Ergebnis
- Nach initialem `placed=...` sinkt die Platzierungsrate auf 0, obwohl viele Chunks `snowCapable > 0` und nicht voll gesättigt/fullyGrown sind.
- `alreadySnow=256`-Einträge deuten auf Cache-State hin, der physisch nicht zur Realität passt (Spalten gelten als mit Schnee bedeckt, obwohl sie es nicht sind).
- `growSnowInChunk` zeigt kein Log, selbst wenn es aufgerufen wird.
- Der Stale-Cache-Reset (wenn `top.getType() != Material.SNOW && != SNOW_BLOCK`) wurde korrigiert, aber wir wissen nicht, wie oft er auslöst.
- Ozean-Chunks werden korrekt geskippt, aber andere Chunks verhalten sich unerwartet.

## Ursachenverdacht
1. **Cache-Integrität:** Stale-Cache-Resets passieren häufiger als gedacht; `snowCovered` könnte danach noch inkorrekt sein (andere Stellen, die `snowCovered` ändern ohne physische Prüfung).
2. **Eligibility-Logik:** `processChunk` wählt Spalten mit `pluginSnowHeight==0 && naturalSnowHeight==0`. Wenn der Cache sagt, viele Spalten haben Schnee, obwohl physisch keiner da ist, werden diese nie als eligible gesehen – Deadlock.
3. **Sättigungs-Logik:** `isSaturated()` könnte true sein, obwohl physisch Lücken existieren, wodurch nur noch `growSnowInChunk` aufgerufen wird, das aber nichts findet.
4. **Blockierte Spalten:** `blocked`-Spalten (result=-4) werden nicht aus `snowCapable` entfernt – der Chunk kann nie sättigen, falls `blocked` häufig auftritt.
5. **NoGround-Problem:** `noGround` zählt hoch für viele Spalten. Sind das echte Hindernisse oder Fehler in der Ground-Erkennung?

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `weather/SnowAccumulator.java` | Hauptakteur – alle Log-Statements hier einbauen |
| `weather/ChunkCacheEntry.java` | Unterstützung für neue Zähler (optional) |

## ToDo-Liste

### Schritt 1: Tick-weises Tracking einführen
- [ ] Zwei `ConcurrentHashMap<String, TickDiagnostics>` für Placement und Growth pro Tick
- [ ] In `accumulateSnow` vor der Schleife eine Instanz erstellen, nach der Schleife ausgeben
- [ ] `TickDiagnostics` enthält: chunkKey, Modus (placed/grown/saturated/fullyGrown/ocean), Details (Anzahl eligible, attempts, results, staleResets, etc.)

### Schritt 2: processChunk detailliert loggen
- [ ] Vor Schleife: Anzahl eligible Spalten loggen (DEBUG-Level)
- [ ] Nach jeder tryPlaceColumn: Ergebnis mitzählen (bereits vorhanden)
- [ ] Nach Durchlauf: Zähler für stale cache resets während des Prozesses (falls zutreffend)
- [ ] Besonderes Augenmerk auf `alreadySnow` – woher kommt der Eintrag im Cache, wenn die physische Spalte keinen Schnee hat?

### Schritt 3: growSnowInChunk detailliert loggen
- [ ] Anzahl growable Spalten vor der Schleife loggen (wir haben jetzt ein Log nach Wachstum, aber kein Pre-Log)
- [ ] Stale-Cache-Resets zählen und mit Weltkoordinaten loggen (erste N Vorkommen)
- [ ] Grund für Nicht-Wachstum (totalCurrent==0, totalCurrent>=8, temp-Limit, SNOW_BLOCK) als Zähler mitloggen

### Schritt 4: accumulateSnow Gesamtstatus pro Tick
- [ ] Am Ende von accumulateSnow: Zusammenfassung der besuchten Chunks, wie viele in welchem Modus, wie viele placed/grown insgesamt, wie viele Stale-Resets, wie viele Ozeane, etc.
- [ ] Alle 10 Ticks eine konsolidierte Zeile ins Info-Log

### Schritt 5: Stale-Cache-Reset Tracking
- [ ] In beiden Methoden (processChunk, growSnowInChunk) bei jeder Änderung der cache counters (pluginSnowHeight, naturalSnowHeight, snowCovered, totalPluginSnowColumns, snowBelowMax) einen separaten Log-Eintrag machen, wenn der Reset ausgelöst wurde.
- [ ] `staleResets` Zähler im Tick-Log ausgeben.

### Schritt 6: Build, Deploy, Test
- [ ] `.\gradlew.bat compileJava` && `.\gradlew.bat shadowJar`
- [ ] SCP-Deploy, Server-Restart
- [ ] 10 Minuten laufen lassen, Logs sammeln, analysieren
- [ ] Anhand der Logs Root-Cause identifizieren und dann einen gezielten Fix-Bug erstellen

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Bei Bedarf Hilfsklasse für Diagnostics auslagern
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

## Sync nach Abschluss
- `Plannung/bugfixes/aufgabe-snow-growth-deadlock.md` → auf diese Karte verweisen
- `docs/handover.md` → Status aktualisieren
- `Plannung/roadmap.md` → Bugfixes vermerken