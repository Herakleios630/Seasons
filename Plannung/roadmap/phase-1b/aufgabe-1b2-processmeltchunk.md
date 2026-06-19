---
title: "Arbeitsauftrag: processMeltChunk implementieren"
quelle: "roadmap.md → Phase 1b, Sprint 1b.2"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: processMeltChunk implementieren

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Kern-Methode `processMeltChunk(Chunk chunk, ChunkCacheEntry cache)` im `SnowMeltManager` implementieren. Nur Plugin-Schnee-Spalten werden um je 1 Layer reduziert (Layer by Layer). Natürlicher Vanilla-Schnee bleibt unangetastet.

## Vorbedingungen
- Sprint 1b.1: `SnowMeltManager`-Klasse existiert mit `accumulateMelt()`-Gerüst
- Phase 1a: `ChunkCacheEntry` mit Feldern `pluginSnowHeight[]`, `naturalSnowHeight[]`, `snowCovered`, `totalPluginSnowColumns` ist vorhanden
- `scanChunkColumns()` mit HeightMap MOTION_BLOCKING + 1-Block-Fallback ist implementiert

## Aktuelles Ergebnis
- `SnowAccumulator.processMeltChunk()` (alt) schmilzt ALLEN Schnee unabhängig von Plugin/Natürlich – das wollen wir nicht mehr
- Die neue Logik muss differenzieren: `pluginSnowHeight` vs `naturalSnowHeight`

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowMeltManager.java` | Hauptbearbeitung – `processMeltChunk()`, `meltColumn()` |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Lesend: pluginSnowHeight, naturalSnowHeight, schreibend: pluginSnowHeight--, snowCovered--, totalPluginSnowColumns-- |
| `SnowAccumulator.java` | Nicht anfassen (alte Melt-Logik bleibt erstmal, wird in 1b.5 bereinigt) |

## Erbetene Hilfe

1. **`processMeltChunk(Chunk, ChunkCacheEntry)` implementieren:**
   - Alle Spalten mit `pluginSnowHeight[index] > 0` sammeln (Liste von x,z-Paaren)
   - Liste shufflen (wie bei Placement)
   - `layersPerScan` Spalten abarbeiten (Config-Wert, Default 4)
   - Pro Spalte `meltColumn(world, chunk, x, z, cache)` aufrufen

2. **`meltColumn(World, Chunk, int x, int z, ChunkCacheEntry)` implementieren:**
   - `index = x * 16 + z`
   - `plugin = cache.pluginSnowHeight[index]`
   - `natural = cache.naturalSnowHeight[index]`
   - Wenn `plugin <= 0` → return
   - `currentTotal = natural + plugin`
   - `newPlugin = plugin - 1`
   - `newTotal = natural + newPlugin`
   - Ground finden: `chunk.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)` + 1-Block-Fallback
   - Wenn `newPlugin == 0 && natural == 0`: Schnee-Block komplett auf AIR setzen
   - Sonst wenn `newTotal > 0 && newTotal <= 8`: Snow-Layer auf `newTotal` setzen
   - `cache.pluginSnowHeight[index] = newPlugin`
   - `cache.totalPluginSnowColumns--`
   - Wenn `newTotal == 0`: `cache.snowCovered--`

3. **`layersPerScan` Konfigurierbar machen:**
   - Entweder direkt aus `ConfigManager` holen (vorläufig) oder Dummy-Konstante bis Sprint 1b.4
   - Default: 4 (doppelt so schnell wie Growth)

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** `layersPerScan` aus Config
- **Biome nie hardcoden**
- **Season deterministisch**
- **Keine NMS/Reflection**
- **Java-Dateien ≤ 400 Zeilen**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Schichten-Impact)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1b.2 abhaken)