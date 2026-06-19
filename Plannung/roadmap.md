!# Seasons Plugin – Roadmap

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

## Phase 1a: Snow Growth – Caching & Plugin‑Schnee (Refactoring) ❄️
> **Abgelöstes Konzept:** `Plannung/snow-growth-concept.md`
> **Auslöser:** Phase 1 Platzierung/Schmelze funktioniert unzuverlässig.
> **Ziel:** Platzierung und Höhenwachstum strikt trennen. Chunk-Cache mit Plugin‑ vs. Naturschnee-Unterscheidung. Kein Nachbar‑Check mehr beim Wachstum.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 1a.1 | ChunkCacheEntry + SnowAccumulator umbauen | `ChunkCacheEntry.java`, `SnowAccumulator.java` (Refactor) | [ ] |
| 1a.2 | scanChunkColumns mit HeightMap MOTION_BLOCKING | `SnowAccumulator.java`, alte `findColumnGround` entfernen | [ ] |
| 1a.3 | getOrComputeCache + TTL | `SnowAccumulator.java`, `ChunkCacheEntry.java` | [ ] |
| 1a.4 | processChunk (Platzierung) umbauen + grow entfernen | `SnowAccumulator.java` (processChunk neu, grow raus) | [ ] |
| 1a.5 | growSnowInChunk implementieren | `SnowAccumulator.java` (neue Methode) | [ ] |
| 1a.6 | BlockListener + SeasonChangeEvent → Cache-Invalidierung | `listener/BlockEventListener.java` (neu/erweitern), `listener/SeasonChangeListener.java` | [ ] |
| 1a.7 | ChunkCacheStore – JSON-Persistenz (Base64) | `ChunkCacheStore.java` (neu) | [ ] |
| 1a.8 | Neue growth.* + growth.cache.* Config-Einträge | `config.yml`, `WeatherConfig.java`, `ConfigManager.java` | [ ] |
| 1a.9 | Alte Config-Werte + Methoden aufräumen | `min-neighbors-for-growth` entfernen, `enoughNeighborsSnowOrBlocked` entfernen | [ ] |
| 1a.10 | Summary-Log um Cache-Stats erweitern | `SnowAccumulator.java` (Logging) | [ ] |
| 1a.11 | Build & Deploy + Funktionstest auf Server | — | [ ] |

**Done‑Definition Phase 1a:**
- [ ] `ChunkCacheEntry` hält `pluginSnowHeight` und `naturalSnowHeight` sauber getrennt
- [ ] `scanChunkColumns` nutzt HeightMap MOTION_BLOCKING + 1‑Block‑Fallback
- [ ] `processChunk` platziert nur noch (kein grow), `growSnowInChunk` nur Wachstum
- [ ] `isFullyGrown` Chunks werden komplett übersprungen (0ms)
- [ ] Cache wird bei BlockBreak/Place, SeasonChange und ChunkUnload korrekt invalidiert
- [ ] JSON-Persistenz lädt und speichert Chunk-Cache asynchron
- [ ] Alle neuen Werte per Config steuerbar
- [ ] Summary-Log zeigt Cache-Hits/Misses/FullyGrown
- [ ] Keine NMS/Reflection

---

## Phase 1b: Snow Melting – Nur Plugin-Schnee schmelzen ☀️
> **Abgelöstes Konzept:** `Plannung/snow-melting-concept.md`
> **Auslöser:** Phase 1 Schmelze per Random‑Tick unzuverlässig; Vanilla-Schnee soll bleiben.
> **Ziel:** Nur Plugin‑eigenen Schnee in Nicht‑Winter‑Saisons schmelzen, Layer by Layer. Cache aus Phase 1a mitnutzen.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 1b.1 | SnowMeltManager-Klasse erstellen | `SnowMeltManager.java` (neu) | [ ] |
| 1b.2 | processMeltChunk implementieren | `SnowMeltManager.java` | [ ] |
| 1b.3 | Saison-Check in tick(): Winter → Growth, sonst → Melt | `SeasonsPlugin.java` (tick erweitern) | [ ] |
| 1b.4 | only-plugin-snow Config + melt.* Config | `config.yml`, `WeatherConfig.java`, `ConfigManager.java` | [ ] |
| 1b.5 | Alte Melt-Logik aus SnowAccumulator entfernen/migrieren | `SnowAccumulator.java` (Cleanup) | [ ] |
| 1b.6 | Summary-Log für Melt + Cache-Stats | `SnowMeltManager.java` (Logging) | [ ] |
| 1b.7 | Build & Deploy + Funktionstest auf Server | — | [ ] |

**Done‑Definition Phase 1b:**
- [ ] Nur `pluginSnowHeight > 0` schmilzt; natürlicher Vanilla-Schnee bleibt
- [ ] Schmelze läuft nur in Frühling/Sommer/Herbst, nicht im Winter
- [ ] Pro Scan genau 1 Layer pro Spalte abgebaut
- [ ] `only-plugin-snow: true` als Default, Legacy-Mode per Config möglich
- [ ] Cache aus Phase 1a wird geteilt (ConcurrentHashMap)
- [ ] Summary-Log zeigt Melt-Statistiken
- [ ] Keine NMS/Reflection

---

## Phase 2: Visual Seasons – Foliage Tints 🍂
> **Konzept:** `Plannung/visual-seasons-concept.md`
> **Ziel:** Immersive, rein plugin-basierte saisonale Laub-/Gras-Farben. Biome-Tinting per NMS-Packet-Overrides. Alles client-seitig – kein Modding, kein Resource-Pack-Zwang. Frost-Effekte sind bewusst ausgeklammert und werden in Phase 2b als reines Tint-Lerp + Partikel-System umgesetzt.
> **Wichtig:** Erst in Phase 2 sind NMS/Reflection erlaubt (Paper-Adapter).

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
| 2.4 | Polish & Testing | `visual.yml` Feintuning, Test-Matrix, `docs/` | [ ] |

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
- [ ] `NmsAdapter` abstrahiert Paper-Versionen; Fallback bei nicht unterstützter Version
- [ ] Im Herbst leuchten Birken orange, Eichen rot, Dark-Forest gelb
- [ ] Im Frühling Kirsch-Biome rosa / intensiver
- [ ] Im Winter alles braun/grau
- [ ] Im Sommer Vanilla-Farben
- [ ] Alle Farben über `foliage_tints.yml` konfigurierbar
- [ ] Keine Konflikte mit Snow Growth/Melting aus Phase 1
- [ ] Performance: <5% Tick-Auslastung durch Visual-System

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
- [ ] Keine Konflikte mit Snow Growth/Melting aus Phase 1

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