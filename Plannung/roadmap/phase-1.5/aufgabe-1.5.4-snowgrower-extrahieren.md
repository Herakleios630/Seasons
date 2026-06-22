---
title: "Arbeitsauftrag: SnowGrower extrahieren"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.4"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.4"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: SnowGrower extrahieren

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Wachstumslogik `growSnowInChunk(Chunk, ChunkCacheEntry, TickDiagnostics.ChunkDiag)` und `getMaxSnowHeight(double)` aus `SnowAccumulator` in eine neue Klasse `SnowGrower.java` auslagern. Dabei die Sättigungs-Prüfung aus 1.5.2 integrieren.

**SnowGrower: ~100 Zeilen.**

**Was nach SnowGrower wandert:**
- `growSnowInChunk(Chunk chunk, ChunkCacheEntry cache, TickDiagnostics.ChunkDiag diag)`
- `getMaxSnowHeight(double temperature)`

**Konstruktor SnowGrower:** `SnowGrower(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)` – braucht Temperaturberechnung für Höhenlimit und Config für growthLayersPerScan sowie saturationThreshold.

**Sättigungs-Prüfung:** In `growSnowInChunk` wird vor dem Sammeln der growable-Spalten geprüft: `if (!cache.isSaturated(weatherConfig.getSaturationThreshold()))` → dann nicht growen, sondern zurückgeben (Platzierung ist noch nicht fertig). Der Aufrufer (SnowAccumulator) prüft das aber bereits – doppelte Prüfung ist OK.

**markDirty:** Wie bei SnowPlacer: `chunkCacheStore.markDirty(chunkKey)` wenn `grown > 0`.

## Aktuelles Ergebnis
`growSnowInChunk` sitzt in `SnowAccumulator` (~120 Z.) mit eigener Temperaturberechnung, Stale-Erkennung, Adoption von Naturschnee. `getMaxSnowHeight` wird auch in `scanChunkColumns` (jetzt in ChunkCacheManager) verwendet. `getMaxSnowHeight` sollte daher entweder in SnowGrower bleiben (und ChunkCacheManager ruft sie über den SnowGrower auf) oder in eine Shared-Utility-Klasse. Entscheidung: `getMaxSnowHeight` bleibt in SnowGrower; ChunkCacheManager bekommt eine Referenz auf SnowGrower oder WeatherConfig (für freezeThreshold/heightPerCold) und berechnet selbst. Da scanChunkColumns aber `getMaxSnowHeight` nur für `snowBelowMax` braucht, kann ChunkCacheManager diese Berechnung auch inline machen.

**Besser:** `getMaxSnowHeight` nach SnowGrower auslagern, und ChunkCacheManager bekommt die Methode ebenfalls (oder ruft sie via SnowGrower auf). Da SnowGrower aber erst nach ChunkCacheManager entsteht, wäre das eine zirkuläre Abhängigkeit. Lösung: `getMaxSnowHeight` als statische Methode in SnowGrower oder als Instanzmethode – ChunkCacheManager bekommt dann den `WeatherConfig` direkt und rechnet selbst.

**Einfachste Lösung:** `getMaxSnowHeight` bleibt als private Methode in ChunkCacheManager (für scanChunkColumns) und als Instanzmethode in SnowGrower. Doppelt, aber entkoppelt. Oder: `getMaxSnowHeight` wird in eine Utility-Klasse `SnowHeightCalculator` ausgelagert, die beide nutzen. Das ist Overkill für eine 3-Zeilen-Methode.

**Pragmatisch:** In ChunkCacheManager die Berechnung inline lassen (ist nur `(freezeThreshold - temp) / 0.2 * heightPerCold`), in SnowGrower eine eigene `getMaxSnowHeight`-Methode. Keine neue Utility-Klasse.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowGrower.java` | **NEU** – Wachstumslogik |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | growSnowInChunk entfernen, Aufruf umleiten |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | getMaxSnowHeight bleibt/stirbt hier (s. Entscheidung) |

## Erbetene Hilfe
1. `SnowGrower.java` erstellen:
   - Konstruktor: `(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)`
   - `growSnowInChunk(Chunk chunk, ChunkCacheEntry cache, TickDiagnostics.ChunkDiag diag, String chunkKey)` → letzter Parameter für markDirty
   - `getMaxSnowHeight(double temperature)` → identisch aus SnowAccumulator kopieren
   - Sättigungs-Prüfung vor Wachstum: `if (!cache.isSaturated(weatherConfig.getSaturationThreshold())) return;`
   - markDirty am Ende wenn grown > 0

2. `SnowAccumulator.java`:
   - Feld `SnowGrower snowGrower` hinzufügen
   - `growSnowInChunk` und `getMaxSnowHeight` entfernen
   - Aufruf in `accumulateSnow()`: `snowGrower.growSnowInChunk(chunk, cache, diag, chunkKey)`

3. `ChunkCacheManager.java` prüfen:
   - `getMaxSnowHeight` wird in `scanChunkColumns` verwendet → entweder als private Methode dort behalten oder die Berechnung inline machen

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
5. Kein Deployment in diesem Schritt

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine NMS/Reflection in Phase 1**
- **Java-Dateien ≤ 400 Zeilen**
- **Build nach jeder Änderung**
- **Kein Deployment ohne Nutzer-Freigabe**