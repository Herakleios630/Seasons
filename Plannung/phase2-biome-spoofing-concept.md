# Phase 2: Visual Seasons – Biome-Spoofing-Konzept (ersetzt NMS-Ansatz)

> **Erkenntnis aus AeternumSeasons 4.1:** Biome-Farben können saisonal OHNE NMS geändert werden, indem das Biome des Chunks temporär mit `world.setBiome()` überschrieben und mit `world.refreshChunk()` neu geladen wird. Der Client berechnet Foliage/Grass-Colors automatisch aus dem neuen Biome – kein Resource Pack, kein Mod, kein NMS-Packet-Hook.

---

## 🎯 Ziel

Immersive saisonale Laub-/Gras-Farben rein plugin-basiert – jetzt **ohne NMS/Reflection**, stattdessen mit Paper-API `world.setBiome()` / `world.refreshChunk()`.

- Herbst: Birken orange, Eichen rot, Dark Forest gelb
- Frühling: Kirsch-Biome rosa, Blumenwald intensiver
- Winter: alles braun/grau (Schneelandschaft)
- Sommer: Vanilla-Farben

---

## 🆚 Vergleich: Alter NMS-Plan vs. Neues Biome-Spoofing

| Aspekt | Alter Plan (NMS, verworfen) | Neuer Plan (Biome-Spoofing) |
|---|---|---|
| **Methode** | Packet-Play-Out-Map-Chunk Override (NMS/Reflection) | `world.setBiome()` + `world.refreshChunk()` |
| **Paper-Versionen** | Pro Version eigener Adapter nötig | Paper-API – versionsunabhängig |
| **Foliage-Tint** | Manuell per Packet-Feld überschrieben, Farbwerte aus Config | Automatisch durch geändertes Biome (Client berechnet selbst) |
| **Biome-Mappings** | Color-RGB-Werte pro Season | Biome-Target pro Season (z.B. PLAINS → FLOWER_FOREST) |
| **Backup/Revert** | Nicht nötig (Packet transient) | Backup-Array mit Original-Biomen, `setBiome()` zum Revert |
| **Persistenz** | Nicht nötig | BiomeBackupStore (JSON auf Platte, Crash-sicher) |
| **Ozeane** | Nicht behandelt | Separate Ocean-Logik (Deep-Variant-Handling, Frozen Ocean im Winter) |
| **Excluded Biomes** | Nicht vorgesehen | Config-Liste `excluded_biomes` |
| **Transition** | Lerp über Tage | Pre-Transition-Faktor (letzte 3 Tage vor Wechsel) |
| **NMS-Abhängigkeit** | Ja, Phase-2-Voraussetzung | **Nein** – bleibt Phase-1-kompatibel |

---

## 🏗️ Architektur (neues Package `seasons.visual`)

```
visual/
├── BiomeSpoofAdapter.java       (→ Hauptklasse, Listener + Runnable, ~350 Z.)
├── BiomeBackupStore.java        (→ Persistenz der Original-Biome auf Platte, ~150 Z.)
├── BiomeSpoofListener.java      (→ ChunkLoad/Unload/SeasonChange-Events, ~100 Z.)
├── BiomeFamily.java             (→ Enum: LAND, OCEAN, SHORE)
├── SpoofMode.java               (→ Enum: OFF, GLOBAL_RING)
└── biome_spoof.yml              (→ Config-Datei)
```

### Klassen im Detail

| Klasse | Verantwortung |
|---|---|
| `BiomeSpoofAdapter` | Haupt-Koordinator. 40-Tick-Timer, iteriert über Online-Player, bestimmt Ziel-Biom pro Season+Family, Backup + `setBiome()` + `refreshChunk()`, Nudge-Queue für Player-Nudges. |
| `BiomeBackupStore` | Speichert Original-Biome pro Chunk als JSON (`plugins/Seasons/biome_backups.json`). Lädt bei Server-Start, speichert periodisch. Methode `saveFirstTouch(Chunk, Biome[], stepX, stepZ)`. |
| `BiomeSpoofListener` | `ChunkLoadEvent` → aus Caches entfernen. `ChunkUnloadEvent` → Revert + Cache-Cleanup. `SeasonChangeEvent` → Revert-Phase einleiten (5000ms Transition-Fenster). |
| `BiomeFamily` | Enum: `LAND`, `OCEAN` (später erweiterbar: `SHORE`, `RIVER`). Wird pro Chunk aus Backup oder Sampling klassifiziert. |
| `SpoofMode` | Enum: `OFF` (deaktiviert), `GLOBAL_RING` (um Spieler herum, Blickrichtungs-Priorisierung). |
| `biome_spoof.yml` | Vollständige Config (Season-Mappings, Ocean-Settings, Excluded-Biomes, Transition-Days, Budget, Radius) |

