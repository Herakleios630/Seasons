# Seasons – Architektur-Konzept

> Alle getroffenen Entscheidungen, Begruendungen und Ausbau-Pläne. Dieses Dokument ist die zentrale Referenz vor und waehrend der Implementierung.

---

## 1. Kernidee

Ein Paper-Plugin, das der Overworld einen echten Jahreszeiten-Kreislauf verleiht: 365 Minecraft-Tage = 1 Jahr mit Fruehling, Sommer, Herbst und Winter. Im Winter schneit es in passenden Biomen, im Herbst faerbt sich das Laub, extreme Temperaturen beeinflussen Spieler.

**Leitprinzip:** Alles ueber Configs steuerbar. Keine Magic Numbers. Kein Hardcoding pro Biom.

---

## 2. Architektur-Entscheidungen

### 2.1 Season-Berechnung (Determinismus)
- Season wird **ausschliesslich** aus `world.getFullTime()` und dem gespeicherten `yearStartOffset` berechnet.
- **Kein mutable State** fuer die aktuelle Season.
- Season-Wechsel werden als `SeasonChangeEvent` (Custom Bukkit Event) gefeuert.
- `SeasonClock` ist die alleinige Quelle fuer "welche Season ist gerade".

### 2.2 Weather Interception
- **Phase 1:** Hybrid-Loesung
  - Spieler in CAN_FREEZE-Biom + Winter: `player.setPlayerWeather(WeatherType.CLEAR)` + eigene Schnee-Partikel
  - Spieler verlaesst CAN_FREEZE-Biom: `player.resetPlayerWeather()`
  - Welt-Wetter bleibt Vanilla (RAIN/CLEAR/THUNDER)
- **Phase 4:** Pro-Chunk-Wetter als Abloesung
  - Regen-Partikel pro Chunk durch Schnee ersetzen
  - Spieler in der Wueste sieht dann im Plains-Biom in der Ferne Schnee
  - Kein `setPlayerWeather()` mehr noetig

### 2.3 Biom-Erkennung
- **Kein Hardcoding.** Alle Biome werden ueber `precipitation_categories.yml` in CAN_FREEZE, NO_FREEZE oder NO_RAIN kategorisiert.
- Unbekannte Biome (Custom Datapacks) → Default = CAN_FREEZE
- Neue Biome ohne Code-Aenderung hinzufuegbar

### 2.4 Temperatur-Modell
- Sinuskurve: `T = amplitude * cos(2π * (day - phaseShift) / yearLength)`
- Phase 1: Eine globale Amplitude + Biome-Offset. Tageszeit-Amplitude als zweiter Faktor.
- Phase 3: Erweiterung fuer Effekte (Hunger, Speed, Eis)
- Keine echten °C – relative Werte (Standard -0.5 bis 0.8)

### 2.5 Schnee-Akkumulation
- Nur in **geladenen Chunks** mit Spielern
- Scan-Intervall: 600 Ticks (30s), max 4 Chunks pro Tick
- `SnowFormEvent.setNewHeight()` mit temperaturabhaengiger Maximalhoehe
- Vanilla-Max: 2 Layer. Extra: +1 Layer pro -0.2°C unter Freeze-Threshold

### 2.6 Schnee-Schmelze
- **Random-Tick** in geladenen Chunks bei positiver Temperatur
- Kein globaler BlockScan – nur in aktuell geladenen Chunks
- In ungeladenen Chunks passiert nichts ("wenn's keiner sieht, ist's egal")

### 2.7 Eis-Effekt
- **Phase 3**
- Primaer stehendes Gewaesser (Seen, Teiche)
- Bei Extremtemperaturen auch Wasserfaelle/Flüsse
- Tauen bei Fruehling/Random-Tick

### 2.8 Config-Migration
- Fehlende Config-Felder: Default aus JAR nehmen, **kein** automatisches Ueberschreiben
- Warnung loggen wenn Felder fehlen
- Kein Kommentarerhalt im ersten Wurf (SnakeYAML loescht Kommentare)

### 2.9 Persistenz
- `seasons_data.yml` speichert: `year-start-offset`, `last-season`, `current-day`
- Auto-Save alle 5 Minuten (konfigurierbar)
- Year-Start-Offset wird beim ersten Start auf aktuelles `world.getFullTime()` gesetzt

### 2.10 Test-Mode
- `season.debug-mode: true` → 20-Tage-Jahr
- Bei aktiviertem Test-Mode loggt jeder Season-Wechsel
- `/season speed <mult>` zusaetzlich als Admin-Override

---

## 3. Paketstruktur

