---
title: "Arbeitsauftrag 2.5.4: Integration mit BiomeSpoofAdapter + Aufräumen"
quelle: "roadmap.md → Phase 2.5, Sprint 2.5.4"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: done
---

# Arbeitsauftrag 2.5.4: Integration mit BiomeSpoofAdapter + Aufräumen

**Quelle:** roadmap.md → Phase 2.5, Sprint 2.5.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
Den `BiomeSpoofAdapter` von überflüssigem Code befreien (Nudge-System komplett entfernen, `flushResends()`/`chunksNeedingResend`-Logik vereinfachen), `biome_spoof.yml` um neue Config-Einträge erweitern, `ConfigManager` entsprechend aktualisieren.

## Aktuelles Ergebnis
- Nudge-System (`nudgeViewers`, `enqueueNudge`, `flushNudges`, Queues) ist noch im Code
- `flushResends()` verwendet `chunksNeedingResend` (ConcurrentHashMap)
- `biome_spoof.yml` hat noch keine `resend_*`-Felder

## Ziel
- Nudge-Code komplett entfernt aus `BiomeSpoofAdapter`
- Re-Send über `chunk.unload(true)` + `world.getChunkAt()` direkt in `captureAndApply()` integriert
- `biome_spoof.yml` hat neue Einträge
- `ConfigManager` liest neue Einträge

## ToDo-Liste
1. [x] `BiomeSpoofAdapter.java` – Nudge-System entfernen:
   - Felder löschen: `nudgeCooldownMs`, `nudgeMaxPerTick`, `nudgeQueues`, `nudgeLast`, `lastNudgeTime`
   - Methoden löschen: `nudgeViewers()`, `enqueueNudge()`, `flushNudges()`
   - `reloadFromConfig()`: Nudge-Config-Zeilen entfernen
   - `unregister()`: Nudge-Queue-Clear-Zeilen entfernen
   - `revertAll()`: Nudge-Queue-Clear-Zeilen entfernen
   - `runInternal()`: `flushNudges()`-Aufruf entfernen (nur `flushResends()` bleibt)
   - Alles was `getNudgeQueueSize()` referenziert anpassen/entfernen
2. [x] `BiomeSpoofAdapter.java` – Re-Send in `captureAndApply()` eingebaut (`unloadChunk` + `getChunkAt`):
   ```java
   // Nach refreshChunk():
   world.unloadChunk(chunkX, chunkZ);
   world.getChunkAt(chunkX, chunkZ);
   ```
   **ODER** alternativ Batch-Re-Send via `chunksNeedingResend` beibehalten (ist evtl. die bessere Wahl für Performance)
3. [x] `biome_spoof.yml` erweitern (resend_enabled, resend_chunks_per_tick bereits vorhanden):
   ```yaml
   resend_enabled: true
   resend_chunks_per_tick: 8
   ```
4. [x] `ConfigManager.java`: `isResendEnabled()` und `getResendChunksPerTick()` bereits vorhanden
5. [x] `BiomeSpoofAdapter.java`: Resend-Werte direkt aus ConfigManager gelesen (kein reload nötig)
6. [x] Heartbeat-Log: Kein separates Re-Send-Set nötig (Re-Send passiert direkt in captureAndApply)
7. [x] Build: `.\gradlew.bat compileJava`