---

## 📊 Datenstrukturen (in `BiomeSpoofAdapter`)

```java
// Pro Season das Ziel-Biom für LAND-Chunks (aus Config lesbar)
EnumMap<Season, Biome> seasonTarget;

// Pro Season das Ziel-Biom für OCEAN-Chunks
EnumMap<Season, Biome> oceanTarget;

// Biomes die NIE gespooft werden (Nether, End, Mushroom, …)
Set<Biome> excludedBiomes;

// Original-Biome pro Chunk (Key = worldUUID + chunkX + chunkZ)
Map<Long, Biome[]> backups;

// Aktuell gespoofte Chunks (für Revert)
Set<Long> spoofed;

// Zuletzt angewandtes Biome (vermeidet doppeltes Setzen)
Map<Long, Biome> lastApplied;

// LAND/OCEAN-Klassifizierung pro Chunk
Map<Long, BiomeFamily> familyCache;

// Chunks mit natürlichen Kalt-Biomen (für shouldSkipSpoof außerhalb Winter)
Set<Long> coldChunks;

// Player-Nudge-Queue (sendBlockChange für Re-Render)
Map<UUID, ArrayDeque<Long>> nudgeQueue;
```

---

## 📝 Ablauf Biome-Spoofing (pro Tick)

```
1. Prüfe: Mode != OFF
2. Für jeden Online-Player:
   a. Welt = Overworld (NORMAL), sonst skip
   b. Prüfe disabled_fx_worlds
   c. Ermittle aktuelle Season + Pre-Transition-Faktor
   d. Ziel-Biom für LAND = seasonTarget.get(season)
   e. Ziel-Biom für OCEAN = oceanTarget.get(season)
   f. Berechne Radius = max(radiusChunksCfg, viewDistance + 1)
   g. Erzeuge Offsets-Liste für Radius, sortiert nach Distanz + Blickrichtung
   h. Für jeden Offset (solange Budget > 0):
      - cx = playerChunkX + offset.dx, cz = playerChunkZ + offset.dz
      - Prüfe Chunk geladen
      - hole Family aus Cache (oder sample)
      - hole Ziel-Biom (LAND/OCEAN via chooseTargetBiomeForChunk)
      - falls isChunkExcludedByConfig() → Revert falls spoofed, dann skip
      - falls shouldSkipSpoofForChunk() → Revert falls spoofed, dann skip
      - falls lastApplied == Ziel-Biom → skip (kein unnötiges Setzen)
      - captureAndApply(chunk, targetBiome) → Backup + setBiome() + refreshChunk()
      - spoofed.add(key), lastApplied.put(key, targetBiome)
      - nudgeViewers(world, cx, cz) → enqueued sendBlockChange(BARRIER)
      - budget--
3. flushNudges() – max 8 Nudges pro Tick, 3s Cooldown pro Spieler+Chunk
```

---

## 🗺️ Season → Biome-Mappings (Default)

```yaml
# biome_spoof.yml
biome_spoof:
  enabled: true
  mode: GLOBAL_RING
  radius_chunks: 8
  budget_chunks_per_tick: 16
  revert_on_non_winter: true
  transition_days: 3  # letzte X Tage vor Season-Wechsel für sanften Übergang

  seasons:
    SPRING: FLOWER_FOREST   # Blühendes Grün
    SUMMER: PLAINS          # Vanilla-Standard
    AUTUMN: WINDSWEPT_SAVANNA  # Herbstliche Brauntöne
    WINTER: SNOWY_PLAINS    # Weiße Winterlandschaft

  oceans:
    enabled: true
    affect_shores: true
    keep_deep_variants: true
    winter_force_snow: false
    winter_force_snow_biome: SNOWY_PLAINS
    seasons:
      SPRING: LUKEWARM_OCEAN
      SUMMER: WARM_OCEAN
      AUTUMN: OCEAN
      WINTER: FROZEN_OCEAN

  excluded_biomes:
    - MUSHROOM_FIELDS
    - DEEP_DARK
    - THE_VOID

  spawn_guard:
    enabled: true
    only_spoofed_chunks: true
    near_water_radius: 2

  disk_backup:
    enabled: true
```

---

## 🔁 Revert-Mechanismus

1. **Season-Wechsel:** `seasonTransitionUntil = now + 5000ms` → innerhalb dieser Zeit werden Chunks mit `revertChunk()` zurückgesetzt
2. **Chunk-Unload:** `revertChunk(chunk)` sofort
3. **Plugin-Disable:** `revertAll()` über alle geladenen Chunks
4. **Chunk-Load:** Falls ein Chunk neu geladen wird, entfernen wir ihn aus `spoofed` und `backups` (Vanilla-Biome liegt jetzt vor)

