---
title: "Arbeitsauftrag: ChunkCacheEntry + SnowAccumulator umbauen"
quelle: "roadmap.md → Phase 1a, Sprint 1a.1"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: ChunkCacheEntry + SnowAccumulator umbauen

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die neue Datenstruktur `ChunkCacheEntry` definieren, die pro Chunk die Spalten-Arrays `pluginSnowHeight` und `naturalSnowHeight` hält. `SnowAccumulator` auf eine `ConcurrentHashMap<String, ChunkCacheEntry>` als Cache umstellen.

## Vorbedingungen
- Phase 1 abgeschlossen (MVP)
- `SnowAccumulator.java` existiert (~420 Zeilen) – muss schrittweise umgebaut werden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | **NEU** – Datenklasse für Chunk-Meta + plugin/natural snow |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Cache-Deklaration hinzufügen, `processChunk` später umbauen |

## Erbetene Hilfe

1. **`ChunkCacheEntry` erstellen:**
   - Felder: `byte[] pluginSnowHeight` (256), `byte[] naturalSnowHeight` (256)
   - Meta: `int snowCapable`, `int snowCovered`, `int snowBelowMax`, `int tempLevelMin`, `int tempLevelMax`, `long lastUpdated`
   - Helper: `hasPluginSnow()` → `totalPluginSnow > 0`, `isSaturated()`, `isFullyGrown()`
   - Index: `idx = x * 16 + z`

2. **Cache in `SnowAccumulator` einführen:**
   - `ConcurrentHashMap<String, ChunkCacheEntry> chunkCache`
   - Key-Format: `worldUID:chunkKey` (vorbereitet, Methode zum Bauen)

3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** 256 als Konstante
- **Keine NMS/Reflection**
- **Java-Dateien ≤ 400 Zeilen:** `ChunkCacheEntry` bleibt unter 100 Zeilen
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (neue Datenstruktur dokumentieren)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1a.1 abhaken)