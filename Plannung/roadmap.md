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
| 1.1 | Gradle & Bootstrap | `build.gradle.kts`, `settings.gradle.kts`, `plugin.yml`, `SeasonsPlugin.java` | [ ] |
| 1.2 | Config & Persistenz | `ConfigManager.java`, `ResourceCopier.java`, `SeasonsDataStore.java`, `config.yml` | [ ] |
| 1.3 | Season & Clock | `Season.java`, `SeasonClock.java`, `SeasonConfig.java` | [ ] |
| 1.4 | Temperatur | `TemperatureCalculator.java`, `TemperatureConfig.java`, `BiomeTemperature.java`, `precipitation_categories.yml` | [ ] |
| 1.5 | Weather Interception | `PrecipitationCategory.java`, `WeatherConfig.java`, `WeatherInterceptor.java` | [ ] |
| 1.6 | Schnee-Akkumulation | `SnowAccumulator.java`, `SnowListener.java` | [ ] |
| 1.7 | Events & Commands | `SeasonChangeEvent.java`, `SeasonCommand.java`, `SeasonAdminCommand.java` | [ ] |
| 1.8 | Integrationstest | `PlayerJoinListener.java`, voller Durchlauf, Bugfixing | [ ] |

**Done‑Definition Phase 1:**
- [ ] Server startet sauber, lädt `config.yml`, `precipitation_categories.yml` & `seasons_data.yml`
- [ ] Jahr taktet durch (365 Tage), `/season` zeigt aktuellen Tag + Season + verbleibende Tage
- [ ] Im Winter fällt in CAN_FREEZE‑Biomen sichtbar Schnee statt Regen
- [ ] In Wüste, Badlands, Savanna, Jungle bleibt Regen / kein Schnee
- [ ] Schnee-Layer stapeln temperaturabhängig (kälter = mehr)
- [ ] Bei Season‑Wechsel geht Schneefall‑Eignung sauber zurück; Schnee schmilzt per Random‑Tick
- [ ] Persistenz über Server‑Restarts erhalten
- [ ] Keine NMS/Reflection-Nutzung
- [ ] Commands `/season`, `/season debug`, `/season skip`, `/season set`, `/season speed` funktionieren

---

## Phase 2: Laub & Farben 🍂 🌸
**Ziel:** Herbst‑Laubfärbung (orange/rot/gelb) und Frühling‑Kirsch‑Rosa. Biome‑Tints werden per Packet‑Overrides an Clients gesendet.

| Sprint | Feature | Datei(en) | Status |
|---|---|---|---|
| 2.1 | NMS‑Adapter für Packet-Overrides | `NmsAdapter.java`, Version‑Abstraktion | [ ] |
| 2.2 | FoliageTintManager – Packet‑Logik | `FoliageTintManager.java` | [ ] |
| 2.3 | Seasonale Farb‑Config + Biome‑Mapping | `foliage_tints.yml`, `FoliageConfig.java` | [ ] |
| 2.4 | Senden der Tints bei Player‑Join & Season‑Change | `PlayerJoinListener.java` (erweitern), `SeasonChangeListener.java` | [ ] |
| 2.5 | Test‑Matrix (alle 4 Seasons durchschalten) | visuelle Prüfung | [ ] |

**Done‑Definition Phase 2:**
- [ ] Im Herbst leuchten Birken orange, Eichen rot, Dark‑Forest gelb
- [ ] Im Frühling Kirsch‑Biome rosa / intensiver
- [ ] Im Winter alles braun/grau
- [ ] Im Sommer Vanilla‑Farben
- [ ] Farben über `foliage_tints.yml` frei einstellbar
- [ ] Farbwechsel erfolgt soft (über ~3 Ingame-Tage linear interpoliert)

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