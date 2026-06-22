---
title: "Arbeitsauftrag: Cache-Selbstzerstörung durch scanChunkColumns reparieren"
quelle: "Ad-hoc – Analyse nach Diagnose-Logging (aufgabe-logging-diagnose-snow-growth.md)"
created: "2025-06-19"
status: in-progress
---

# Arbeitsauftrag: Cache-Selbstzerstörung durch scanChunkColumns reparieren

**Quelle:** Ad-hoc – basierend auf Diagnose-Logs und Code-Analyse vom 2025-06-19

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
`scanChunkColumns` zerstört den Zustand von `pluginSnowHeight`, weil bei jedem Re-Scan pauschal `pluginSnowHeight[idx] = 0` gesetzt wird. Dadurch wandern alle Plugin-Platzierungen in `naturalSnowHeight`, und `processChunk` findet keine freien Spalten mehr (alreadySnow=256). Zusätzlich muss `markDirty` in `processChunk` und `growSnowInChunk` aufgerufen werden, damit der ChunkCacheStore die Daten persistiert.

## Aktuelles Ergebnis
- Log zeigt `eligible=256, alreadySnow=256` für fast alle Chunks während Regen.
- `pluginSnowHeight` wird bei jedem TTL-bedingten Re-Scan zurückgesetzt.
- `chunk_cache.json` bleibt leer, weil `markDirty()` nirgends aufgerufen wird.
- `totalPluginSnowColumns` und andere Zähler werden nach Re-Scan nie wieder aufgebaut.

## Ursachenverdacht (bestätigt)
1. `scanChunkColumns` setzt `naturalSnowHeight` aus physischem Schnee, aber `pluginSnowHeight = 0` ohne Prüfung, ob der Schnee Plugin-Ursprung ist.
2. Dadurch wird `pluginSnowHeight` nach jedem Re-Scan zu 0, `snowCovered` zählt nur noch `naturalSnowHeight>0`, aber `totalPluginSnowColumns` bleibt 0.
3. `processChunk` selektiert nur Spalten mit `pluginSnowHeight==0 && naturalSnowHeight==0` – also nach Re-Scan: keine.
4. `markDirty` wird nie aufgerufen → keine Persistenz.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `weather/SnowAccumulator.java` | `scanChunkColumns`, `processChunk`, `growSnowInChunk` |
| `weather/ChunkCacheEntry.java` | Felder `pluginSnowHeight`, `naturalSnowHeight`, `totalPluginSnowColumns`, etc. |
| `persistence/ChunkCacheStore.java` | `markDirty` vorhanden, aber nie aufgerufen |

## ToDo-Liste

### Schritt 1: scanChunkColumns korrigieren
- [x] Statt `pluginSnowHeight[idx] = 0` prüfen: Ist physisch Schnee vorhanden UND `pluginSnowHeight[idx] > 0`? Dann Plugin-Schnee beibehalten.
- [x] Wenn physisch Schnee vorhanden ist und `pluginSnowHeight[idx] == 0`, dann ist es Vanilla-Schnee → `naturalSnowHeight` setzen.
- [x] Wenn physisch KEIN Schnee vorhanden ist, aber `pluginSnowHeight[idx] > 0` → Schnee geschmolzen → `pluginSnowHeight = 0`, `snowCovered--`, `totalPluginSnowColumns--` (Stale-Reset).
- [x] `totalPluginSnowColumns` nach dem Scan aus `pluginSnowHeight` korrekt berechnen (Summe über alle Spalten mit `pluginSnowHeight > 0`).
- [x] `snowCovered` korrekt berechnen (Anzahl Spalten mit `pluginSnowHeight + naturalSnowHeight > 0`).

### Schritt 2: markDirty in processChunk und growSnowInChunk aufrufen
- [x] `processChunk`: Nach jeder erfolgreichen Platzierung `chunkCacheStore.markDirty(chunkKey)` aufrufen (wenn `placed > 0`).
- [x] `growSnowInChunk`: Nach erfolgreichem Wachstum `markDirty` aufrufen (wenn `grown > 0`).
- [x] `SnowAccumulator` braucht eine Referenz auf `ChunkCacheStore` – über Setter `setChunkCacheStore()` gelöst, in `SeasonsPlugin.onEnable()` aufgerufen.

### Schritt 3: Temperature-invalidierung prüfen
- [x] Sicherstellen, dass der Temperatur-Tolerance-Check in `getOrComputeCache` Re-Scans verhindert, solange die Temperatur stabil ist. Der Check ist korrekt implementiert, keine Änderung nötig.

### Schritt 4: Build, Deploy, Test
- [x] `.\gradlew.bat compileJava && .\gradlew.bat shadowJar` (Build erfolgreich)
- [ ] SCP-Deploy, Server-Restart
- [ ] Auf Regen warten, Logs prüfen: `alreadySnow` sollte gegen 0 gehen, `placed` steigen.

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Bei Annäherung → auslagern
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

## Sync nach Abschluss
- `Plannung/bugfixes/aufgabe-logging-diagnose-snow-growth.md` → auf diese Karte verweisen
- `docs/handover.md` → Status aktualisieren
- `Plannung/roadmap.md` → Bugfixes vermerken