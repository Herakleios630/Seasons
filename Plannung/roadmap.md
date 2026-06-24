# Seasons Plugin – Roadmap

> Vanilla-Plus-Jahreszeiten für Paper-Minecraft. Alle Werte config‑gesteuert, keine Magic Numbers.

---

## Phase 1: MVP – Jahreszeiten-Kreislauf & Winter-Wetter ❄️
**Ziel:** Server startet im Frühling, das Jahr läuft 365 Tage, im Winter schneit es in allen passenden Biomen, Schnee‑Platten akkumulieren temperaturabhängig.

### Architektur-Entscheidungen für Phase 1
- [x] Season rein deterministisch aus `world.getFullTime()` + Year‑Start‑Offset
- [x] Weather Interception: Hybrid-Lösung (`setPlayerWeather` + Reset bei Biom-Wechsel), später pro‑Chunk ausbaubar
- [x] Schnee-Schmelze: Random‑Tick in geladenen Chunks
- [x] Schnee-Akkumulation: 600 ticks Scan-Intervall, 4 Chunks pro Tick, max 2 Layer Vanilla, +1 pro -0.2°C unter Freeze
- [x] Kein Eis-Effekt in Phase 1 (→ Phase 3)
- [x] Schnee nur an der Oberfläche (wie Vanilla)
- [x] Global eine Season für alle Overworld-Welten
- [x] Unbekannte Biome = CAN_FREEZE
- [x] `weather.enabled` Config-Flag in Phase 1
- [x] Test-Mode mit 20-Tage-Jahr (`debug-mode`)

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 1.1 | Gradle & Bootstrap | `build.gradle.kts`, `settings.gradle.kts`, `plugin.yml`, `SeasonsPlugin.java` | [x] |
| 1.2 | Config & Persistenz | `ConfigManager.java`, `ResourceCopier.java`, `SeasonsDataStore.java`, `config.yml` | [x] |
| 1.3 | Season & Clock | `Season.java`, `SeasonClock.java`, `SeasonConfig.java` | [x] |
| 1.4 | Temperatur | `TemperatureCalculator.java`, `TemperatureConfig.java`, `BiomeTemperature.java`, `precipitation_categories.yml` | [x] |
| 1.5 | Weather Interception | `PrecipitationCategory.java`, `WeatherConfig.java`, `WeatherInterceptor.java` | [x] |
| 1.6 | Schnee-Akkumulation | `SnowAccumulator.java`, `SnowListener.java` | [x] |
| 1.7 | Events & Commands | `SeasonChangeEvent.java`, `SeasonCommand.java`, `SeasonAdminCommand.java` | [x] |
| 1.8 | Integrationstest | `PlayerJoinListener.java`, voller Durchlauf, Bugfixing | [x] |

**Done‑Definition Phase 1:**
- [x] Server startet sauber, lädt `config.yml`, `precipitation_categories.yml` & `seasons_data.yml`
- [x] Jahr taktet durch (365 Tage), `/season` zeigt aktuellen Tag + Season + verbleibende Tage
- [x] Im Winter fällt in CAN_FREEZE‑Biomen sichtbar Schnee statt Regen
- [x] In Wüste, Badlands, Savanna, Jungle bleibt Regen / kein Schnee
- [x] Schnee-Layer stapeln temperaturabhängig (kälter = mehr)
- [ ] Bei Season‑Wechsel geht Schneefall‑Eignung sauber zurück; Schnee schmilzt per Random‑Tick
- [x] Persistenz über Server‑Restarts erhalten
- [x] Keine NMS/Reflection-Nutzung
- [x] Commands `/season`, `/season debug`, `/season skip`, `/season set`, `/season speed` funktionieren

---

## Phase 1.5: Snow System 2.0 – Refactoring ❄️
> **Konzept:** `Plannung/snow-system-2.0-concept.md`
> **Ziel:** `SnowAccumulator` (aktuell ~498 Z., 5 Verantwortlichkeiten) in modulare Klassen aufteilen.
> Cache-Semantik korrigieren, 7 Kernfehler beheben, Temperatur-basierte Aktivierung für Growth/Melt.
> **Ersetzt:** Phase 1a (`snow-growth-concept.md`) und Phase 1b (`snow-melting-concept.md`).

### Architektur nach Refactoring

```
weather/
├── WeatherInterceptor.java      (unverändert)
├── WeatherConfig.java           (erweitert: saturationThreshold lesen)
├── PrecipitationCategory.java   (unverändert)
│
├── SnowAccumulator.java         (→ Orchestrator, ~80 Z.)
├── ChunkCacheManager.java       (→ Extrahiert: Cache-Logik, ~150 Z.)
├── SnowPlacer.java              (→ Extrahiert: Platzierung, ~120 Z.)
├── SnowGrower.java              (→ Extrahiert: Wachstum, ~100 Z.)
├── SnowMelter.java              (→ Neu: Schmelzen via Cache, ~100 Z.)
│
├── ChunkCacheEntry.java         (→ Fix: isSaturated mit Config-Threshold)
├── ChunkCacheEntrySerializer.java (→ Hilfsklasse: toJson/fromJson aus ChunkCacheStore ausgelagert)
└── TickDiagnostics.java         (unverändert)
```

### Behobene Fehler

| # | Fehler | Lösung |
|---|--------|--------|
| 1 | Cache-Drift: `pluginSnowHeight = oldPlugin` | `= physicalSnow` in `scanChunkColumns` |
| 2 | Schmelzen umgeht Cache | Schmelze über `SnowMelter` → Cache-Update + markDirty |
| 3 | Season-basierter Trigger | Temperatur-basiert: `temp < freezeThreshold` / `temp >= meltThreshold` |
| 4 | Toter Config-Wert `saturationThreshold` | Wird jetzt in `isSaturated(threshold)` genutzt |
| 5 | Fehlende Persistenz nach Scan | `markDirty` auch nach `scanChunkColumns` mit Plugin-Schnee |
| 6 | Schmelzen zerstört Vanilla-Schnee | Nur `pluginSnowHeight > 0` schmelzen |
| 7 | Cache-Invalidation fehlt bei Season-Wechsel | Wird durch `ChunkCacheManager.invalidate()` korrekt behandelt |

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 1.5.1 | ChunkCacheManager extrahieren | `ChunkCacheManager.java` (neu), `SnowAccumulator.java` (auslagern) | [x] |
| 1.5.2 | saturationThreshold-Fix | `ChunkCacheEntry.java`, `WeatherConfig.java`, `config.yml` | [x] |
| 1.5.3 | SnowPlacer extrahieren | `SnowPlacer.java` (neu), `SnowAccumulator.java` (auslagern) | [ ] |
| 1.5.4 | SnowGrower extrahieren | `SnowGrower.java` (neu), `SnowAccumulator.java` (auslagern) | [ ] |
| 1.5.5 | SnowMelter implementieren | `SnowMelter.java` (neu) | [ ] |
| 1.5.6 | SnowAccumulator verschlanken | `SnowAccumulator.java` (→ reiner Orchestrator, ~80 Z.) | [ ] |
| 1.5.7 | Integration & Test | Listener anpassen, Build, Deploy, Funktionstest | [ ] |

