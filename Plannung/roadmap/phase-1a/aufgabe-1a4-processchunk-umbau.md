---
title: "Arbeitsauftrag: processChunk (Platzierung) umbauen + grow entfernen"
quelle: "roadmap.md → Phase 1a, Sprint 1a.4"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: processChunk (Platzierung) umbauen + grow entfernen

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`processChunk` so umbauen, dass es NUR noch platziert (kein grow mehr). Grow wird in eigener Methode (1a.5) implementiert. Die Trennung folgt dem Konzept:
- `processChunk` (Platzierung): läuft nur, wenn Chunk nicht gesättigt ist
- Kein `tryGrowColumn()` mehr innerhalb von `processChunk`
- `enoughNeighborsSnowOrBlocked` bleibt für Platzierung, aber wird später entfernt (1a.9)

## Vorbedingungen
- 1a.3: `getOrComputeCache()` existiert
- `SnowAccumulator.accumulateSnow()` iteriert Chunks

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `processChunk` umbauen, grow-Aufrufe entfernen |

## Erbetene Hilfe

1. **`accumulateSnow` ändern:**
   - Statt direkt `processChunk` aufzurufen, erst Cache holen: `cache = getOrComputeCache(chunk)`
   - Wenn `cache.isFullyGrown()` → continue (0ms)
   - Wenn `cache.isSaturated()` → später `growSnowInChunk(chunk, cache)` (in 1a.5)
   - Sonst: `processChunk(chunk, cache)` (Platzierung)

2. **`processChunk(Chunk, ChunkCacheEntry)` umbauen:**
   - NUR Platzierung: Spalten ohne Schnee (wo `pluginSnowHeight == 0 && naturalSnowHeight == 0`)
   - Liste der platzierbaren Spalten bauen, shufflen, `layersPerScan` abarbeiten
   - Schnee-Layer 1 setzen, `cache.pluginSnowHeight[index] = 1`, `cache.snowCovered++`, `cache.totalPluginSnowColumns++`
   - `tryGrowColumn()` komplett entfernen
   - `enoughNeighborsSnowOrBlocked` für Platzierung prüfen (bleibt erstmal drin, wird in 1a.9 entfernt)

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine NMS/Reflection**
- **Java-Dateien ≤ 400 Zeilen**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Platzierung vs Wachstum)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.4 abhaken)