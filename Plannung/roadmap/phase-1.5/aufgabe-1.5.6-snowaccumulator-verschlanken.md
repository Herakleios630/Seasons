---
title: "Arbeitsauftrag: SnowAccumulator verschlanken"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.6"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.6"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: SnowAccumulator verschlanken

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`SnowAccumulator.java` auf einen reinen Orchestrator reduzieren (≤ 100 Zeilen). Alle Fachlogik wurde in den vorherigen Schritten ausgelagert (ChunkCacheManager, SnowPlacer, SnowGrower, SnowMelter). Dieser Schritt vollendet die Verschlankung und ersetzt den Season-basierten Trigger durch eine Temperatur-basierte Modus-Wahl (Fix #3).

**Nach diesem Schritt hat SnowAccumulator nur noch:**
- Konstruktor mit allen Abhängigkeiten
- `start()` – startet den BukkitRunnable-Tick
- Tick-Loop: Welt-Iteration, Chunk-Iteration, Temperatur-basierte Modus-Wahl
- `invalidateChunk(Chunk)` – öffentliche Delegation an ChunkCacheManager
- `clearCache()` – öffentliche Delegation
- `getCache()` – für ChunkCacheStore (oder entfernen, wenn ChunkCacheStore direkt den ChunkCacheManager nutzt)
- Summary-Log

**Fix #3 – Temperatur-basierter Trigger:**
Statt:
```java
if (clock.getCurrentSeason() == Season.WINTER) {
    accumulateSnow(world);
} else {
    meltSnow(world);
}
```
Neu:
```java
// Für jeden Chunk wird die Temperatur an repräsentativer Spalte (8,8) berechnet
// Wenn temp < freezeThreshold UND world.hasStorm(): Growth/Platzierung
// Wenn temp >= meltThreshold: Schmelze
// Sonst: nichts (kalter Tag ohne Regen, etc.)
```

**Wichtig:** Der Check `world.hasStorm()` bleibt für Growth/Platzierung erhalten (Schnee nur bei Niederschlag). Für Schmelze gilt er NICHT – Schnee schmilzt auch bei Sonnenschein.

**Zielgröße: ≤ 100 Zeilen.**

## Aktuelles Ergebnis
`SnowAccumulator` ist nach den Auslagerungen (1.5.1–1.5.5) bereits deutlich schlanker, enthält aber noch die alten `accumulateSnow`/`meltSnow`-Methoden mit Season-basiertem Trigger und direkten Aufrufen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Verschlanken zum Orchestrator |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Ggf. ChunkCacheStore-Bindung anpassen |

## Erbetene Hilfe
1. `SnowAccumulator.java` umbauen:
   - Tick-Loop: `start()` bleibt, aber `accumulateSnow` und `meltSnow` werden zu einer Methode `processWorld(World)` fusioniert
   - `processWorld(World)`: iteriert über geladene Chunks, holt Cache via `cacheManager.getOrComputeCache(chunk)`, berechnet Temperatur an (8,8), wählt Modus:
     - `temp < freezeThreshold && world.hasStorm()` → wenn nicht gesättigt: `snowPlacer.processChunk(...)`, sonst `snowGrower.growSnowInChunk(...)`
     - `temp >= meltThreshold` → `snowMelter.processMeltChunk(...)`
     - Sonst: nichts
   - Summary-Log beibehalten
   - `chunkIndex`-Round-Robin beibehalten für faire Chunk-Verteilung
   - `maxChunksPerTick` für Growth, `meltChunksPerTick` für Melt verwenden
   - Alle ausgelagerten Methoden (`processChunk`, `growSnowInChunk`, `processMeltChunk`, `tryPlaceColumn`, Hilfsmethoden) müssen bereits in den vorherigen Schritten entfernt worden sein
   - `getMaxSnowHeight` entfernen (ist jetzt in SnowGrower + ChunkCacheManager)
   - `random`-Feld entfernen (wird von SnowPlacer/SnowGrower/SnowMelter jeweils eigenständig genutzt)

2. Prüfen ob `SeasonsPlugin` noch `snowAccumulator.setChunkCacheStore()` aufruft → wenn ChunkCacheStore jetzt direkt vom ChunkCacheManager referenziert wird, kann der Setter entfallen

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
4. Kein Deployment in diesem Schritt (erfolgt in 1.5.7)

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine NMS/Reflection in Phase 1**
- **Java-Dateien ≤ 400 Zeilen** (SnowAccumulator nach diesem Schritt ≤ 100)
- **Build nach jeder Änderung**
- **Kein Deployment ohne Nutzer-Freigabe**