### Sprint-Details

#### 1.5.1 – ChunkCacheManager extrahieren
- `getOrComputeCache`, `scanChunkColumns`, TTL-Prüfung, Temperatur-Toleranz aus `SnowAccumulator` auslagern
- `ConcurrentHashMap<String, ChunkCacheEntry>` in `ChunkCacheManager` verschieben
- `invalidate(key)`, `clearCache()`, Cache-Statistiken (hits/misses)
- Fix #1 (Cache-Drift) und Fix #5 (markDirty nach Scan) gleich mit umsetzen

#### 1.5.2 – saturationThreshold-Fix
- `WeatherConfig.getSaturationThreshold()` liest `weather.snow.growth.saturation-threshold` (Default 0.95)
- `ChunkCacheEntry.isSaturated(threshold)` nutzt den Config-Wert statt hart 100%
- Ggf. `config.yml` prüfen ob der Wert bereits existiert (aus 1a.8)

#### 1.5.3 – SnowPlacer extrahieren
- `processChunk(Chunk, ChunkCacheEntry)` und `tryPlaceColumn` auslagern
- Pflanzen-Tracking (`removedPlants`-Liste, Wiederherstellung vorbereiten)
- Arbeitet nur auf Spalten mit `pluginSnowHeight==0 && naturalSnowHeight==0`

#### 1.5.4 – SnowGrower extrahieren
- `growSnowInChunk(Chunk, ChunkCacheEntry)` auslagern
- Sättigungs-Prüfung via `isSaturated(threshold)` aus 1.5.2
- Adoption von Naturschnee beim ersten Wachstum

