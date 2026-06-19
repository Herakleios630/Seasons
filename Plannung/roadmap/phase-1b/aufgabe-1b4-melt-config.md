---
title: "Arbeitsauftrag: only-plugin-snow + melt.* Config"
quelle: "roadmap.md → Phase 1b, Sprint 1b.4"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: only-plugin-snow + melt.* Config

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die neuen Config-Einträge für das Melt-System anlegen:
- `weather.snow.melting.enabled` (boolean, default: true)
- `weather.snow.melting.layers-per-scan` (int, default: 4)
- `weather.snow.melting.only-plugin-snow` (boolean, default: true) – **Hauptschalter**

Diese Werte in `ConfigManager` und `WeatherConfig` als Getter verfügbar machen und in `config.yml` eintragen.

## Vorbedingungen
- Sprints 1b.1–1b.3 sind umgesetzt
- `SnowMeltManager` nutzt diese Config-Werte

## Aktuelles Ergebnis
- `WeatherConfig` hat nur `getMeltThreshold()`, `getMeltSpeed()`, `getMeltChunksPerTick()` – alte Melt-Config
- Neue Melt-Werte existieren noch nicht

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | Neue Sektion `weather.snow.melting:` einfügen |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Neue Getter: `isSnowMeltingEnabled()`, `getMeltLayersPerScan()`, `isOnlyPluginSnow()` |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Neue Getter delegieren an ConfigManager |
| `src/main/java/de/ajsch/seasons/weather/SnowMeltManager.java` | Dummy-Werte durch Config-Getter ersetzen |

## Erbetene Hilfe

1. **`config.yml` erweitern:**
   Unter `weather.snow:` neue Sektion einfügen:
   ```yaml
   melting:
     enabled: true
     layers-per-scan: 4
     only-plugin-snow: true
   ```

2. **`ConfigManager` erweitern:**
   ```java
   public boolean isSnowMeltingEnabled() {
       return config.getBoolean("weather.snow.melting.enabled", true);
   }
   public int getMeltLayersPerScan() {
       return config.getInt("weather.snow.melting.layers-per-scan", 4);
   }
   public boolean isOnlyPluginSnow() {
       return config.getBoolean("weather.snow.melting.only-plugin-snow", true);
   }
   ```

3. **`WeatherConfig` erweitern:**
   ```java
   public boolean isSnowMeltingEnabled() { return config.isSnowMeltingEnabled(); }
   public int getMeltLayersPerScan() { return config.getMeltLayersPerScan(); }
   public boolean isOnlyPluginSnow() { return config.isOnlyPluginSnow(); }
   ```

4. **`SnowMeltManager` anpassen:**
   - `layersPerScan` aus `weatherConfig.getMeltLayersPerScan()` holen
   - `enabled`-Check aus `weatherConfig.isSnowMeltingEnabled()`
   - `onlyPluginSnow`-Check in `meltColumn()` einbauen: wenn `false`, ALLEN Schnee schmelzen (Legacy-Mode)

5. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
6. Deployment: Config per SCP kopieren: `scp src\main\resources\config.yml mc@10.0.0.86:"..."`

## Technische Randbedingungen
- **YAML-Edit:** `single_find_and_replace` verwenden, nicht `filesystem_write_file`
- **Keine Magic Numbers**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `docs/developer-guide.md` (neue Config-Einträge dokumentieren)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1b.4 abhaken)