```
de.ajsch.seasons/
├── SeasonsPlugin.java              (Plugin-Basis, Service-Bootstrap)
│
├── season/                         (Jahreszeiten-Kern)
│   ├── Season.java                 (Enum: SPRING, SUMMER, FALL, WINTER)
│   ├── SeasonClock.java            (Tag aus FullTime, Season-Wechsel-Erkennung)
│   ├── SeasonConfig.java           (Jahreslänge, Tagesbereiche)
│   └── SeasonChangeEvent.java      (Custom Event: alte → neue Season)
│
├── temperature/                    (Temperatur-Modell)
│   ├── TemperatureCalculator.java  (Sinuskurve aus Tag + Offset)
│   ├── TemperatureConfig.java      (Amplituden, Biome-Offsets)
│   └── BiomeTemperature.java       (Biome → Temperatur-Kategorie)
│
├── weather/                        (Wetter-Interception)
│   ├── WeatherInterceptor.java     (Regen→Schnee, Hybrid-Lösung)
│   ├── SnowAccumulator.java        (Schnee-Layer-Management)
│   ├── WeatherConfig.java          (Schnee-Höhen, Freeze-Map)
│   └── PrecipitationCategory.java  (CAN_FREEZE, NO_FREEZE, NO_RAIN)
│
├── foliage/                        (Phase 2 – Laubfärbung)
│   ├── FoliageTintManager.java
│   └── FoliageConfig.java
│
├── effects/                        (Phase 3 – Spieler-Effekte)
│   ├── SeasonalEffect.java         (Interface)
│   ├── EffectScheduler.java
│   ├── TemperatureEffect.java
│   ├── MistEffect.java
│   └── IceEffect.java
│
├── commands/                       (Commands)
│   ├── SeasonCommand.java          (/season info)
│   └── SeasonAdminCommand.java     (/season debug/skip/set/speed)
│
├── config/                         (Config-Management)
│   ├── ConfigManager.java          (YAML-Loader, Reload)
│   ├── YamlFile.java               (YAML-Wrapper)
│   └── ResourceCopier.java         (Default-Configs kopieren)
│
├── persistence/                    (Persistenz)
│   └── SeasonsDataStore.java       (Year-Start-Offset)
│
└── listener/                       (Event-Listener)
    ├── PlayerJoinListener.java     (Season-Info, Wetter-Sync)
    ├── PlayerMoveListener.java     (Biom-Wechsel → Wetter anpassen)
    └── SnowListener.java           (SnowFormEvent → Höhensteuerung)
```

---

## 4. Schichten-Regeln

1. **Listener rufen NUR Services auf**, nie direkt Modelle oder Configs
2. **Commands sind duenne Facaden**, keine Geschaeftslogik
3. **Services holen ihre Config** ueber ConfigManager-Injection
4. **SeasonClock** ist einzige Quelle fuer "welche Season ist gerade"
5. **Kein Service haelt mutable State** ausser Persistenz-Daten
6. **Keine Java-Datei ueber 800 Zeilen**

---

## 5. Config-Struktur

### config.yml
- `season`: year-length-days, start-season, start-day, tagesbereiche pro Season
- `temperature`: min-winter, max-summer, biome-offsets, day-night-amplitude
- `weather.snow`: freeze-threshold, max-natural-height, height-per-cold
- `weather.enabled`: true/false (Plugin-Konflikt-Schutz)
- `precipitation-categories.file`: Verweis auf precipitation_categories.yml
- `persistence`: file, save-interval-minutes
- `commands`: enabled, debug-requires-op
- `season.debug-mode`: true/false (20-Tage-Jahr)
- `performance`: chunk-scan-interval-ticks, max-snow-chunks-per-tick

### precipitation_categories.yml
- `categories.CAN_FREEZE`: Liste aller Biome mit moeglichem Schnee
- `categories.NO_FREEZE`: Liste aller Biome ohne Schnee (Regen normal)
- `categories.NO_RAIN`: Liste aller Biome ohne Niederschlag (Wueste etc.)

### seasons_data.yml (auto-generiert)
- `year-start-offset`: FullTime-Wert bei Plugin-Installation
- `last-season`: Letzte bekannte Season
- `current-day`: Aktueller Jahrestag

---

## 6. Datenfluss

```
world.getFullTime()
    │
    ▼
SeasonsDataStore.getYearStartOffset()
    │
    ▼
SeasonClock.calculateDayOfYear()        → 0..364
    │
    ▼
SeasonClock.getCurrentSeason()          → Season Enum
    │
    ▼
TemperatureCalculator.calculate(day, biome)
    │   ├── Sinuskurve aus Tag
    │   └── Biome-Offset aus Config
    ▼
effektiveTemperatur (float, z.B. -0.3)
    │
    ▼
WeatherInterceptor.onWeatherChange()
    │   Wenn Season == WINTER
    │   UND Biom in CAN_FREEZE
    │   UND effektiveTemperatur < freezeThreshold
    │     → player.setPlayerWeather(CLEAR)
    │     → Schnee-Partikel spawnen
    │
    ▼
SnowAccumulator.onSnowForm()
    │   Hoehe = map(temperatur → 1..maxLayer)
    │   Nur in geladenen Chunks mit Spielern
```

---

## 7. Offene Punkte fuer spaetere Phasen

### 7.1 Ungeladene Chunks und Schnee
- **Neu generierte Chunks:** Koennten beim Chunk-Generation-Event den Schnee-Zustand basierend auf aktueller Season/Temperatur initial setzen. WorldGen-Hook noetig.
- **Bereits generierte, ungeladene Chunks:** Async Chunk-Loader, der im Winter periodisch ungeladene Chunks laedt, Schnee anwendet und wieder entlaedt. Nur sinnvoll wenn Server-Performance es zulaesst.
- **Phase 1:** Beide Punkte zurueckgestellt. Nur geladene Chunks.

### 7.2 Seasonale Crop-Growth
- Pflanzenwachstum saisonal modulieren (langsamer im Winter, schneller im Fruehling)
- Hook: `BlockFertilizeEvent`, `CropGrowthEvent` (falls Paper das hergibt)

### 7.3 Seasonale Mob-Spawns
- Bestimmte Mobs saisonal haeufiger/seltener
- Beispiel: Husk in Wueste nur im Sommer, Stray nur im Winter

### 7.4 Tageslaengen
- Im Winter kuertzere Tage, im Sommer laengere (wie Vanilla-Plus-Anmutung)
- Hook: `PlayerJoinEvent` → `player.setPlayerTime()` relativ zu Season

---

## 8. Build & Deploy

- **Lokal:** `./gradlew shadowJar` → `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Server:** `/home/mc/crafty-4/servers/.../plugins/Seasons-0.1.0-SNAPSHOT.jar`
- **Configs liegen unter:** `plugins/Seasons/config.yml` etc.
- **Kein Bridge-Dienst** noetig (anders als VillagerAI)