#### 1.5.5 – SnowMelter implementieren
- **Neu geschrieben** (ersetzt alte `processMeltChunk`-Logik)
- Click-by-Click-Schmelze über Cache: `pluginSnowHeight[idx]--`, dann physisch synchronisieren
- Nur Spalten mit `pluginSnowHeight > 0` (Fix #6)
- Laub-Wiederherstellung wenn `pluginSnowHeight` auf 0 fällt
- `markDirty` bei `melted > 0` (Fix #2)

#### 1.5.6 – SnowAccumulator verschlanken
- Tick-Loop, Welt-Iteration, Temperatur-basierte Modus-Wahl (Growth vs. Melt)
- Delegiert an `ChunkCacheManager`, `SnowPlacer`, `SnowGrower`, `SnowMelter`
- Summary-Log alle `summaryIntervalScans`
- Ziel: ≤ 100 Zeilen

#### 1.5.7 – Integration & Test
- `BlockEventListener` an `ChunkCacheManager.invalidate()` binden
- `SeasonChangeListener` für Cache-Invalidation bei Season-Wechsel
- `SeasonsPlugin`-Tick an neue Orchestrator-API anpassen
- Build, Deploy, Funktionstest auf Server

### Done‑Definition Phase 1.5
- [ ] `SnowAccumulator.java` ≤ 100 Zeilen (nur Orchestrierung)
- [ ] Keine Klasse > 250 Zeilen
- [ ] Alle 7 Kernfehler behoben
- [ ] Temperatur-basierte Modus-Wahl (statt hartem Season-Check)
- [ ] `saturationThreshold: 0.95` wird wirksam (Wachstum startet bei 95% Sättigung)
- [ ] `chunk_cache.json` wird regelmäßig geschrieben mit korrekten Daten
- [ ] Schmelzen nur für Plugin-Schnee, Vanilla-Schnee bleibt
- [ ] Kein Cache-Drift mehr: pluginSnowHeight und physischer Schnee synchron
- [ ] Build erfolgreich, Server-Test bestanden
- [ ] Keine NMS/Reflection

---

## Phase 2-PRE: Rückbau altes Visual-Seasons-Konzept 🧹
> **Konzept:** `Plannung/phase2-biome-spoofing-concept.md`
> **Ziel:** Alle Artefakte des alten NMS-basierten Phase-2-Ansatzes entfernen, Code bereinigen, Projekt auf den neuen Biome-Spoofing-Ansatz vorbereiten.
> **Voraussetzung:** Phase 1.5 abgeschlossen.

### Zu bereinigende Artefakte

| Artefakt | Aktion |
|---|---|
| `visual/FoliageTintManager.java` | Löschen (NMS-basiert) |
| `visual/VisualSeasonManager.java` | Löschen (NMS-basiert) |
| `visual/SeasonalColorCalculator.java` | Löschen (NMS-basiert) |
| `visual/VisualConfig.java` | Löschen (NMS-basiert) |
| `nms/` Package | Komplett löschen |
| `visual.yml` | Löschen (wird durch `biome_spoof.yml` ersetzt) |
| `SeasonsPlugin.java` | Alte Visual-Registrierungen entfernen |
| `PlayerJoinListener.java` | Alte Visual-Referenzen entfernen |
| `SeasonChangeListener.java` | Alte Visual-Referenzen entfernen |
| `build.gradle.kts` | NMS-Abhängigkeiten prüfen/entfernen |
| `docs/visual-seasons-concept.md` | Als "verworfen" markieren, auf neues Konzept verweisen |

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2-PRE.1 | visuelles Package & NMS-Code löschen | `visual/`, `nms/` (löschen) | [x] |
| 2-PRE.2 | Plugin-Registrierungen bereinigen | `SeasonsPlugin.java`, `PlayerJoinListener.java`, `SeasonChangeListener.java` | [x] |
| 2-PRE.3 | Build & Config bereinigen | `build.gradle.kts`, `visual.yml` (löschen), `docs/visual-seasons-concept.md` | [x] |
| 2-PRE.4 | Build, Deploy, Smoke-Test | `build\libs\Seasons-0.1.0-SNAPSHOT.jar` | [x] |

**Done‑Definition Phase 2-PRE:**
- [x] Keine NMS/Reflection-Importe mehr im Projekt
- [x] Keine `visual/` oder `nms/` Packages mehr
- [x] `SeasonsPlugin` startet sauber ohne visuelle Komponenten
- [x] Phase 1.5 funktioniert unverändert weiter
- [x] Build erfolgreich

---

## ⚠️ VERALTET – Phase 2: Visual Seasons – Biome-Spoofing 🍂
> **Konzept:** `Plannung/phase2-biome-spoofing-concept.md`
> **Status:** ❌ VERWORFEN – Ersetzt durch **Phase 2.6: Custom-Biome-Datapack** (siehe `Plannung/custom-biome-concept.md`)
> **Grund:** `world.setBiome()` + `refreshChunk()` ändert Biome serverseitig, aber der Client sieht die Farbänderung NICHT zuverlässig. Der ProtocolLib-Ansatz (Phase 2.5) scheiterte an Minecrafts komplexem Farb-Rendering. Der neue Datapack-Ansatz mit Custom-Biomes funktioniert im Proof-of-Concept.
> **Original-Ziel:** Immersive saisonale Laub-/Gras-Farben rein plugin-basiert – OHNE NMS/Reflection, stattdessen mit Paper-API `world.setBiome()` / `world.refreshChunk()`. Der Client berechnet Foliage/Grass-Colors automatisch aus dem geänderten Biome.
> **Voraussetzung:** Phase 2-PRE abgeschlossen (kein alter NMS-Code mehr), Phase 1.5 abgeschlossen.

### Architektur (neues Package `seasons.visual`)

```
visual/
├── BiomeSpoofAdapter.java       (→ Hauptklasse, Listener + Runnable, ~350 Z.)
├── BiomeBackupStore.java        (→ Persistenz der Original-Biome auf Platte, ~150 Z.)
├── BiomeSpoofListener.java      (→ ChunkLoad/Unload/SeasonChange-Events, ~100 Z.)
├── BiomeFamily.java             (→ Enum: LAND, OCEAN)
├── SpoofMode.java               (→ Enum: OFF, GLOBAL_RING)
└── biome_spoof.yml              (→ Config-Datei)
```

| Klasse | Verantwortung |
|---|---|
| `BiomeSpoofAdapter` | Haupt-Koordinator. 40-Tick-Timer, iteriert über Online-Player, bestimmt Ziel-Biom pro Season+Family, Backup + `setBiome()` + `refreshChunk()`, Nudge-Queue. |
| `BiomeBackupStore` | Speichert Original-Biome pro Chunk als JSON (`biome_backups.json`). Lädt bei Start, speichert periodisch. |
| `BiomeSpoofListener` | `ChunkLoadEvent` → aus Caches entfernen. `ChunkUnloadEvent` → Revert + Cache-Cleanup. `SeasonChangeEvent` → Revert-Phase einleiten. |
| `BiomeFamily` | Enum: `LAND`, `OCEAN` (später erweiterbar: `SHORE`, `RIVER`). |
| `SpoofMode` | Enum: `OFF` (deaktiviert), `GLOBAL_RING` (um Spieler herum, Blickrichtungs-Priorisierung). |
| `biome_spoof.yml` | Vollständige Config (Season-Mappings, Ocean-Settings, Excluded-Biomes, Transition-Days, Budget, Radius). |

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.1 | Config & Datenmodell | `biome_spoof.yml`, `SpoofMode.java`, `BiomeFamily.java` | [x] |
| 2.2 | BiomeBackupStore | `BiomeBackupStore.java` (Persistenz + Load/Restore) | [x] |
| 2.3 | BiomeSpoofAdapter – Grundgerüst | `BiomeSpoofAdapter.java` (init, reload, Timer, Player-Loop) | [x] |
| 2.4 | BiomeSpoofAdapter – Klassifizierung & Mappings | Family-Klassifizierung, Season→Biome-Mapping, isChunkExcluded, shouldSkipSpoof | [x] |
| 2.5 | BiomeSpoofAdapter – Capture & Apply | `captureAndApply()`, `revertChunk()`, `revertAll()`, `refreshChunk()` | [x] |
| 2.6 | BiomeSpoofAdapter – Nudge-System | `nudgeViewers()`, `enqueueNudge()`, `flushNudges()` | [x] |
| 2.7 | BiomeSpoofListener – Events | ChunkLoad/Unload/SeasonChange-Listener | [x] |
| 2.8 | Integration & Test | `SeasonsPlugin.java` (init/shutdown), Build, Deploy, Funktionstest | [x] |

### Sprint-Details

#### 2.1 – Config & Datenmodell
- `biome_spoof.yml` wie im Konzept spezifiziert anlegen
- `SpoofMode.java`: `OFF`, `GLOBAL_RING` (nur globale Ring-Methode für MVP)
- `BiomeFamily.java`: `LAND`, `OCEAN`
- `ConfigManager.java` erweitern: `biome_spoof.yml` laden

#### 2.2 – BiomeBackupStore
- JSON-Serialisierung/Deserialisierung mit Gson
- Methoden: `saveFirstTouch(Chunk, Biome[])`, `loadAll()`, `saveAll()`, `purgeOld()`
- World-UID als Top-Level-Key, Chunk-Keys als `\"cx_cz\"`

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
- `isColdBiome(Biome)`: String-basierte Prüfung auf \"SNOWY\", \"FROZEN\", \"ICE\", \"GROVE\", \"PEAK\", \"MOUNTAIN\"

#### 2.5 – Capture & Apply
- `captureAndApply(Chunk, Biome)`: Original-Biome in Liste sammeln → Backup → setBiome() für alle Sections → refreshChunk()
- `revertChunk(Chunk)`: Backup-Array durchgehen → setBiome() original → refreshChunk()
- `revertAll()`: Alle geladenen Chunks revertieren

#### 2.6 – Nudge-System
- `nudgeViewers(World, chunkX, chunkZ)`: Für alle Spieler im View-Distance-Radius Nudge enqueuen
- `enqueueNudge(Player, World, chunkX, chunkZ)`: Nudge pro Spieler in Queue, 3s Cooldown
- `flushNudges()`: Max 8 Nudges pro Tick abarbeiten

#### 2.7 – BiomeSpoofListener
- `onChunkLoad(ChunkLoadEvent)`: Entferne Chunk aus spoofed/backups/lastApplied/familyCache
- `onChunkUnload(ChunkUnloadEvent)`: Falls gespooft → revertChunk(), entferne aus allen Maps
- `onSeasonChange(SeasonUpdateEvent)`: Setze `seasonTransitionUntil = now + 5000`. Transition-Fenster für Pre-Transition-Faktor.

#### 2.8 – Integration & Test
- `SeasonsPlugin.java`: BiomeSpoofAdapter init + BiomeBackupStore laden in `onEnable()`, unregister in `onDisable()`
- Build, Deploy auf Server
- Test-Matrix: Alle 4 Seasons durchschalten mit `/season skip`
- Prüfen: Biome-Wechsel sichtbar? Chunk-Refresh? Keine Doppel-Reverts?
- Performance: Tick-Auslastung mit 5-10 Spielern messen

### Done‑Definition Phase 2
- [x] `BiomeSpoofAdapter` arbeitet mit 40-Tick-Timer, kein NMS
- [x] `biome_spoof.yml` wird korrekt geladen und genutzt
- [x] Im Herbst: Plains → Windswept Savanna, Wälder → herbstliche Brauntöne
- [x] Im Frühling: Plains → Flower Forest (blühendes Grün)
- [x] Im Winter: Plains → Snowy Plains (weiß), Ozean → Frozen Ocean
- [x] Im Sommer: Plains → Plains (Vanilla)
- [x] Ozeane werden korrekt behandelt (Deep-Varianten erhalten, Frozen Ocean im Winter)
- [x] Excluded Biomes (Mushroom, Deep Dark, The Void) bleiben unverändert
- [x] Chunk-Unload/Server-Stop revertiert alle gespooften Chunks sauber
- [x] `biome_backups.json` persistiert Original-Biome Crash-sicher
- [x] Performance: <5% Tick-Auslastung durch Spoofing
- [x] Keine Konflikte mit Snow-System aus Phase 1.5
- [x] Keine NMS/Reflection-Nutzung in Phase 2

---

## ⚠️ VERALTET – Phase 2.5: ProtocolLib Chunk-Packet-Override 🎨
> **Konzept:** `Plannung/phase2-protocollib-biome-color-override-concept.md`
> **Status:** ❌ VERWORFEN – Ersetzt durch **Phase 2.6: Custom-Biome-Datapack** (siehe `Plannung/custom-biome-concept.md`)
> **Grund:** Packet-Override via ProtocolLib konnte Biome-Farben nicht zuverlässig client-seitig rendern. Minecraft's Farb-Rendering ist zu komplex (Biome-Blending, Client-Caches). Der Datapack-Ansatz mit echten Custom-Biomes funktioniert.
> **Original-Ziel:** Die serverseitigen Biome-Änderungen aus Phase 2 client-seitig sichtbar machen. Dazu wird ProtocolLib verwendet, um `ClientboundLevelChunkWithLightPacket`-Pakete abzufangen und die Biome-Daten vor dem Senden an den Client zu überschreiben.
> **Voraussetzung:** Phase 2 abgeschlossen (BiomeSpoofAdapter, BiomeBackupStore, BiomeSpoofListener laufen).
> **Abhängigkeit:** ProtocolLib muss auf dem Server installiert sein (`softdepend` in `plugin.yml`).

### Was aus Phase 2 erhalten bleibt

| Klasse | Änderung |
|---|---|
| `BiomeSpoofAdapter.java` | `captureAndApply()`, `revertChunk()`, `revertAll()`, Klassifizierung, Mappings – **unverändert** |
| `BiomeSpoofListener.java` | ChunkLoad/Unload/SeasonChange – **unverändert** |
| `BiomeBackupStore.java` | Persistenz – **unverändert** |
| `biome_spoof.yml` | Config – **um `resend_chunks_per_tick` und `resend_enabled` erweitert** |

### Was aus Phase 2 wegfällt / ersetzt wird

| Artefakt | Aktion |
|---|---|
| `flushResends()` + `chunksNeedingResend` in `BiomeSpoofAdapter` | Ersetzt durch `ChunkPacketInterceptor` (ProtocolLib-basiert) |
| `nudgeViewers()` / `enqueueNudge()` / `flushNudges()` | Entfällt – Chunk-Re-Send triggert Client-Update direkt |
| Heartbeat-Diagnose | Bleibt, aber angepasst an neue Metriken |

### Neue Architektur

```
visual/
├── BiomeSpoofAdapter.java       (→ bestehend, mini-Änderungen)
├── BiomeSpoofListener.java      (→ bestehend, unverändert)
├── BiomeBackupStore.java        (→ bestehend, unverändert)
├── ChunkPacketInterceptor.java  (→ NEU: ProtocolLib-Packet-Interceptor)
├── BiomeFamily.java             (→ unverändert)
├── SpoofMode.java               (→ unverändert)
└── biome_spoof.yml              (→ erweitert)
```

### Datenfluss

```
1. SeasonChangeEvent → BiomeSpoofListener.onSeasonChange()
2. Timer (40 ticks): BiomeSpoofAdapter.run()
   └── setBiome() serverseitig + markiert Chunk als \"dirty\"
3. flushResends(): chunk.unload(true) + world.getChunkAt()
   └── Server sendet ClientboundLevelChunkWithLightPacket
4. ChunkPacketInterceptor.onPacketSending()
   └── Prüft ob Chunk gespooft → überschreibt Biome-Daten im Packet
5. Client empfängt Chunk-Paket mit neuen Biome-Daten → Farben ändern sich
```

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.5.1 | ProtocolLib-Dependency + plugin.yml | `build.gradle.kts`, `plugin.yml` | [ ] |
| 2.5.2 | ChunkPacketInterceptor – Grundgerüst + Registrierung | `ChunkPacketInterceptor.java` (neu), `SeasonsPlugin.java` (erweitern) | [ ] |
| 2.5.3 | Packet-Override-Logik (Biome-Daten im Chunk-Paket überschreiben) | `ChunkPacketInterceptor.java` (erweitern) | [ ] |
| 2.5.4 | Integration mit BiomeSpoofAdapter + Aufräumen | `BiomeSpoofAdapter.java` (Nudge-Code entfernen, Re-Send vereinfachen), `biome_spoof.yml` (erweitern) | [ ] |
| 2.5.5 | Build, Deploy, Funktionstest | `build\libs\Seasons-0.1.0-SNAPSHOT.jar` | [ ] |

### Sprint-Details

#### 2.5.1 – ProtocolLib-Dependency + plugin.yml
- `build.gradle.kts`: ProtocolLib-Repository (`repo.dmulloy2.net`) + Dependency (`compileOnly(\"com.comphenix.protocol:ProtocolLib:5.3.0\")`)
- `plugin.yml`: `softdepend: [ProtocolLib]` hinzufügen
- Build testen: `compileJava` muss mit ProtocolLib im Classpath funktionieren
- **Achtung:** ProtocolLib 5.3.0 ist die letzte stabile Version für 1.21.x

#### 2.5.2 – ChunkPacketInterceptor Grundgerüst + Registrierung
- Neue Klasse `visual/ChunkPacketInterceptor.java`
- Implementiert `PacketListener` von ProtocolLib
- Registriert sich auf `PacketType.Play.Server.MAP_CHUNK` für SENDING-Events
- Zugriff auf `BiomeSpoofAdapter.getSpoofedSet()` und `getLastAppliedMap()`
- Grundgerüst: Log-Ausgabe wenn ein Chunk-Paket abgefangen wird
- Registrierung in `SeasonsPlugin.onEnable()` via `ProtocolLibrary.getProtocolManager().addPacketListener(interceptor)`
- Deregistrierung in `onDisable()` via `ProtocolLibrary.getProtocolManager().removePacketListener(interceptor)`

#### 2.5.3 – Packet-Override-Logik
- `onPacketSending(PacketEvent event)` implementieren
- Chunk-Koordinaten aus dem Packet lesen (StructureModifier / PacketContainer API)
- Prüfen ob Chunk in `spoofed` Set und `lastApplied` Map
- Biome-Daten im Packet überschreiben:
  - Zugriff auf die Biome-Palette im Chunk-Paket (NMS-Reflection via ProtocolLib)
  - Alle Biome-IDs in der Palette durch das Ziel-Biom ersetzen
  - ODER: Biome-Section-Array durchgehen und Ziel-Biome setzen
- Nach erfolgreichem Override: Log-Ausgabe (nur jeden 10. Chunk)

#### 2.5.4 – Integration mit BiomeSpoofAdapter + Aufräumen
- `captureAndApply()`: Nach `setBiome()` + `refreshChunk()` → `chunk.unload(true)` + `world.getChunkAt()` triggern
- Nudge-System komplett entfernen:
  - `nudgeQueues`, `nudgeLast`, `nudgeCooldownMs`, `nudgeMaxPerTick` Felder löschen
  - `nudgeViewers()`, `enqueueNudge()`, `flushNudges()` Methoden löschen
  - `nudge`-Konfiguration aus `reloadFromConfig()` entfernen
- `biome_spoof.yml` erweitern:
  - `resend_enabled: true` (NEU)
  - `resend_chunks_per_tick: 8` (NEU)
- `ConfigManager`: Neue Felder auslesen
- Heartbeat-Log anpassen: Statt Nudge-Queue → Re-Send-Queue-Pending anzeigen

#### 2.5.5 – Build, Deploy, Funktionstest
- Build: `compileJava` + `shadowJar -x test`
- ProtocolLib auf dem Server prüfen/installieren
- Deploy: JAR kopieren, `biome_spoof.yml` kopieren, Server restart
- Test-Matrix:
  1. Einloggen → `captureAndApply`-Logs für Initial-Biome erscheinen
  2. `/season set fall` → `ChunkPacketInterceptor`-Logs erscheinen
  3. **Visuell prüfen: Laubfarben ändern sich?**
  4. `/season set winter` → erneut prüfen
  5. `/season set spring` → erneut prüfen
  6. Mit 2+ Spielern testen (verschiedene Positionen)

### Done‑Definition Phase 2.5
- [ ] ProtocolLib ist als `softdepend` registriert, Plugin startet auch ohne ProtocolLib
- [ ] `ChunkPacketInterceptor` fängt Chunk-Pakete ab und überschreibt Biome-Daten
- [ ] **Laub-/Gras-Farben ändern sich sichtbar bei Season-Wechsel**
- [ ] Herbst: Braune/Orange Töne (Windswept Savanna)
- [ ] Winter: Weiße/Graue Töne (Snowy Plains, Frozen Ocean)
- [ ] Frühling: Blühendes Grün (Flower Forest)
- [ ] Sommer: Normales Grün (Plains)
- [ ] Keine Nudge-Queue mehr – Code ist sauber entfernt
- [ ] Performance: Max 8 Chunk-Re-Sends pro Tick
- [ ] Re-Send-Queue wird nicht größer als ~500 Einträge
- [ ] Keine NMS-Importe außerhalb von `ChunkPacketInterceptor`
- [ ] Build erfolgreich

---

## Phase 2.6: Custom-Biome-Datapack – Biome-Farben sichtbar machen 🎨
> **Konzept:** `Plannung/custom-biome-concept.md`
> **Ziel:** Saisonale Laub-/Gras-Farben client-seitig sichtbar machen – durch ein Plugin-generiertes Custom-Biome-Datapack + `world.setBiome()`. Der Ansatz aus Phase 2 (Biome-Spoofing) bleibt als Grundgerüst erhalten, aber statt auf Vanilla-Biome zu mappen, werden **generierte Custom-Biomes** mit interpolierten Farben verwendet.
> **Voraussetzung:** Phase 2-PRE abgeschlossen (kein alter NMS-Code), Phase 1.5 abgeschlossen. Phase 2 (BiomeSpoofAdapter, BiomeBackupStore, BiomeSpoofListener) wird als Basis verwendet und erweitert.

### Warum dieser Ansatz?

| Ansatz | Ergebnis |
|--------|----------|
| `world.setBiome()` + `refreshChunk()` (Phase 2) | ❌ Client sieht keine Farbänderung |
| ProtocolLib Packet-Override (Phase 2.5) | ❌ Farb-Rendering zu komplex |
| **Custom-Biome via Datapack + `world.setBiome()`** | ✅ **Funktioniert!** Im Proof-of-Concept validiert |

### Architektur (nach Refactoring)

```
visual/
├── BiomeFamily.java              ✅ unverändert
├── SpoofMode.java                ✅ unverändert
├── BiomeBackupStore.java         🔄 Registry-Lookup statt Biome.valueOf()
├── BiomeSpoofListener.java       🔄 Leichte Anpassungen
├── BiomeSpoofCoordinator.java    🆕 Timer, Spieler-Loop, Budget
├── SeasonBiomeResolver.java      🆕 Klassifizierung + Target + Sub-Varianten
├── ChunkBiomeApplier.java        🆕 captureAndApply/revert
├── TransitionManager.java        🆕 Nacht-Check, Stufen-Tracking
├── SeasonColorConfig.java        🆕 Liest season_colors.yml
├── VanillaBiomeReference.java    🆕 Hält Dump der Original-Biom-Farben (im JAR)
└── BiomeJsonGenerator.java       🆕 Erzeugt alle Custom-Biome-JSONs

❌ ChunkPacketInterceptor.java    GELÖSCHT (Phase 2.6a)
❌ BiomeSpoofAdapter.java         AUFGETEILT (→ Coordinator, Resolver, Applier)
```

### Neue Config-Dateien

| Datei | Zweck |
|-------|-------|
| `season_colors.yml` | Zentrale Farbsteuerung: Ziel-Farben pro Saison, Biom-Overrides, Transition-Steps |
| `biome_spoof.yml` | Vereinfacht: Transition-Steuerung, `seasons`- und `oceans.seasons`-Sektionen ENTFALLEN (Mapping jetzt dynamisch aus Original-Biom abgeleitet) |

### Generator-Prinzip

1. **VanillaBiomeReference** liest beim Start die Original-Biom-Farben aus dem Plugin-JAR (einmaliger Dump der Vanilla-Biome-JSONs)
2. **SeasonColorConfig** liest `season_colors.yml` (Ziel-Farben, Overrides, Transition-Steps)
3. **BiomeJsonGenerator** interpoliert für jedes konfigurierte Biom: `lerp(original, target, blend_factor)` → N Sub-Varianten pro Übergang
4. Schreibt generierte Custom-Biome-JSONs nach `world/datapacks/seasons_biomes/`

**Kernregel: Jede generierte JSON ist eine 1:1-Kopie des Vanilla-Originals** (temperature, downfall, precipitation, effects, carvers, features, spawn_costs, spawners). **Ausschließlich** `grass_color` und `foliage_color` werden überschrieben. Später erweiterbar um `sky_color`, `water_color`, `water_fog_color`, `fog_color` (siehe `Plannung/_TODO-future-features.md`).
5. Nach Server-Neustart lädt die Welt die Custom-Biomes → Farben aktiv

### Transition-Konzept: Sub-Varianten mit Nacht-Wechsel

Statt räumlichem Staffeln wechseln **alle sichtbaren Chunks gleichzeitig** in der Nacht. Der sanfte Übergang entsteht durch mehrere **Farb-Zwischenstufen** (Sub-Varianten), die in aufeinanderfolgenden Nächten getauscht werden.

```
Sommer → Herbst (4 Stufen):
  Nacht 1: late_summer  (t=0.25)
  Nacht 2: early_fall   (t=0.50)
  Nacht 3: mid_fall     (t=0.75)
  Nacht 4: fall         (t=1.00)
```

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.6a | Aufräumen: ChunkPacketInterceptor + ProtocolLib entfernen | `ChunkPacketInterceptor.java` (löschen), `build.gradle.kts`, `plugin.yml` | [ ] |
| 2.6b | SeasonColorConfig + VanillaBiomeReference | `SeasonColorConfig.java`, `VanillaBiomeReference.java`, `season_colors.yml` | [ ] |
| 2.6c | BiomeJsonGenerator + `/season generate-biomes` | `BiomeJsonGenerator.java`, `MainSeasonCommand.java` (erweitern) | [x] |
| 2.6d | Refactoring: BiomeSpoofAdapter aufteilen | `BiomeSpoofCoordinator.java`, `SeasonBiomeResolver.java`, `ChunkBiomeApplier.java` | [~] (2.6d1 ✅) |
| 2.6e | TransitionManager | `TransitionManager.java` (Nacht-Check, Stufen-Tracking) | [ ] |
| 2.6f | BiomeBackupStore: Registry-Lookup | `BiomeBackupStore.java` (`Biome.valueOf()` → Registry) | [ ] |
| 2.6g | Integration & Test | `SeasonsPlugin.java`, Build, Deploy, Farb-Test | [~] (weitgehend durch 2b.5 erledigt – Frost-System testet gleichzeitig 2.6-Infrastruktur) |

### Sprint-Details

#### 2.6a – Aufräumen
- `ChunkPacketInterceptor.java` löschen (157 Zeilen toter Code)
- ProtocolLib-Dependency aus `build.gradle.kts` entfernen
- `softdepend: [ProtocolLib]` aus `plugin.yml` entfernen
- Build testen

#### 2.6b – SeasonColorConfig + VanillaBiomeReference
- `VanillaBiomeReference.java`: Liest Vanilla-Biome-JSONs aus JAR-Ressource `vanilla_biomes/`, stellt `getGrassColor(Biome)` / `getFoliageColor(Biome)` bereit
- `SeasonColorConfig.java`: Liest `season_colors.yml`, stellt `getTargetColor(Biome, Season, ColorType)`, `getTransitionSteps(Season, Season)` bereit
- `season_colors.yml` im Ressourcen-Ordner anlegen, wird nach `plugins/Seasons/` kopiert

#### 2.6c – BiomeJsonGenerator
- Iteriert über `enabled_biomes` aus Config
- Interpoliert Farben: `lerp(original, target, step/totalSteps)`
- Schreibt JSONs nach `world/datapacks/seasons_biomes/data/seasons/worldgen/biome/`
- Aktualisiert `pack.mcmeta`
- Command: `/season generate-biomes [force]`
- Hash-Check: Nur bei Config-Änderung neu generieren

#### 2.6d – Refactoring BiomeSpoofAdapter
- `BiomeSpoofCoordinator`: Timer, Spieler-Loop, Budget (aus `BiomeSpoofAdapter.run()`)
- `SeasonBiomeResolver`: Klassifizierung LAND/OCEAN, **dynamisches Per-Biome-Mapping** (siehe Konzept 11a).
  **Kein** hartes `seasonTarget.get(season)` mehr – jedes Vanilla-Biom bekommt eigene Custom-Variante:
  `seasons:<variant>_<biomeKey>` (z.B. `seasons:fall_swamp`, `seasons:fall_birch_forest`)
- `ChunkBiomeApplier`: `captureAndApply()`, `revertChunk()`, `revertAll()` (aus `BiomeSpoofAdapter`)
- `BiomeSpoofAdapter.java` wird nach erfolgreichem Test gelöscht

#### 2.6e – TransitionManager
- Zustandsmaschine: `fromSeason`, `toSeason`, `totalSteps`, `currentStep`, `nextTransitionTick`
- Trigger: `SeasonChangeEvent` → initialisiert Transition
- Nacht-Check: Alle 40 Ticks prüfen, ob Nacht erreicht → `currentStep++` → alle Chunks updaten
- `nights_per_step: 1` in `biome_spoof.yml`

#### 2.6f – BiomeBackupStore Registry-Lookup
- `Biome.valueOf()` (Enum) durch Registry-Lookup ersetzen: `Registry.BIOME.get(NamespacedKey)`
- Custom-Biomes haben keinen Vanilla-Enum-Wert, müssen via Registry geladen werden
- Rückwärtskompatibilität: Alte Backups mit Vanilla-Namen weiterhin lesbar

#### 2.6g – Integration & Test
- `SeasonsPlugin.java`: Neue Komponenten initialisieren, Generator bei Bedarf ausführen
- Build, Deploy, Server-Neustart (Datapack wird erst nach 2. Start aktiv)
- Test: `/season skip` durch alle Seasons, Farben mit F3 prüfen
- Test: Biome-Blending an Chunk-Grenzen beobachten

### Done‑Definition Phase 2.6
- [ ] `ChunkPacketInterceptor.java` + ProtocolLib-Abhängigkeit entfernt
- [ ] `BiomeJsonGenerator` erzeugt valide Custom-Biome-JSONs im Datapack
- [ ] **Laub-/Gras-Farben ändern sich sichtbar bei Season-Wechsel**
- [ ] Herbst: Braune/Orange Töne (via interpolierte Custom-Biomes)
- [ ] Winter: Graue/Entsättigte Töne (Nadelbäume bleiben grün!)
- [ ] Frühling: Original-Farben (blend_factor = 0)
- [ ] Sommer: Original-Farben (Vanilla)
- [ ] Transition über Nacht-Wechsel sichtbar (mehrere Stufen)
- [ ] Ozeane werden korrekt behandelt
- [ ] `BiomeBackupStore` kann Custom-Biomes laden/speichern
- [ ] Keine NMS/Reflection-Nutzung
- [ ] Keine ProtocolLib-Abhängigkeit mehr
- [ ] Build erfolgreich

---

## Phase 2b: Frost System – Frost-Biome + Partikel ❄️
> **Konzept:** `Plannung/frost-concept-phase-2b.md`
> **Ziel:** Frostige Optik bei Temperaturen unter 0°C – realisiert durch **Frost-Biome** (Custom-Biome-Datapack) + `SNOWFLAKE`-Partikel. Kein dynamischer Tint-Lerp, kein `sendBlockChange()`.
> **Voraussetzung:** Phase 2.6 abgeschlossen (BiomeJsonGenerator, SeasonBiomeResolver, Custom-Biome-Datapack).

### Prinzip

1. **BiomeJsonGenerator** erzeugt pro Vanilla-Biom eine zusätzliche Frost-Variante: `seasons:frost_<biome>` (z.B. `seasons:frost_forest`). Farbe: kühles Weiß/Grau (aus `frost.yml`).
2. **SeasonBiomeResolver** wählt das Frost-Biom statt des normalen Saison-Bioms, wenn die aktuelle Temperatur < `freeze-threshold` ist.
3. **FrostEffectManager** spawnt `SNOWFLAKE`-Partikel um Spieler, wenn Frost aktiv ist.

### Betroffene Klassen

| Klasse | Änderung |
|---|---|
| `BiomeJsonGenerator.java` | Erweitert: Liest Frost-Zielfarben aus `frost.yml`, erzeugt `frost_*.json` |
| `SeasonBiomeResolver.java` | Erweitert: `resolveBiome()` prüft Temperatur → wählt Frost-Variante |
| `FrostConfig.java` | 🆕 Liest `frost.yml` (Thresholds, Farben, Partikel, Excluded-Biomes) |
| `FrostEffectManager.java` | 🆕 Frost-Faktor, Partikel-Spawns, Biome-Filter |
| `BiomeSpoofCoordinator.java` | Minimal: Übergibt Temperatur an Resolver (bereits vorhanden) |

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2b.1 | FrostConfig + frost.yml | `FrostConfig.java`, `frost.yml`, `ConfigManager.java` | [x] |
| 2b.2 | BiomeJsonGenerator: Frost-Biome erzeugen | `BiomeJsonGenerator.java` (erweitern) | [x] |
| 2b.3 | SeasonBiomeResolver: Frost-Biom wählen | `SeasonBiomeResolver.java` (erweitern) | [x] |
| 2b.4 | FrostEffectManager: Partikel | `FrostEffectManager.java` (neu) | [x] |
| 2b.5 | Integration & Test | Build, Deploy, Server-Test | [x]

### Sprint-Details

#### 2b.1 – Config
- **frost.yml**: `freeze-threshold`, `full-frost-threshold`, `target-grass-color`, `target-foliage-color`, `particles.*`, `excluded-biomes`
- **FrostConfig.java** wrappt alle Werte
- **ConfigManager.java** registriert `frost.yml`

#### 2b.2 – Frost-Biome im Generator
- `BiomeJsonGenerator` liest Frost-Zielfarben aus `FrostConfig`
- Erzeugt pro `enabled_biome` eine `frost_<biome>.json` (z.B. `frost_forest.json`)
- Nur `grass_color` und `foliage_color` werden auf Frost-Farben gesetzt

#### 2b.3 – Resolver wählt Frost-Biom
- `SeasonBiomeResolver.resolveBiome(originalBiome, season, temperature)`:
  - Wenn `temperature < freezeThreshold` → `seasons:frost_<biomeKey>`
  - Sonst → normales Saison-Biom (`seasons:<variant>_<biomeKey>`)

#### 2b.4 – FrostEffectManager (Partikel)
- Berechnet `frostFactor` aus Temperatur (für Partikel-Intensität)
- Spawnt `SNOWFLAKE`-Partikel bei `frostFactor > 0`
- Prüft `excluded-biomes` (keine Partikel in Wüste, Nether, End)
- Periodischer Task (alle 4–8 Sekunden)
- Cleanup bei `PlayerQuitEvent`

#### 2b.5 – Integration & Test
- `SeasonsPlugin.onEnable()`: `FrostEffectManager` starten
- Build, Deploy, `/season generate-biomes force`, Server-Neustart
- Test: Im Winter + Nacht → Frost-Biome aktiv + Partikel sichtbar

### Done‑Definition Phase 2b
- [x] `BiomeJsonGenerator` erzeugt `frost_*.json` für alle `enabled_biomes`
- [x] `SeasonBiomeResolver` wählt Frost-Biom bei Temperatur < `freeze-threshold`
- [x] Gras-/Laub-Farben frostig (kühles Weiß/Grau) bei Frost
- [x] `SNOWFLAKE`-Partikel sichtbar, Dichte konfigurierbar
- [x] Keine Frost-Effekte in `excluded-biomes`
- [x] Schnelle Tag/Nacht-Reaktion (< 40 Ticks über Coordinator-Timer)
- [x] Keine Konflikte mit Snow-System (Phase 1.5) oder Custom-Biome-Datapack (Phase 2.6)

---

## Phase 3: Temperatur‑Effekte & Spieler‑Interaktion 🌡️
> **Konzept:** `Plannung/phase3-effects-concept.md`
> **Ziel:** Eis‑Effekt auf Wasser via Frost-Biome‑Temperatur (vanilla Random‑Tick), atmosphärischer Nebel via `fog_color` in Custom‑Biome‑JSONs, Spieler‑Debuffs bei extremen Temperaturen.
> **Grundlegende Überarbeitung nach Phase 2.6+2b:** `IceEffect` und `MistEffect` werden größtenteils durch Vanilla‑Mechaniken ersetzt (Biome‑Temperatur, Custom‑Biome‑`fog_color`). Nur `TemperatureEffect` bleibt als eigener Potion‑Manager. `SeasonalEffect`‑Interface + `EffectScheduler` bündeln Laufzeiteffekte in einem Timer.

### Neue Architektur

```
effects/
├── SeasonalEffect.java         (Interface: tick + isApplicable)
├── EffectScheduler.java        (Ein Timer, Spieler-Iteration, delegiert)
├── FrostEffectManager.java     (umbauen: implements SeasonalEffect)
└── TemperatureEffect.java      (NEU: Potion-Debuffs)

❌ IceEffect.java               GELÖSCHT
❌ MistEffect.java              GELÖSCHT (atmosphärischer Nebel via Biome, Partikel-Nebel später)
```

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 3.1 | BiomeJsonGenerator + Configs: `temperature` + `fog_color` in JSONs | `BiomeJsonGenerator.java` (erweitern), `season_colors.yml` (fog), `frost.yml` (temperature) | [ ] |
| 3.2 | SeasonalEffect‑Interface + EffectScheduler + FrostEffectManager umbauen | `SeasonalEffect.java`, `EffectScheduler.java`, `FrostEffectManager.java` | [ ] |
| 3.3 | TemperatureEffect – Potion‑Debuffs (Hunger/Slowness) | `TemperatureEffect.java` | [ ] |
| 3.4 | Config‑Erweiterung `temperature-effects` + Tests | `config.yml` (erweitern) | [ ] |
| 3.5 | Integration, Build, Deploy & Test | `SeasonsPlugin.java` | [ ] |

**Done‑Definition Phase 3:**
- [ ] Frost‑Biome setzen `temperature < 0` → stehendes Wasser friert per Vanilla Random‑Tick
- [ ] `fog_color` in Custom‑Biome‑JSONs → atmosphärischer Horizont‑Dunst sichtbar
- [ ] Bei `temp < -0.2` (konfigurierbar) leichter Hunger‑Effekt
- [ ] Bei `temp < -0.5` (konfigurierbar) Slowness I
- [ ] Bei `temp > 0.8` (konfigurierbar) Erschöpfungs‑Hunger
- [ ] `EffectScheduler` bündelt alle Laufzeiteffekte in EINEM Timer
- [ ] `FrostEffectManager` implementiert `SeasonalEffect` Interface
- [ ] Kein manueller `IceEffect`‑Code (kein Block‑Scan, kein `setType(ICE)`)
- [ ] Kein separater `MistEffect`‑Code (atmosphärischer Nebel via Biome, Partikel‑Nebel in Zukunft)
- [ ] Keine NMS/Reflection
- [ ] Build erfolgreich

---

## Phase 4: Fortgeschrittene Wettereffekte 🌩️
**Ziel:** Hagel, Monsun‑Regen im Dschungel, Gewitter-Häufigkeit nach Season. Pro‑Chunk‑Wetter für realistischere Übergänge.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 4.1 | HailEffect – Hagel‑Partikel + leichter Schaden | `HailEffect.java` | [ ] |
| 4.2 | MonsoonEffect – Dschungel‑Starkregen | `MonsoonEffect.java` | [ ] |
| 4.3 | ThunderFrequency‑Modulation | `ThunderModulator.java` | [ ] |
| 4.4 | Pro‑Chunk‑Wetter (Ablösung der Hybrid-Lösung aus Phase 1) | `WeatherInterceptor.java` (Refactor) | [ ] |
| 4.5 | Config‑Vollständigkeit & Template‑Export | `config_full_template.yml` | [ ] |

---

## Phase 5: Admin‑Tooling & Polishing 🧰
**Ziel:** Admin‑Commands zum Testen, Triggern, Konfigurieren. PlaceholderAPI‑Integration. Performance‑Profiling. Dokumentation finalisieren.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 5.1 | `/season skip`, `/season set`, `/season speed` Feinschliff | `SeasonAdminCommand.java` | [ ] |
| 5.2 | PlaceholderAPI‑Integration (`%seasons_current%`, `%seasons_day%`, `%seasons_temperature%`, …) | `SeasonsPlaceholder.java` | [ ] |
| 5.3 | Performance‑Profil (Ticks, Chunk‑Load, Memory) | — | [ ] |
| 5.4 | README, Developer‑Guide, Handover finalisieren | `README.md`, `docs/` | [ ] |
| 5.5 | Testplan‑Abschluss & Bugfixing | `Plannung/testplan.md` | [ ] |

**Done‑Definition Phase 5:**
- [ ] Alle Admin-Commands funktionieren mit voller Permission-Prüfung
- [ ] PlaceholderAPI registriert %seasons_*% Variablen
- [ ] Performance innerhalb gesetzter Grenzen (<5% Tick-Auslastung)
- [ ] Dokumentation vollständig und aktuell

---

**Legende:** [ ] = offen | [~] = in Arbeit | [x] = abgeschlossen