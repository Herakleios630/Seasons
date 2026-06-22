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

## Phase 2: Visual Seasons – Foliage Tints 🍂
> **Konzept:** `Plannung/visual-seasons-concept.md`
> **Ziel:** Immersive, rein plugin-basierte saisonale Laub-/Gras-Farben. Biome-Tinting per NMS-Packet-Overrides. Alles client-seitig – kein Modding, kein Resource-Pack-Zwang. Frost-Effekte sind bewusst ausgeklammert und werden in Phase 2b als reines Tint-Lerp + Partikel-System umgesetzt.
> **Wichtig:** Erst in Phase 2 sind NMS/Reflection erlaubt (Paper-Adapter).
> **Voraussetzung:** Phase 1.5 abgeschlossen (stabiles Snow-System als Fundament).

### Architektur (neues Package `seasons.visual`)
| Klasse | Verantwortung |
|---|---|
| `VisualSeasonManager.java` | Haupt-Koordination, pro-Spieler Tick-Loop |
| `FoliageTintManager.java` | Biome Color Tinting per NMS-Packet-Overrides |
| `SeasonalColorCalculator.java` | Interpolation Saison-Farben |
| `VisualConfig.java` | Config-Handling für `visual.yml` |
| `nms/NmsAdapter.java` | Version-sichere NMS-Abstraktion |

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.1 | Foundation | `NmsAdapter.java`, `VisualConfig.java`, `SeasonalColorCalculator.java`, `visual.yml` | [ ] |
| 2.2 | FoliageTintManager | `FoliageTintManager.java`, `VisualSeasonManager.java` (Grundgerüst) | [ ] |
| 2.3 | Integration | `PlayerJoinListener.java`, `SeasonChangeListener.java`, `SeasonsPlugin.java` (Tick) | [ ] |
| 2.4 | Polish & Testing | `visual.yml` Feintuning, Test-Matrix, `docs/` | [~] |

### Sprint-Details

#### 2.1 – Foundation
- **NmsAdapter.java**: Abstraktion für Paper-API-Versionen (Packet-Play-Out-Map-Chunk, Biome-Farben)
- **VisualConfig.java**: Lädt `plugins/Seasons/visual.yml` mit `foliage.seasons`, `visual.transition-days`
- **SeasonalColorCalculator.java**: Lineare Interpolation zwischen Saison-Farbe und Biome-Multiplier
- **visual.yml**: Vollständige Default-Config (Season-Farben pro Baumart)

#### 2.2 – FoliageTintManager
- Überschreibt Biome-Farben (Foliage + Grass) via `PacketPlayOutMapChunk`-Hook
- Pro Saison konfigurierbare Default-Tints + Overrides pro Baumart (Birch, Oak, Dark Oak, Cherry, …)
- Biome-Multiplier (Taiga ×1.3, Mountains ×1.5, …)
- `VisualSeasonManager` als Koordinator (pro Spieler, Chunk-basiert)
- **Kein Frost-Lerp** – Frost folgt in Phase 2b als separater Layer

#### 2.3 – Integration
- `PlayerJoinEvent` → FoliageTintManager sendet aktuelle Saison-Farben
- `SeasonChangeEvent` → komplette Neuberechnung für alle Online-Spieler
- Tick-Loop in `VisualSeasonManager.onTick(Player)`: Saison-Tint berechnen und senden

#### 2.4 – Polish & Testing
- Übergänge feintunen: Saison-Übergang langsam (mehrere Tage)
- Test-Matrix: Alle 4 Seasons durchschalten
- Performance-Profil: Tint-Update
- Dokumentation in `docs/developer-guide.md` und `docs/visual-seasons-concept.md`

### Done‑Definition Phase 2
- [x] `NmsAdapter` abstrahiert Paper-Versionen; Fallback bei nicht unterstützter Version
- [x] Im Herbst leuchten Birken orange, Eichen rot, Dark-Forest gelb
- [x] Im Frühling Kirsch-Biome rosa / intensiver
- [x] Im Winter alles braun/grau
- [x] Im Sommer Vanilla-Farben
- [x] Alle Farben über `foliage_tints.yml` konfigurierbar
- [x] Keine Konflikte mit Snow-System aus Phase 1.5
- [x] Performance: <5% Tick-Auslastung durch Visual-System

---

## Phase 2b: Frost System – Temperaturabhängiger Frost (Tint-Lerp + Partikel) ❄️
> **Konzept:** `Plannung/frost-concept-phase-2b.md`
> **Ziel:** Eine frostige/weiße Optik bei Temperaturen <0°C, unabhängig von echten Snow-Layern. Rein plugin-basiert über Biome-Color-Tint-Lerp und `SNOWFLAKE`-Partikel. Kein `sendBlockChange()`, kein permanenter Block-State.
> **Voraussetzung:** Phase 2 (`FoliageTintManager` + `VisualSeasonManager`) muss abgeschlossen sein.

