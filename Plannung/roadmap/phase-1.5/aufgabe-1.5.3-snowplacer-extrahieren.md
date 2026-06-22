---
title: "Arbeitsauftrag: SnowPlacer extrahieren"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.3"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.3"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: SnowPlacer extrahieren

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Platzierungslogik `processChunk(Chunk, ChunkCacheEntry, TickDiagnostics.ChunkDiag)` und `tryPlaceColumn(int, int, World, int)` aus `SnowAccumulator` in eine neue Klasse `SnowPlacer.java` auslagern. Dazu die benötigten Hilfsmethoden (`isSnowCapable`, `isColumnPlaceable`, `isReplaceablePlant`, `getGroundBlock`, `removePlantAt`, `REPLACEABLE_PLANTS`, `DOUBLE_PLANTS`) ebenfalls dorthin verschieben.

**SnowPlacer: ~120 Zeilen.**

**Was nach SnowPlacer wandert:**
- `processChunk(Chunk chunk, ChunkCacheEntry cache, TickDiagnostics.ChunkDiag diag)`
- `tryPlaceColumn(int wx, int wz, World world, int dayOfYear)`
- `isSnowCapable(Block)`
- `isColumnPlaceable(Block)`
- `isReplaceablePlant(Material)`
- `getGroundBlock(World, int, int)`
- `removePlantAt(Block)`
- `REPLACEABLE_PLANTS` (EnumSet)
- `DOUBLE_PLANTS` (EnumSet)

**Was in SnowAccumulator verbleibt (bis 1.5.6):**
- Tick-Loop
- growSnowInChunk
- processMeltChunk
- getMaxSnowHeight
- Summary-Log & Diagnostik

**Konstruktor SnowPlacer:** `SnowPlacer(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)` – braucht all das, weil `tryPlaceColumn` Temperatur berechnen muss und `markDirty` den Store braucht.

**Pflanzen-Tracking vorbereiten:** `SnowPlacer` führt eine `List<Block> removedPlants` pro Chunk, die bei `removePlantAt` befüllt wird. Diese Liste wird später von `SnowMelter` zur Wiederherstellung genutzt (via ChunkCacheEntry? oder als Rückgabewert? → Rückgabewert: `processChunk` gibt `int placed` zurück plus die removedPlants-Liste als Parameter).

## Aktuelles Ergebnis
`SnowAccumulator` enthält `processChunk` (~80 Z.) und `tryPlaceColumn` (~70 Z.) plus die ganzen Hilfsmethoden. Auslagerung ist rein mechanisch – das Verhalten soll 1:1 erhalten bleiben.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowPlacer.java` | **NEU** – Platzierungslogik |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Methoden entfernen, Aufrufe umleiten |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Unverändert |

## Erbetene Hilfe
1. `SnowPlacer.java` erstellen:
   - Konstruktor: `(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)`
   - `processChunk(Chunk, ChunkCacheEntry, TickDiagnostics.ChunkDiag)` → kopiert aus SnowAccumulator
   - `tryPlaceColumn(int wx, int wz, World world, int dayOfYear)` → kopiert
   - Alle Hilfsmethoden und Material-Sets kopieren
   - `markDirty` via `chunkCacheStore.markDirty(SnowAccumulator.buildCacheKey(chunk))` – oder die Key-Erstellung übernimmt der Aufrufer (besser: SnowAccumulator übergibt den key als Parameter)

2. `SnowAccumulator.java`:
   - Feld `SnowPlacer snowPlacer` hinzufügen, im Konstruktor instanziieren
   - `processChunk`, `tryPlaceColumn`, `isSnowCapable`, `isColumnPlaceable`, `isReplaceablePlant`, `getGroundBlock`, `removePlantAt`, `REPLACEABLE_PLANTS`, `DOUBLE_PLANTS` entfernen
   - Aufruf in `accumulateSnow()`: `snowPlacer.processChunk(chunk, cache, diag)`

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
4. Kein Deployment in diesem Schritt

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine NMS/Reflection in Phase 1**
- **Java-Dateien ≤ 400 Zeilen**
- **Build nach jeder Änderung**
- **Kein Deployment ohne Nutzer-Freigabe**