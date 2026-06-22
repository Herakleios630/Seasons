---
title: "Arbeitsauftrag: getOrComputeCache + TTL"
quelle: "roadmap.md → Phase 1a, Sprint 1a.3"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: done
---

# Arbeitsauftrag: getOrComputeCache + TTL

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die zentrale Methode `getOrComputeCache(World, Chunk)` implementieren, die:
- Im Cache nachschaut (Key `worldUID:chunkKey`)
- Bei Miss: `scanChunkColumns(chunk)` aufruft
- TTL prüft (30s default, config-gesteuert)
- Temperatur-Toleranz prüft (aktuelles Temp-Level vs gespeichertes Intervall)

## Vorbedingungen
- 1a.1: Cache-Struktur existiert
- 1a.2: `scanChunkColumns()` implementiert

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `getOrComputeCache()` implementieren |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Nutzung der Helper |

## Erbetene Hilfe

1. **`getOrComputeCache(World, Chunk): ChunkCacheEntry`:**
   - Key bauen: `String key = world.getUID() + ":" + chunk.getChunkKey()`
   - `cache.get(key)` → wenn vorhanden:
     - TTL prüfen: `(System.currentTimeMillis() - entry.lastUpdated) / 1000 > ttlSeconds` → Cache-Miss
     - Temperatur-Toleranz prüfen: aktuelles TempLevel ausrechnen, mit `[tempLevelMin, tempLevelMax]` vergleichen
     - Bei Gültigkeit: Cache-Hit → return entry
   - Bei Miss: `entry = scanChunkColumns(chunk)`, `cache.put(key, entry)`, return entry

2. **Zähler für Summary-Log vorbereiten:**
   - `cacheHits`, `cacheMisses` (werden später im Log verwendet)

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** TTL aus Config
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md`
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.3 abhaken)