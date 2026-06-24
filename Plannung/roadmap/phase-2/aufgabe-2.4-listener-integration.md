---
title: "Arbeitsauftrag: Phase 2.4 – BiomeSpoofListener & Integration"
quelle: "roadmap.md → Phase 2, Sprints 2.7 + 2.8"
related-roadmap: "roadmap.md → Phase 2"
created: "2025-02-05"
status: in-progress
---

# Arbeitsauftrag: Phase 2.4 – BiomeSpoofListener & Integration

**Quelle:** roadmap.md → Phase 2, Sprints 2.7 + 2.8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Den `BiomeSpoofListener` für ChunkLoad, ChunkUnload und SeasonChange implementieren. Anschließend alles in `SeasonsPlugin` integrieren und auf dem Server testen. Dies ist die letzte Phase-2-Anweisung – nach Abschluss ist das Biome-Spoofing vollständig.

## Aktuelles Ergebnis
- `BiomeSpoofAdapter` ist vollständig (Grundgerüst, Klassifizierung, Capture/Apply, Revert, Nudge)
- `BiomeBackupStore` persistiert Backups
- `ConfigManager` liefert alle Werte aus `biome_spoof.yml`
- Listener und Plugin-Integration fehlen noch

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofListener.java` | **Neu:** ChunkLoad/Unload/SeasonChange-Events |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | **Edit:** BiomeSpoofAdapter init + BiomeBackupStore laden |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | **Edit:** Ggf. anpassen für Season-Update-Event |

## Erbetene Hilfe

### Slice 1: `BiomeSpoofListener`
1. `BiomeSpoofListener.java` im Package `de.ajsch.seasons.visual` erstellen:
   - Implementiert `Listener`
   - Konstruktor: `BiomeSpoofListener(BiomeSpoofAdapter adapter, BiomeBackupStore backupStore)`
   - Felder: `adapter`, `backupStore`
2. `@EventHandler onChunkLoad(ChunkLoadEvent event)`:
   - Hole `chunkKey = BiomeSpoofAdapter.chunkKey(event.getChunk().getX(), event.getChunk().getZ())`
   - Entferne Chunk aus `adapter.spoofed`, `adapter.lastApplied`, `adapter.familyCache`
   - Entferne Backup aus `backupStore.removeBackup(chunkKey)` (weil Chunk jetzt Vanilla-Biome hat)
   - Kein Revert nötig – frisch geladene Chunks haben originale Biome
3. `@EventHandler onChunkUnload(ChunkUnloadEvent event)`:
   - Hole `chunkKey = BiomeSpoofAdapter.chunkKey(event.getChunk().getX(), event.getChunk().getZ())`
   - Prüfe: `adapter.spoofed.contains(chunkKey)`
   - Falls ja: `adapter.revertChunk(event.getChunk())` aufrufen
   - Entferne aus allen Maps (macht `revertChunk()` bereits)
4. `@EventHandler onSeasonChange(SeasonUpdateEvent event)`:
   - Setze `adapter.seasonTransitionUntil = System.currentTimeMillis() + 5000` (5-Sekunden-Transition-Fenster)
   - Wenn Config `revert_on_non_winter` aktiv ist:
     * Alle aktuell gespooften Chunks revertieren via `adapter.revertAll()`
   - Logge: "[Seasons] Season changed to X, reverting all spoofed chunks..."
   - Dann im nächsten Tick wird `run()` automatisch neue Ziel-Biome setzen (weil Season jetzt anders)
5. Build: `.\gradlew.bat compileJava`

### Slice 2: Plugin-Integration
6. `SeasonsPlugin.java` anpassen (in `onEnable()`):
   - `BiomeBackupStore backupStore = new BiomeBackupStore(dataFolder.toPath())`
   - `backupStore.loadAll(world)` – world ist die Haupt-Overworld (nur eine Welt für Phase 2)
   - `BiomeSpoofAdapter biomeSpoof = new BiomeSpoofAdapter(this, seasonClock, configManager, backupStore)`
   - `biomeSpoof.register()` → startet Timer falls mode != OFF
   - `BiomeSpoofListener listener = new BiomeSpoofListener(biomeSpoof, backupStore)`
   - `getServer().getPluginManager().registerEvents(listener, this)`
   - Felder speichern für `onDisable()`
7. `SeasonsPlugin.java` anpassen (in `onDisable()`):
   - `biomeSpoof.unregister()` → revertAll() + Timer cancel
   - `backupStore.saveAll(world)` → Backup auf Platte schreiben
8. Prüfen: `SeasonChangeListener` importiert noch alte Visual-Referenzen? Falls ja entfernen.
9. Build: `.\gradlew.bat compileJava` – muss grün sein

### Slice 3: Build, Deploy, Funktionstest
10. Build: `.\gradlew.bat shadowJar -x test`
11. Deployment:
    - `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
    - Neue Config-Datei kopieren: `scp src\main\resources\biome_spoof.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/biome_spoof.yml"`
    - `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
12. Server starten, Logs prüfen:
    - Keine Exceptions beim Plugin-Start
    - Log-Eintrag: "[Seasons] BiomeSpoofAdapter registered with mode GLOBAL_RING" oder "disabled"
13. `/season skip` mehrfach ausführen, jede Season testen:
    - Frühling: Gras-/Laubfarben heller/grüner
    - Sommer: Vanilla-Farben
    - Herbst: Brauntöne, Windswept Savanna
    - Winter: Weiße Landschaft (Snowy Plains)
    - Ozeane: Frozen Ocean im Winter
14. Prüfen ob Chunks nach Unload/Restart korrekt revertiert werden
15. Performance: `/tps` vor und während Biome-Spoofing checken

### Slice 4: Dokumentation & Sync
16. `Plannung/roadmap.md`: Phase 2 Sprints 2.1–2.8 als abgeschlossen markieren
17. `docs/developer-guide.md`: Neues `visual/`-Package dokumentieren (Schichten-Impact)
18. `docs/handover.md`: Status updaten, nächste Phase (2b Frost) anmerken
19. `README.md`: Falls neue Commands/Features sichtbar → ergänzen

## Done‑Definition Phase 2.4
- [ ] `BiomeSpoofListener` reagiert auf ChunkLoad, ChunkUnload, SeasonChange
- [ ] `SeasonChangeEvent` triggert Revert aller gespooften Chunks
- [ ] `SeasonsPlugin` orchestriert BiomeSpoofAdapter + BiomeBackupStore korrekt
- [ ] Build erfolgreich: `.\gradlew.bat shadowJar -x test`
- [ ] Server startet sauber mit Biome-Spoofing
- [ ] Biome-Farben ändern sich sichtbar pro Season
- [ ] Ozeane werden korrekt behandelt
- [ ] Excluded Biomes bleiben unverändert
- [ ] Chunk-Unload/Server-Stop revertiert sauber
- [ ] `biome_backups.json` wird geschrieben
- [ ] Performance: <5% Tick-Auslastung
- [ ] Keine NMS/Reflection

## Done‑Definition Phase 2 (Gesamt)
- [ ] Im Herbst: Plains → Windswept Savanna, Wälder → herbstliche Brauntöne
- [ ] Im Frühling: Plains → Flower Forest (blühendes Grün)
- [ ] Im Winter: Plains → Snowy Plains (weiß), Ozean → Frozen Ocean
- [ ] Im Sommer: Plains → Plains (Vanilla)
- [ ] Keine Konflikte mit Snow-System aus Phase 1.5
- [ ] Alle Doku-Dateien synchronisiert

## Technische Randbedingungen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** BiomeSpoofListener ~100 Z.
- **Terminal:** PowerShell-Syntax
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deployment: Nur Befehle posten, nicht selbst ausführen**