**revertChunk()-Ablauf:**
```
1. Hole Backup-Array für Chunk-Key
2. Iteriere durch X/Z (step=4), für jede Y-Ebene:
   world.setBiome(x, y, z, backup[i++])
3. world.refreshChunk(chunkX, chunkZ)
4. Entferne aus lastApplied, familyCache
5. Nudge Viewer
```

---

## 💾 BiomeBackupStore – Persistenz

```java
// JSON-Struktur: plugins/Seasons/biome_backups.json
{
  "world_uid": "abc-def-ghi",
  "backups": {
    "chunk_12_-8": ["PLAINS", "PLAINS", "PLAINS", "FOREST", ...],
    "chunk_13_-8": ["OCEAN", "OCEAN", ...]
  }
}
```

- **saveFirstTouch(Chunk, Biome[], stepX, stepZ):** Speichert Backup beim ersten Spoof
- **Laden bei Server-Start:** Stellt sicher, dass nach Crash/Restart die Original-Biome bekannt sind
- **Bereinigung:** Alte Backups (>7 Tage ohne Zugriff) werden entfernt

---

## 🌳 AutumnSoilPainter – Herbstlaub am Boden (spätere Erweiterung)

> **Für später zurückgestellt.** Wird als separates Feature nach Phase 2 umgesetzt.

**Idee (aus AeternumSeasons abgeleitet):**
- Während des Herbstes wird unter Bäumen (Spruce/Birch/Oak-Leaves) temporär Podzol oder Blatt-Blöcke auf Gras/Dirt gesetzt
- Spieler-zentrierter Scan mit konfigurierbarem Radius
- Nur oberirdisch, nur in `can_freeze`-Biomen
- Rückbau nach Saison-Ende

**Config-Erweiterung (später):**
```yaml
autumn_soil:
  enabled: true
  radius_chunks: 4
  attempts_per_tick: 4
  leaf_chance_per_block: 0.15
  painted_material: PODZOL  # oder OAK_LEAVES, BROWN_MUSHROOM_BLOCK
```

---

## 📋 Sprint-Übersicht (ersetzt alte Phase 2)

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.1 | Config & Datenmodell | `biome_spoof.yml`, `SpoofMode.java`, `BiomeFamily.java` | [ ] |
| 2.2 | BiomeBackupStore | `BiomeBackupStore.java` (Persistenz + Load/Restore) | [ ] |
| 2.3 | BiomeSpoofAdapter – Grundgerüst | `BiomeSpoofAdapter.java` (init, reload, Timer, Player-Loop) | [ ] |
| 2.4 | BiomeSpoofAdapter – Klassifizierung & Mappings | Family-Klassifizierung, Season→Biome-Mapping, isChunkExcluded, shouldSkipSpoof | [ ] |
| 2.5 | BiomeSpoofAdapter – Capture & Apply | `captureAndApply()`, `revertChunk()`, `revertAll()`, `refreshChunk()` | [ ] |
| 2.6 | BiomeSpoofAdapter – Nudge-System | `nudgeViewers()`, `enqueueNudge()`, `flushNudges()` | [ ] |
| 2.7 | BiomeSpoofListener – Events | ChunkLoad/Unload/SeasonChange-Listener | [ ] |
| 2.8 | Integration & Test | `SeasonsPlugin.java` (init/shutdown), Build, Deploy, Funktionstest | [ ] |

### Sprint-Details

#### 2.1 – Config & Datenmodell
- `biome_spoof.yml` wie oben spezifiziert anlegen
- `SpoofMode.java`: `OFF`, `GLOBAL_RING` (nur globale Ring-Methode für MVP)
- `BiomeFamily.java`: `LAND`, `OCEAN`
- `ConfigManager.java` erweitern: `biome_spoof.yml` laden

#### 2.2 – BiomeBackupStore
- JSON-Serialisierung/Deserialisierung mit Gson (bereits in Abhängigkeiten)
- Methoden: `saveFirstTouch(Chunk, Biome[])`, `loadAll()`, `saveAll()`, `purgeOld()`
- World-UID als Top-Level-Key, Chunk-Keys als `"cx_cz"`

#### 2.3 – BiomeSpoofAdapter Grundgerüst
- Konstruktor: `BiomeSpoofAdapter(SeasonsPlugin, SeasonClock)`
- `reloadFromConfig()`: Alle Config-Werte einlesen, Mappings aus `biome_spoof.yml`
- `register()`: Listener registrieren + 40-Tick-Timer starten
- `unregister()`: revertAll() + Timer cancel + Listener unregister
- `run()`: Grundgerüst mit Player-Iteration, Budget-Loop (noch ohne echte Logik)

