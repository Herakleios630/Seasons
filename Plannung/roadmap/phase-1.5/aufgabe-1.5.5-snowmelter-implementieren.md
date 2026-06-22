---
title: "Arbeitsauftrag: SnowMelter implementieren"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.5"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.5"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: SnowMelter implementieren

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die alte `processMeltChunk`-Logik aus `SnowAccumulator` komplett ersetzen durch eine neue Klasse `SnowMelter.java`, die korrekt über den Cache arbeitet. Dies behebt Fix #2 (Schmelzen umgeht Cache), Fix #6 (nur Plugin-Schnee schmelzen) und bereitet Laub-Wiederherstellung vor (Fix #7 – Vorbereitung).

**Alte Logik (wird verworfen):**
- Iteriert über alle Spalten, sucht `Material.SNOW` per `getHighestBlockAt`
- Prüft Temperatur
- Manipuliert Blöcke direkt ohne Cache-Update
- Schmilzt auch Vanilla-Schnee

**Neue Logik:**
1. ChunkCacheEntry aus Cache holen
2. Wenn `cache.totalPluginSnowColumns == 0` → sofort return (0ms)
3. Alle Spalten mit `pluginSnowHeight[idx] > 0` sammeln
4. Shufflen
5. `meltPerTick` Spalten abarbeiten:
   - `cache.pluginSnowHeight[idx]--`
   - `cache.totalPluginSnowColumns--` (wenn auf 0 fällt)
   - Physische Welt synchronisieren:
     - Neue Gesamthöhe = `naturalSnowHeight[idx] + pluginSnowHeight[idx]`
     - Wenn Gesamthöhe == 0: Block auf Air setzen
     - Wenn Gesamthöhe > 0: Snow-Layer auf Gesamthöhe setzen
   - Wenn `pluginSnowHeight[idx] == 0` und naturalSnowHeight == 0: `cache.snowCovered--`
6. `markDirty(chunkKey)` wenn `melted > 0`

**Wichtig:** `naturalSnowHeight[idx]` bleibt unverändert. Nur `pluginSnowHeight[idx]` wird dekrementiert (Fix #6).

**Laub-Wiederherstellung (Vorbereitung für Fix #7):**
Wenn `pluginSnowHeight[idx]` auf 0 fällt und `cache.removedPlants` Einträge für diese Spalte hat, die Pflanzen wiederherstellen. Da `removedPlants` aktuell noch nicht im ChunkCacheEntry persistiert wird, wird dieser Teil nur als TODO-Kommentar notiert und in einem späteren eigenen Fix umgesetzt.

**Konfiguration:**
- `melt-threshold`: Temperatur, ab der geschmolzen wird (default 0.5)
- `melt-speed`: Wie viele Layer pro Spalte pro Melt-Durchlauf (default 1)
- `melt-chunks-per-tick`: Wie viele Chunks pro Tick (default 8)
- `melt-layers-per-chunk`: Wie viele Spalten pro Chunk (default 4) – neuer Config-Wert!

## Aktuelles Ergebnis
`processMeltChunk` arbeitet direkt auf der physischen Welt, ohne Cache-Update. Plugin-Schnee und Vanilla-Schnee werden gleichermaßen geschmolzen. `totalPluginSnowColumns` wird nicht aktualisiert. Nach dem Schmelzen ist der Cache stale.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/SnowMelter.java` | **NEU** – Cache-basierte Schmelzlogik |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | processMeltChunk entfernen, Aufruf umleiten |
| `src/main/java/de/ajsch/seasons/weather/WeatherConfig.java` | Neuen Getter `getMeltLayersPerChunk()` hinzufügen |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Neuen Config-Wert lesen |
| `src/main/resources/config.yml` | `weather.snow.melting.layers-per-chunk: 4` hinzufügen |

## Erbetene Hilfe
1. `config.yml`: Neuen Eintrag `weather.snow.melting.layers-per-chunk: 4` hinzufügen
2. `ConfigManager.java`: `getMeltLayersPerChunk()` → liest `weather.snow.melting.layers-per-chunk` (default 4)
3. `WeatherConfig.java`: `getMeltLayersPerChunk()` → delegiert an ConfigManager
4. `SnowMelter.java` erstellen:
   - Konstruktor: `(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)`
   - `processMeltChunk(Chunk chunk, ChunkCacheEntry cache, String chunkKey)`:
     - Prüft `cache.totalPluginSnowColumns > 0`
     - Sammelt Indizes mit `pluginSnowHeight[idx] > 0`
     - Shuffled, arbeitet `meltLayersPerChunk` Spalten ab
     - Dekrementiert `pluginSnowHeight[idx]`, synchronisiert physische Welt
     - `markDirty` bei `melted > 0`
   - Private Methode `syncColumn(Chunk, int x, int z, int newTotal)` für Block-Updates
5. `SnowAccumulator.java`:
   - Feld `SnowMelter snowMelter` hinzufügen
   - `processMeltChunk` komplett entfernen
   - `meltSnow(World)` umbauen: iteriert Chunks, ruft `cacheManager.get(key)` auf, dann `snowMelter.processMeltChunk(chunk, cache, key)`
6. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
7. Kein Deployment in diesem Schritt

## Technische Randbedingungen
- **Keine NMS/Reflection in Phase 1**
- **Java-Dateien ≤ 400 Zeilen**
- **Build nach jeder Änderung**
- **Kein Deployment ohne Nutzer-Freigabe**