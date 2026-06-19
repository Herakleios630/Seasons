---
title: "Arbeitsauftrag: growSnowInChunk implementieren"
quelle: "roadmap.md → Phase 1a, Sprint 1a.5"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: growSnowInChunk implementieren

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die reine Growth-Methode `growSnowInChunk(Chunk, ChunkCacheEntry)` implementieren. Sie läuft nur auf gesättigten Chunks und erhöht Plugin-Schnee-Layer um 1 – ohne Nachbar-Prüfung.

## Vorbedingungen
- 1a.4: `processChunk` ist umgebaut und platziert nur noch
- `ChunkCacheEntry` hat `snowBelowMax` und `pluginSnowHeight`

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | `growSnowInChunk()` implementieren |

## Erbetene Hilfe

1. **`growSnowInChunk(Chunk, ChunkCacheEntry)` implementieren:**
   - Nur Spalten mit `pluginSnowHeight[index] > 0 && snowBelowMax[index]` sammeln
   - Liste shufflen, `growth-layers-per-scan` (Config, default 2) abarbeiten
   - Pro Spalte:
     - `pluginSnowHeight[index]++`
     - Snow-Layer auf neue Gesamthöhe setzen (bis max 8)
     - Wenn `pluginSnowHeight + naturalSnowHeight >= getMaxSnowHeight(temp)` → `snowBelowMax[index] = false`
     - Wenn kein `snowBelowMax` mehr → `isFullyGrown` kann true werden

2. **Growth-Parameter aus Config:**
   - `growth-layers-per-scan` (default 2)

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** Growth-Layers aus Config
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (Growth-Algorithmus)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.5 abhaken)