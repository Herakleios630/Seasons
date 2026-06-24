---
title: "Arbeitsauftrag: Phase 2.6d2 – Refactoring: ChunkBiomeApplier extrahieren + BiomeSpoofAdapter löschen"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6d (Teil 2/2)"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6d2 – ChunkBiomeApplier extrahieren + BiomeSpoofAdapter löschen

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6d (Refactoring BiomeSpoofAdapter aufteilen – Teil 2/2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
**A) `ChunkBiomeApplier.java`** – Neue Klasse, die `captureAndApply()`, `revertChunk()`, `revertAll()` aus `BiomeSpoofAdapter` übernimmt.

**B) `BiomeSpoofCoordinator` final mit `ChunkBiomeApplier` verdrahten** (bisher als TODO-Stub).

**C) `BiomeSpoofAdapter.java` LÖSCHEN** – alle Referenzen im Projekt auf `BiomeSpoofCoordinator` + `SeasonBiomeResolver` + `ChunkBiomeApplier` umbiegen.

## Aktuelles Ergebnis
- `BiomeSpoofCoordinator.java` und `SeasonBiomeResolver.java` existieren aus 2.6d1 (parallel zu `BiomeSpoofAdapter`)
- `ChunkBiomeApplier.java` existiert noch NICHT
- `BiomeSpoofAdapter` wird noch von `BiomeSpoofListener`, `SeasonsPlugin` und anderen verwendet
- Coordinator hat TODO-Stub für `captureAndApply`-Aufruf

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/ChunkBiomeApplier.java` | 🆕 Neu: captureAndApply / revert |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofCoordinator.java` | 🔄 Verdrahten mit Applier (aus 2.6d1) |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofAdapter.java` | ❌ LÖSCHEN |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofListener.java` | 🔄 Referenzen auf Coordinator umbiegen |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | 🔄 init/shutdown auf neue Klassen umstellen |

## Erbetene Hilfe
1. **`ChunkBiomeApplier.java` NEU anlegen:**
   - Konstruktor: `ChunkBiomeApplier(BiomeBackupStore, Logger)`
   - `captureAndApply(Chunk, Biome targetBiome, String chunkKey)`:
     - Code aus `BiomeSpoofAdapter.captureAndApply()` exakt übernehmen
     - `targetBiome` wird vom Coordinator geliefert (via `SeasonBiomeResolver.resolveTargetBiome()` – dynamisches Per-Biome-Mapping, kein hartes Family-Mapping)
     - `spoofed`-Set und `lastApplied`-Map werden HIER verwaltet (nicht im Coordinator)
     - Logik: Original-Biome sammeln → `backupStore.saveFirstTouch()` → `world.setBiome()` für alle Sections → `world.refreshChunk()` → `world.unloadChunk()` + `world.getChunkAt()`
   - `revertChunk(Chunk chunk)`:
     - Code aus `BiomeSpoofAdapter.revertChunk()` exakt übernehmen
     - Backup laden → Biome zurücksetzen → `refreshChunk()` → Maps/Caches bereinigen
   - `revertAll()`:
     - Code aus `BiomeSpoofAdapter.revertAll()` exakt übernehmen
   - **Max ~180 Zeilen**
2. **`BiomeSpoofCoordinator.java` verdrahten:**
   - `ChunkBiomeApplier` im Konstruktor als Parameter akzeptieren (statt Dummy)
   - `run()`: Nach Target-Bestimmung durch Resolver → `applier.captureAndApply(chunk, targetBiome, chunkKey)` aufrufen
   - `unregister()`: `applier.revertAll()` aufrufen
   - Getter für `applier.getSpoofedSet()`, `applier.getLastAppliedMap()` usw. (für Listener)
3. **`BiomeSpoofListener.java` anpassen:**
   - Statt `BiomeSpoofAdapter` → `BiomeSpoofCoordinator` verwenden
   - Methoden-Aufrufe anpassen: `adapter.getSpoofedSet()` → `coordinator.getSpoofedSet()`
   - `adapter.revertChunk()` → `coordinator.getApplier().revertChunk()` (oder Coordinator-Forwarder-Methode)
4. **`SeasonsPlugin.java` anpassen:**
   - `BiomeSpoofAdapter`-Feld ersetzen durch `BiomeSpoofCoordinator`
   - `onEnable()`: `BiomeSpoofCoordinator` instanziieren (mit Resolver + Applier) → `register()`
   - `onDisable()`: `unregister()`
5. **`BiomeSpoofAdapter.java` LÖSCHEN**
6. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
7. **Deployment:** JAR kopieren, Server restart
8. **Test:** Plugin muss starten, `/season` muss funktionieren, Biome-Spoofing muss unverändert laufen
9. Sync: `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

## Technische Randbedingungen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Applier ≤ 180 Z., Coordinator ≤ 250 Z.
- **Terminal:** PowerShell-Syntax
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`