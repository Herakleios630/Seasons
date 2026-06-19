---
title: "Arbeitsauftrag: SnowMeltManager-Klasse erstellen"
quelle: "roadmap.md → Phase 1b, Sprint 1b.1"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: SnowMeltManager-Klasse erstellen

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die neue Klasse `SnowMeltManager` erstellen. Sie ist das Pendant zum `SnowAccumulator` und ausschließlich für die Schneeschmelze zuständig.
- Nur Plugin-eigenen Schnee schmelzen (`pluginSnowHeight > 0`)
- Layer by Layer: Pro Scan 1 Layer pro Spalte abbauen
- Nutzt denselben Chunk-Cache (`ConcurrentHashMap`) wie `SnowAccumulator`
- Läuft NUR in Frühling/Sommer/Herbst

## Vorbedingungen
- **Phase 1a muss abgeschlossen sein** – `ChunkCacheEntry`, `ConcurrentHashMap<String, ChunkCacheEntry>`, `getOrComputeCache()` und `scanChunkColumns()` müssen existieren
- Im Projektstamm `src/main/java/de/ajsch/seasons/weather/` existiert bereits `SnowAccumulator.java` (420 Zeilen) – dies NICHT anfassen, wird erst in 1b.5 bereinigt
- `WeatherConfig.java` wird erst in Sprint 1b.4 erweitert – vorübergehend mit Dummy-Werten arbeiten oder die Werte direkt aus `ConfigManager` beziehen

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowMeltManager.java` | **NEU** – Hauptklasse für Schneeschmelze |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Nicht anfassen (Referenz für Struktur, ChunkCache shared) |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Neue Getter in Sprint 1b.4 |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Saison-Check in Sprint 1b.3 |

## Erbetene Hilfe

1. **Klasse `SnowMeltManager` anlegen:**
   ```java
   package de.ajsch.seasons.weather;
   
   public class SnowMeltManager {
       // Fields: plugin, clock, tempCalc, weatherConfig, cacheRef (ConcurrentHashMap)
       // Constructor analog zu SnowAccumulator + cache parameter
       // tick() → accumulateMelt(world)
   }
   ```

2. **Konstruktor mit folgenden Parametern:**
   - `JavaPlugin plugin`
   - `SeasonClock clock`
   - `TemperatureCalculator tempCalc`
   - `WeatherConfig weatherConfig`
   - `ConcurrentHashMap<String, ChunkCacheEntry> sharedCache` (Referenz aus SnowAccumulator)
   - `int scanInterval` (ticks)
   - `int maxChunksPerTick` (= `layers-per-scan` aus Melt-Config)

3. **`accumulateMelt(World world)` implementieren:**
   - Nur geladene Chunks iterieren
   - `getOrComputeCache(chunk)` → wenn `!cache.hasPluginSnow()` → continue (0ms, nichts zu schmelzen)
   - `processMeltChunk(chunk, cache)` aufrufen (Implementierung folgt in Sprint 1b.2)

4. **Summary-Counter vorbereiten:**
   - `totalMelted` (Layer), `totalPluginColumnsRemaining`
   - Alle 50 Scans loggen (wie SnowAccumulator)

5. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`

## Technische Randbedingungen
- **Keine Magic Numbers:** Jeder numerische Wert über Config
- **Biome nie hardcoden:** Über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschließlich aus `world.getFullTime()` + `yearStartOffset`
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** `SnowMeltManager` sollte unter 300 bleiben
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)

## Sync nach Abschluss
- `README.md` (neue Klasse dokumentieren)
- `docs/developer-guide.md` (Schichten-Impact)
- `docs/handover.md` (Status)
- `Plannung/roadmap.md` (1b.1 abhaken)