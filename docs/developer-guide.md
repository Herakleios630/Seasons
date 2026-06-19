# Seasons Plugin – Developer Guide

> Struktur, Schichten und Datenmodelle für die Weiterarbeit. Kurzreferenz für KI und Entwickler.

---

## 1. Überblick

Das Seasons Plugin verleiht der Overworld einen Jahreszeiten‑Kreislauf. Es ist strikt **schichtenorientiert** aufgebaut:

```
Listener → Services → Events
Commands → Services
Config → Services (Injection)
```

- **Listener** behandeln Minecraft‑Events und delegieren an Services
- **Services** enthalten die Geschäftslogik und feuern Custom Events
- **Commands** sind dünne Facaden ohne Logik
- **Config** wird zentral geladen und an Services durchgereicht
- **Persistenz** speichert den Year‑Start‑Offset

---

## 2. Paketstruktur

```
de.ajsch.seasons/
├── SeasonsPlugin.java              # Plugin‑Basis, Service‑Bootstrap
├── season/                         # KERN: Jahreszeiten‑Berechnung
│   ├── Season.java                 # Enum SPRING/SUMMER/FALL/WINTER
│   ├── SeasonClock.java            # Tag aus FullTime, Season‑Wechsel
│   ├── SeasonConfig.java           # Config‑Wrapper: Jahreslänge etc.
│   └── SeasonChangeEvent.java      # Custom Event
├── temperature/                    # Temperatur‑Modell
│   ├── TemperatureCalculator.java  # Sinuskurve
│   ├── TemperatureConfig.java      # Amplituden, Offsets
│   └── BiomeTemperature.java       # Biome → Kategorie
├── weather/                        # Wetter‑Interception
│   ├── WeatherInterceptor.java     # Regen→Schnee (Hybrid)
│   ├── SnowAccumulator.java        # Schnee‑Layer
│   ├── WeatherConfig.java          # Freeze‑Threshold etc.
│   └── PrecipitationCategory.java  # CAN_FREEZE/NO_FREEZE/NO_RAIN
├── foliage/                        # PHASE 2
│   ├── FoliageTintManager.java
│   └── FoliageConfig.java
├── effects/                        # PHASE 3
│   ├── SeasonalEffect.java         # Interface
│   ├── EffectScheduler.java
│   ├── TemperatureEffect.java
│   ├── MistEffect.java
│   └── IceEffect.java
├── commands/                       # Commands (Facaden)
│   ├── SeasonCommand.java
│   └── SeasonAdminCommand.java
├── config/                         # Config‑Management
│   ├── ConfigManager.java          # YAML‑Loader, Reload
│   ├── YamlFile.java               # YAML‑Wrapper
│   └── ResourceCopier.java         # JAR→plugins/ Kopie
├── persistence/
│   └── SeasonsDataStore.java       # Year‑Offset, Auto‑Save
└── listener/                       # Event‑Listener
    ├── PlayerJoinListener.java
    ├── PlayerMoveListener.java     # Biom‑Wechsel → Wetter
    └── SnowListener.java           # SnowFormEvent → Höhe
```

---

## 3. Schichten‑Regeln

1. **Listener rufen NUR Services auf** – niemals direkt Configs oder Modelle
2. **Commands sind dünne Facaden** – keine Geschäftslogik
3. **Services holen Config** über `ConfigManager`‑Injection
4. **SeasonClock** ist alleinige Quelle für die aktuelle Season
5. **Kein Service hält mutable State** außer Persistenz‑Daten
6. **Keine Java‑Datei > 400 Zeilen** – ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
7. **Biome nie hardcoden** – immer über Config‑Kategorien
8. **Phase 1: Kein NMS** – nur Paper‑API
