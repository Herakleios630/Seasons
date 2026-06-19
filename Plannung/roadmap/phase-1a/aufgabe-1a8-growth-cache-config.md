---
title: "Arbeitsauftrag: Neue growth.* + growth.cache.* Config-Einträge"
quelle: "roadmap.md → Phase 1a, Sprint 1a.8"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: Neue growth.* + growth.cache.* Config-Einträge

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die neuen Config-Einträge für Growth und Cache anlegen:
- `weather.snow.growth.layers-per-scan` (int, default: 2)
- `weather.snow.growth.saturation-threshold` (double, default: 0.95)
- `weather.snow.growth.cache.temp-level-tolerance` (int, default: 0)
- `weather.snow.growth.cache.ttl-seconds` (int, default: 30)
- `weather.snow.growth.cache.save-interval-seconds` (int, default: 5)

Diese Werte in `ConfigManager`, `WeatherConfig` und `config.yml` verfügbar machen.

## Vorbedingungen
- Phase 1a Sprints 1a.1–1a.7 sind umgesetzt

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | Neue Sektion `weather.snow.growth:` und `growth.cache:` einfügen |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Neue Getter |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Neue Getter |

## Erbetene Hilfe

1. **`config.yml` erweitern:**
   Unter `weather.snow:` neue Sektion:
   ```yaml
   growth:
     layers-per-scan: 2
     saturation-threshold: 0.95
     cache:
       temp-level-tolerance: 0
       ttl-seconds: 30
       save-interval-seconds: 5
   ```

2. **`ConfigManager` erweitern:**
   - `getGrowthLayersPerScan()` → default 2
   - `getSaturationThreshold()` → default 0.95
   - `getCacheTempLevelTolerance()` → default 0
   - `getCacheTtlSeconds()` → default 30
   - `getCacheSaveIntervalSeconds()` → default 5

3. **`WeatherConfig` erweitern:**
   - Delegierende Getter für die neuen Config-Werte

4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
5. Deployment: Config per SCP kopieren

## Technische Randbedingungen
- **YAML-Edit:** `single_find_and_replace` verwenden
- **Keine Magic Numbers**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (neue Config-Einträge)
- `docs/handover.md`
- `Plannung/roadmap.md` (1a.8 abhaken)