### Architektur (neue/ergänzte Klassen)
| Klasse | Verantwortung |
|---|---|
| `FrostEffectManager.java` | Frost-Faktor, Biome-Filter, Partikel-Spawns |
| `FrostConfig.java` | Config-Handling für `frost.yml` |

### Sprint-Übersicht

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2b.1 | Config – FrostConfig + frost.yml | `FrostConfig.java`, `frost.yml`, `ConfigManager.java` (erweitern) | [ ] |
| 2b.2 | FrostEffectManager – Frost-Faktor + Partikel | `FrostEffectManager.java` (neu) | [ ] |
| 2b.3 | Integration in VisualSeasonManager – Tint-Lerp | `VisualSeasonManager.java` (erweitern) | [ ] |
| 2b.4 | Testing & Feintuning | Test-Matrix, Partikel-Dichte, Performance | [ ] |

### Sprint-Details

#### 2b.1 – Config
- **frost.yml** mit `freeze-threshold`, `full-frost-threshold`, `day-night-transition-seconds`, `intensity-multiplier`, `target-color`, `tint-strength`, `particles.*`, `excluded-biomes`
- **FrostConfig.java** lädt und wrappt die Werte
- **ConfigManager.java** registriert `frost.yml`

#### 2b.2 – FrostEffectManager
- Berechnet `frostFactor` pro Spieler aus `TemperatureCalculator.getTemperature(loc)`
- Prüft `excluded-biomes` (Biome-Filter, keine Frost-Effekte in Wüste/Nether/End)
- Spawnt `SNOWFLAKE`-Partikel um den Spieler bei `frostFactor > 0`
- Periodischer Task (alle 4–8 Sekunden) für aktive Spieler
- Cleanup bei `PlayerQuitEvent`

#### 2b.3 – Integration
- `VisualSeasonManager.updatePlayerVisuals()` erweitern:
  1. Saison-Tint von `FoliageTintManager`
  2. `frostFactor` von `FrostEffectManager`
  3. Finaler Tint: `lerp(seasonTint, frostTargetColor, frostFactor * tintStrength)`
- `FrostEffectManager` kennt den `FoliageTintManager` nicht direkt
- Kein `PlayerMoveEvent`-Trigger; 4s-Timer fängt Temperaturänderungen ein

#### 2b.4 – Testing & Feintuning
- Tag/Nacht-Frost-Übergang muss flüssig sein (60s Transition)
- Partikel-Dichte bei 10–15 Spielern testen
- Biome-Ausschlussliste prüfen (keine Frost-Partikel in Desert, Savanna, Nether, End)
- Performance: Partikel-Spawns sind leichtgewichtig, kein zusätzlicher CPU-Verbrauch

### Done‑Definition Phase 2b
- [ ] Deutlich frostige Optik unter 0°C (Gras/Laub bleicht aus)
- [ ] `SNOWFLAKE`-Partikel bei Frost sichtbar, konfigurierbare Dichte
- [ ] Keine Frost-Effekte in `excluded-biomes` (Wüste, Nether, End etc.)
- [ ] Schnelle Tag/Nacht-Reaktion ohne Lag
- [ ] Keine spürbaren Performance-Einbußen bei 10–15 Spielern
- [ ] Alle Werte über `frost.yml` steuerbar
- [ ] Keine Konflikte mit Snow-System aus Phase 1.5

---

## Phase 3: Temperatur‑Effekte & Spieler‑Interaktion 🌡️
**Ziel:** Extreme Temperaturen beeinflussen Spieler (Hunger, Speed). Eis‑Effekt auf stehendem Gewässer. Nebel bei Übergängen.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 3.1 | SeasonalEffect‑Interface + EffectScheduler | `SeasonalEffect.java`, `EffectScheduler.java` | [ ] |
| 3.2 | TemperatureEffect – Spieler‑Modifier bei Kälte/Hitze | `TemperatureEffect.java` | [ ] |
| 3.3 | MistEffect – Sicht‑Nebel in Morgen/Abend‑Stunden des Herbstes | `MistEffect.java` | [ ] |
| 3.4 | IceEffect – Stehendes Wasser friert bei Minusgraden | `IceEffect.java` | [ ] |
| 3.5 | Config‑Erweiterung für Effekt‑Stärken | `config.yml` (Patch) | [ ] |
| 3.6 | Test‑Durchlauf aller Effekte | — | [ ] |

**Done‑Definition Phase 3:**
- [ ] Bei -0.2 °C effektiver Temperatur leichter Hunger‑Effekt
- [ ] Bei -0.5 °C Slowness‑Stufe
- [ ] Hitzewellen im Sommer idem (Erschöpfung)
- [ ] Nebel in morgendlichen Herbst‑Biomen
- [ ] Stehendes Wasser friert bei anhaltenden Minusgraden (Random‑Tick)
- [ ] Bei extremen Temperaturen können auch Wasserfälle einfrieren
- [ ] Eis taut bei positiven Temperaturen wieder

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