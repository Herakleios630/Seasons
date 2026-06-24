---
title: "Arbeitsauftrag: Phase 2.3 – Capture & Apply, Revert, Nudge-System"
quelle: "roadmap.md → Phase 2, Sprints 2.5 + 2.6"
related-roadmap: "roadmap.md → Phase 2"
created: "2025-02-05"
status: done
---

# Arbeitsauftrag: Phase 2.3 – Capture & Apply, Revert, Nudge-System

**Quelle:** roadmap.md → Phase 2, Sprints 2.5 + 2.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Kernmechanik des Biome-Spoofings implementieren: `captureAndApply()` sichert Original-Biome und überschreibt sie mit dem Ziel-Biom, `revertChunk()` stellt den Originalzustand wieder her. Das Nudge-System sorgt für Client-seitige Chunk-Updates (`sendBlockChange` + Timed-Revert) damit der Client die neuen Biome-Farben auch ohne Chunk-Reload übernimmt.

## Aktuelles Ergebnis
- `BiomeSpoofAdapter` Grundgerüst steht: Timer, Player-Loop, Budget-Loop, Klassifizierung, Mappings
- `BiomeBackupStore` persistiert Backups
- `ConfigManager` liefert alle Werte
- Capture & Apply, Revert, Nudge fehlen komplett

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofAdapter.java` | **Edit:** captureAndApply, revertChunk, revertAll, Nudge-System hinzufügen (~150 Z. neu) |

## Erbetene Hilfe

### Slice 1: `captureAndApply()`
1. `captureAndApply(Chunk chunk, Biome targetBiome, String chunkKey)` implementieren:
   - Hole `World world = chunk.getWorld()`
   - Hole `int minY = world.getMinHeight()`, `int maxY = world.getMaxHeight()`
   - Berechne Anzahl Sections: `int sections = (maxY - minY) / 4` (nicht `world.getSections()` – Paper 1.21 hat andere API)
   - Oder einfach: Iteriere über `y = minY; y < maxY; y += 4`, für jeden 3D-Punkt `world.getBiome(cx*16, y, cz*16)` auslesen
   - **Alternative (besser):** `Biome[] originalBiomes = chunk.getBiomeSections()` (Paper-API) – prüfen ob verfügbar
   - Original-Biome in Array `Biome[]` sammeln (Reihenfolge dokumentieren: iteriere x=0..3, z=0..3, y=minY..maxY step 4)
   - `backupStore.saveFirstTouch(chunk, originalBiomes, chunkKey)` → Backup falls erster Touch
   - `chunk.setBiome(x, y, z, targetBiome)` für alle x=0..3, z=0..3, y über alle Sections
   - `world.refreshChunk(chunk.getX(), chunk.getZ())`
   - `spoofed.add(chunkKey)`, `lastApplied.put(chunkKey, targetBiome)`
   - **Budget:** Jeder `setBiome()`-Aufruf zählt als 1 – aber hier reicht 1 Budget-Punkt pro Chunk
   - Logik in `run()` integrieren: Nach Klassifizierung + Mapping prüfen ob `lastApplied != targetBiome`, dann `captureAndApply()` aufrufen
2. Build: `.\gradlew.bat compileJava`

### Slice 2: `revertChunk()` & `revertAll()`
3. `revertChunk(Chunk chunk)` implementieren:
   - Hole `String chunkKey`
   - Prüfe ob Backup existiert (`backupStore.hasBackup(chunkKey)`) – wenn nicht: entferne aus Maps, return
   - Hole `Biome[] originalBiomes = backupStore.getBackup(chunkKey)`
   - `World world = chunk.getWorld()`, `int minY = world.getMinHeight()`, `int maxY = world.getMaxHeight()`
   - Für jede Position (x,z,y) das ursprüngliche Biome zurücksetzen: `world.setBiome(x, y, z, originalBiomes[index++])`
   - `world.refreshChunk(chunk.getX(), chunk.getZ())`
   - Entferne aus `spoofed`, `lastApplied`, `familyCache`, `coldChunks`
   - `backupStore.removeBackup(chunkKey)`
4. `revertAll()` implementieren:
   - Über alle Einträge in `spoofed` iterieren
   - Falls Chunk in der Welt geladen: `world.getChunkAt()` → `revertChunk()`
   - Sonst: nur aus Maps entfernen (Chunk ist unloaded, Vanilla-Biome liegen vor)
   - `spoofed.clear()`, `lastApplied.clear()` (familyCache und coldChunks können bleiben)
5. Build: `.\gradlew.bat compileJava`

### Slice 3: Nudge-System
6. Nudge-Datenstrukturen vorbereiten:
   - `Map<UUID, ArrayDeque<long[]>> nudgeQueue` (Player-UUID → Queue von [chunkX, chunkZ])
   - `Map<UUID, Long> lastNudgeTime` (Spieler → letzter Nudge-Timestamp)
   - `int nudgeCooldownMs` (aus Config, default 3000)
   - `int nudgeMaxPerTick` (aus Config, default 8)
7. `nudgeViewers(World world, int chunkX, int chunkZ)` implementieren:
   - Für alle Spieler in `world.getPlayers()`, die den Chunk in Sichtweite haben:
     * `Location playerLoc = player.getLocation()`
     * `playerChunkX = playerLoc.getBlockX() >> 4`, `playerChunkZ = playerLoc.getBlockZ() >> 4`
     * Distanz prüfen: `abs(playerChunkX - chunkX) <= viewDistance && abs(playerChunkZ - chunkZ) <= viewDistance`
     * `enqueueNudge(player, world, chunkX, chunkZ)` aufrufen
8. `enqueueNudge(Player player, World world, int chunkX, int chunkZ)` implementieren:
   - Prüfe Cooldown: `lastNudgeTime.getOrDefault(uuid, 0) + nudgeCooldownMs < System.currentTimeMillis()`
   - Wenn Cooldown abgelaufen: `nudgeQueue.computeIfAbsent(uuid, k -> new ArrayDeque<>()).add(new long[]{chunkX, chunkZ})`
9. `flushNudges()` implementieren:
   - Pro Tick max `nudgeMaxPerTick` Nudges aus allen Queues abarbeiten
   - Für jeden Nudge:
     * `player.sendBlockChange(new Location(world, chunkX*16, world.getMinHeight(), chunkZ*16), Material.BARRIER.createBlockData())`
     * Dann 1 Tick später (via `Bukkit.getScheduler().runTaskLater()`): `player.sendBlockChange(sameLocation, world.getBlockData(sameLocation))` – stellt echten Block wieder her
   - `lastNudgeTime.put(player.getUniqueId(), System.currentTimeMillis())`
   - Nudge aus Queue entfernen
10. `flushNudges()` am Ende von `run()` aufrufen (wo bereits vorgesehen)
11. Build: `.\gradlew.bat compileJava shadowJar -x test`

## Done‑Definition Phase 2.3
- [x] `captureAndApply()` sichert Original-Biome, überschreibt und refresht Chunks
- [x] `revertChunk()` stellt Original-Biome wieder her
- [x] `revertAll()` reverts alle gespooften Chunks
- [x] Nudge-System queuet und sendet Block-Changes korrekt
- [x] 3s Cooldown pro Spieler, max 8 Nudges/Tick
- [x] Build grün: `.\gradlew.bat compileJava shadowJar -x test`
- [x] Keine NMS/Reflection

## Technische Randbedingungen
- **Paper-API:** `world.setBiome(x, y, z, biome)`, `world.refreshChunk(cx, cz)`, `player.sendBlockChange()` sind Paper-API
- **Keine NMS:** Auch für Nudge nur Paper-API nutzen
- **Java-Dateien ≤ 400 Zeilen:** BiomeSpoofAdapter aktuell ~350 Z., genau an der Grenze – ggf. Nudge in eigene Klasse auslagern wenn >400
- **Grosse Java-Dateien:** Mit `filesystem_read_text_file` lesen
- **Terminal:** PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`