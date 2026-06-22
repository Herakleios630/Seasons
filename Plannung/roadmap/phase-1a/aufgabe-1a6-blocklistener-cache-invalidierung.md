---
title: "Arbeitsauftrag: BlockListener + SeasonChangeEvent → Cache-Invalidierung"
quelle: "roadmap.md → Phase 1a, Sprint 1a.6"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: done
---

# Arbeitsauftrag: BlockListener + SeasonChangeEvent → Cache-Invalidierung

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Einen `BlockEventListener` (oder Erweiterung des existierenden `SnowListener`) erstellen, der bei BlockBreak/BlockPlace den Chunk-Cache invalidiert. Zusätzlich muss `SeasonChangeListener` den gesamten Cache leeren (Season-Wechsel → Temperatur-Sprung).

## Vorbedingungen
- `ChunkCacheEntry` und Cache existieren
- `SeasonChangeListener.java` existiert (liegt unter `listener/`)

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/listener/BlockEventListener.java` | **NEU** – Listener für BlockBreak/BlockPlace |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | Cache-Clear bei SeasonChange |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | BlockEventListener registrieren |

## Ergebnis

Alle vier Schritte erfolgreich umgesetzt.

## Erledigte Schritte

1. **`BlockEventListener` erstellen:**
   - `@EventHandler onBlockBreak(BlockBreakEvent)` → Cache für den Chunk löschen
   - `@EventHandler onBlockPlace(BlockPlaceEvent)` → Cache für den Chunk löschen
   - `@EventHandler onChunkUnload(ChunkUnloadEvent)` → Cache löschen, Eintrag für JSON-Persistenz markieren (später)
   - Zugriff auf `SnowAccumulator.getCache()` zum Invalidieren

2. **`SeasonChangeListener` erweitern:**
   - `onSeasonChange(SeasonChangeEvent)` → `snowAccumulator.getCache().clear()`

3. **Registrierung in `SeasonsPlugin`:**
   - `BlockEventListener` instanziieren und beim PluginManager registrieren

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar` ✅

## Technische Randbedingungen
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax ✅

## Sync nach Abschluss
- `docs/developer-guide.md` (Cache-Invalidierung) ✅
- `docs/handover.md` ✅
- `Plannung/roadmap.md` (1a.6 abhaken) ✅