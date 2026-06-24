---
title: "Arbeitsauftrag: Phase 2.2 – BiomeSpoofAdapter Grundgerüst & Klassifizierung"
quelle: "roadmap.md → Phase 2, Sprints 2.3 + 2.4"
related-roadmap: "roadmap.md → Phase 2"
created: "2025-02-05"
status: done
completed: 2025-02-06
---

# Arbeitsauftrag: Phase 2.2 – BiomeSpoofAdapter Grundgerüst & Klassifizierung

**Quelle:** roadmap.md → Phase 2, Sprints 2.3 + 2.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Den `BiomeSpoofAdapter` als zentrale Koordinator-Klasse aufbauen: Grundgerüst mit Konstruktor, Config-Reload, 40-Tick-Timer, Player-Iteration und Budget-Loop. Anschließend die Chunk-Klassifizierung (LAND/OCEAN) und das Season→Biome-Mapping implementieren inklusive Exclude-Prüfung und Cold-Skip-Logik.

## Aktuelles Ergebnis
- `SpoofMode`, `BiomeFamily`, `BiomeBackupStore` existieren
- `ConfigManager` liefert alle Werte aus `biome_spoof.yml`
- `SeasonClock.getCurrentSeason()` ist verfügbar
- Der `BiomeSpoofAdapter` muss noch komplett neu geschrieben werden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofAdapter.java` | **Neu/Erweitern:** Grundgerüst + Klassifizierung (~200 Z. nach dieser Phase) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | **Edit:** BiomeSpoofAdapter instanziieren, register() aufrufen |

## Erbetene Hilfe

### Slice 1: Grundgerüst – Konstruktor, Config-Reload, Timer
1. `BiomeSpoofAdapter.java` im Package `de.ajsch.seasons.visual` erstellen:
   - Implementiert `Runnable` (für 40-Tick-Timer)
   - Konstruktor: `BiomeSpoofAdapter(SeasonsPlugin plugin, SeasonClock clock, ConfigManager config, BiomeBackupStore backupStore)`
   - Felder: `plugin`, `clock`, `config`, `backupStore`, `mode`, `taskId`, `seasonTarget` (EnumMap), `oceanTarget` (EnumMap), `excludedBiomes` (Set<Biome>), alle Maps aus dem Konzept (backups, spoofed, lastApplied, familyCache, coldChunks, nudgeQueue)
   - `reloadFromConfig()`: Liest alle Werte aus `ConfigManager`, befüllt `seasonTarget`, `oceanTarget`, `excludedBiomes`, `mode`, `radiusChunks`, `budgetPerTick`, `transitionDays`
2. `register()`: `reloadFromConfig()` aufrufen → prüfen ob `mode != OFF` → wenn aktiv: 40-Tick-Timer via `Bukkit.getScheduler().runTaskTimer()` starten, taskId speichern. Wenn OFF: loggen dass Spoofing deaktiviert ist.
3. `unregister()`: Timer canceln via `Bukkit.getScheduler().cancelTask(taskId)`, `revertAll()` aufrufen (vorerst leer), taskId = -1
4. Build: `.\gradlew.bat compileJava`

### Slice 2: run() – Player-Iteration & Budget-Loop
5. `run()`-Methode implementieren:
   ```
   - Wenn mode == OFF: return
   - Für jeden Online-Player:
     * Welt = player.getWorld(), nur NORMAL (Overworld) → sonst continue
     * disabled_fx_worlds prüfen (aus Config)
     * Season = clock.getCurrentSeason(world)
     * playerChunkX, playerChunkZ aus Location
     * Liste von Chunk-Offsets generieren (Radius = radiusChunks), sortiert nach Distanz
     * Für jeden Offset (solange budgetThisTick > 0):
       - cx = playerChunkX + dx, cz = playerChunkZ + dz
       - Chunk geladen? → sonst skip
       - Key = chunkKey(cx, cz)
       - (weitere Logik folgt in späteren Slices)
       - budgetThisTick--
   - flushNudges() (vorerst leer)
   ```
   - `chunkKey(cx, cz)`: Statische Methode, Form "cx_cz"
   - Offsets-Generator: `List<int[]> getOffsetsByDistance(int radius)` – alle (dx,dz) im Quadrat, sortiert nach euklidischer Distanz
6. Build: `.\gradlew.bat compileJava`

### Slice 3: Klassifizierung – Family & Cold-Logik
7. `classifyOriginalFamily(Chunk chunk, String chunkKey)` implementieren:
   - Falls `backupStore.hasBackup(chunkKey)`: Hole erstes Biome aus Backup-Array → sampleBiome
   - Sonst: `chunk.getChunkSnapshot()` oder direkt über `world.getBiome(cx*16, 64, cz*16)` → sampleBiome
   - Klassifiziere: `OCEAN` wenn Biome-Name "OCEAN" enthält (außer FROZEN_OCEAN in manchen Fällen) → sonst `LAND`
   - Ergebnis in `familyCache` speichern
8. `chooseTargetBiomeForChunk(String chunkKey, BiomeFamily family, Season season, Chunk chunk)` implementieren:
   - Falls `family == OCEAN` und Ocean-Spoofing enabled: `oceanTarget.get(season)` mit Deep-Variant-Handling (wenn `keep_deep_variants` und Original-Biome deep → Deep-Variante des Target-Oceans wählen)
   - Sonst: `seasonTarget.get(season)`
   - Falls Ergebnis null: Original-Biome zurückgeben (kein Spoof)
9. `isChunkExcludedByConfig(Chunk chunk)` implementieren:
   - Sample ein Biome aus dem Chunk (z.B. Mitte bei y=64)
   - Prüfen ob in `excludedBiomes` enthalten → true
10. `shouldSkipSpoofForChunk(Chunk chunk, Season season, String chunkKey)` implementieren:
    - Nur außerhalb WINTER relevant
    - Prüfe ob Chunk natürliche Kalt-Biome hat → `isColdBiome()` aufrufen
    - Wenn cold und season != WINTER → true (nicht überschreiben)
11. `isColdBiome(Biome biome)` implementieren:
    - String-basierte Prüfung: biome.name().contains("SNOWY") || contains("FROZEN") || contains("ICE") || contains("GROVE") || contains("MOUNTAIN") || contains("PEAK")
    - Explizit false für WARM_OCEAN, LUKEWARM_OCEAN
12. Build: `.\gradlew.bat compileJava shadowJar -x test`

## Done‑Definition Phase 2.2
- [x] `BiomeSpoofAdapter` kompiliert sauber
- [x] `register()` startet 40-Tick-Timer wenn mode != OFF
- [x] `run()` iteriert über Online-Player, berechnet Budget-Loop

- [x] Chunk-Klassifizierung (LAND/OCEAN) funktioniert

- [x] Season→Biome-Mapping wird aus Config geladen
- [ ] Excluded Biomes werden korrekt erkannt
- [ ] Keine NMS/Reflection

## Technische Randbedingungen
- **Keine Magic Numbers:** Alle Werte aus Config
- **Biome nie hardcoden:** `isColdBiome()` ist eine Hilfsmethode, kein vollständiges Biome-Mapping
- **Keine NMS/Reflection:** Nur Paper-API (`world.getBiome()`, `ChunkSnapshot`)
- **Java-Dateien ≤ 400 Zeilen:** `BiomeSpoofAdapter` aktuell ~200 Z., Ziel nach Phase 2.5 ~350 Z.
- **Grosse Java-Dateien:** Mit `filesystem_read_text_file` lesen
- **Terminal:** PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`