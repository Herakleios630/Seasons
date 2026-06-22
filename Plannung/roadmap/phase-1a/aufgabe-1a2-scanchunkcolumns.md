---
title: "Arbeitsauftrag: scanChunkColumns mit HeightMap MOTION_BLOCKING"
quelle: "roadmap.md → Phase 1a, Sprint 1a.2"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: done
---

# Arbeitsauftrag: scanChunkColumns mit HeightMap MOTION_BLOCKING

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die neue Methode `scanChunkColumns(Chunk)` implementieren, die pro Spalte:
- `naturalSnowHeight` und `pluginSnowHeight` ermittelt
- `snowCapable`, `snowCovered`, `snowBelowMax` berechnet
- `findColumnGround` wird durch HeightMap MOTION_BLOCKING + 1-Block-Fallback ersetzt

## Vorbedingungen
- 1a.1: `ChunkCacheEntry` existiert

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Neue Methode `scanChunkColumns`, alte `findColumnGround` entfernen |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Wird befüllt |

## Erbetene Hilfe

1. **`scanChunkColumns(Chunk): ChunkCacheEntry` implementieren:**
   - Für jedes x,z (0..15):
     - `topY = chunk.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)`
     - `topBlock = chunk.getBlock(x, topY, z)`
     - Wenn `topBlock.isSolid()` → ground = topBlock; sonst ground = chunk.getBlock(x, topY-1, z)
     - Aktuelle Schneehöhe messen: `highestSnowLayer` prüfen
     - Wenn Erst-Scan: `naturalSnowHeight = aktuelle Höhe`, `pluginSnowHeight = 0`
     - `snowCapable` inkrementieren wenn ground snow-capable
     - `snowCovered` wenn Schnee >0
     - `snowBelowMax` wenn unter Grow-Limit
   - Temperatur-Intervall `[tempLevelMin, tempLevelMax]` setzen

2. **Alte `findColumnGround` entfernen:**
   - Wird nicht mehr gebraucht
   - Auf alle Aufrufer prüfen

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- HeightMap.MOTION_BLOCKING ist O(1)
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Scan-Algorithmus dokumentieren)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.2 abhaken)