#### 2.4 – Klassifizierung & Mappings
- `classifyOriginalFamily(Chunk)`: Aus Backup oder Sampling bestimmen ob LAND/OCEAN
- `chooseTargetBiomeForChunk(key, family, …)`: Season + Family → Target-Biome
- `isChunkExcludedByConfig(Chunk)`: Prüft excludedBiomes
- `shouldSkipSpoofForChunk(Chunk, Season)`: Natürlich kalte Chunks außerhalb Winter skippen
- `isColdBiome(Biome)`: String-basierte Prüfung auf “SNOWY”, “FROZEN”, “ICE”, “GROVE”, “PEAK”, “MOUNTAIN”

#### 2.5 – Capture & Apply
- `captureAndApply(Chunk, Biome)`: Original-Biome in Liste sammeln → Backup → setBiome() für alle Sections → refreshChunk()
- `revertChunk(Chunk)`: Backup-Array durchgehen → setBiome() original → refreshChunk()
- `revertAll()`: Alle geladenen Chunks revertieren

#### 2.6 – Nudge-System
- `nudgeViewers(World, chunkX, chunkZ)`: Für alle Spieler im View-Distance-Radius Nudge enqueuen
- `enqueueNudge(Player, World, chunkX, chunkZ)`: Nudge pro Spieler in Queue, 3s Cooldown
- `flushNudges()`: Max 8 Nudges pro Tick abarbeiten – per `sendBlockChange(BARRIER)` an unterster Y-Koordinate und Scheduler-Task die den echten Block 1 Tick später zurückschickt

#### 2.7 – BiomeSpoofListener
- `onChunkLoad(ChunkLoadEvent)`: Entferne Chunk aus spoofed/backups/lastApplied/familyCache
- `onChunkUnload(ChunkUnloadEvent)`: Falls gespooft → revertChunk(), entferne aus allen Maps
- `onSeasonChange(SeasonUpdateEvent)`: Setze `seasonTransitionUntil = now + 5000`. Falls `revertOnSeasonChange` aktiv → alle gespooften Chunks revertieren. Transition-Fenster für Pre-Transition-Faktor.

#### 2.8 – Integration & Test
- `SeasonsPlugin.java`: BiomeSpoofAdapter init + BiomeBackupStore laden im `onEnable()`, unregister im `onDisable()`
- `PlayerJoinListener.java` anpassen (keine direkte NMS-Interaktion mehr)
- Build, Deploy auf Server
- Test-Matrix: Alle 4 Seasons durchschalten mit `/season skip`
- Prüfen: Biome-Wechsel sichtbar? Chunk-Refresh? Keine Doppel-Reverts?
- Performance: Tick-Auslastung mit 5-10 Spielern messen

---

## ✅ Done‑Definition Phase 2 (NEU)

- [ ] `BiomeSpoofAdapter` arbeitet mit 40-Tick-Timer, kein NMS
- [ ] `biome_spoof.yml` wird korrekt geladen und genutzt
- [ ] Im Herbst: Plains → Windswept Savanna, Wälder → herbstliche Brauntöne
- [ ] Im Frühling: Plains → Flower Forest (blühendes Grün)
- [ ] Im Winter: Plains → Snowy Plains (weiß), Ozean → Frozen Ocean
- [ ] Im Sommer: Plains → Plains (Vanilla)
- [ ] Ozeane werden korrekt behandelt (Deep-Varianten erhalten, Frozen Ocean im Winter)
- [ ] Excluded Biomes (Mushroom, Deep Dark, The Void) bleiben unverändert
- [ ] Chunk-Unload/Server-Stop revertiert alle gespooften Chunks sauber
- [ ] `biome_backups.json` persistiert Original-Biome Crash-sicher
- [ ] Performance: <5% Tick-Auslastung durch Spoofing
- [ ] Keine Konflikte mit Snow-System aus Phase 1.5
- [ ] Keine NMS/Reflection-Nutzung in Phase 2

---

## 🔗 Abhängigkeiten

- **Phase 1.5** muss abgeschlossen sein (stabiles Snow-System)
- `SeasonClock.getCurrentSeason()` wird von BiomeSpoofAdapter genutzt
- `TemperatureCalculator` wird NICHT für Biome-Spoofing benötigt (Season-basiert, nicht Temperatur-basiert)

---

## 🗄️ AutumnSoilPainter – Zurückgestellt

> Das Herbstlaub-Feature (`AutumnSoilPainter`) wird als **eigenständiges Konzept** in einer späteren Phase (z.B. Phase 2.5 oder Phase 4) umgesetzt. Es ist unabhängig vom Biome-Spoofing und kann parallel existieren.

**Geplante Dateien (später):**
- `visual/AutumnSoilPainter.java`
- Config-Erweiterung in `biome_spoof.yml` oder eigene `autumn_soil.yml`

---

*Konzept erstellt am 2025-01-XX, basierend auf Analyse von AeternumSeasons 4.1 BiomeSpoofAdapter.*