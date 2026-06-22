---
title: "Arbeitsauftrag: ChunkCacheManager extrahieren"
quelle: "roadmap.md → Phase 1.5, Sprint 1.5.1"
related-roadmap: "roadmap.md → Phase 1.5: Snow System 2.0 – Refactoring → Sprint 1.5.1"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: ChunkCacheManager extrahieren

**Quelle:** roadmap.md → Phase 1.5, Sprint 1.5.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Aus `SnowAccumulator.java` (~650 Z.) die gesamte Cache-Verwaltungslogik in eine neue Klasse `ChunkCacheManager.java` auslagern. Gleichzeitig die zwei Kernfehler Fix #1 (Cache-Drift) und Fix #5 (markDirty nach Scan) beheben.

**Was wird ausgelagert:**
- `ConcurrentHashMap<String, ChunkCacheEntry> chunkCache`
- `getOrComputeCache(Chunk)` inkl. TTL-Prüfung und Temperatur-Toleranz
- `scanChunkColumns(Chunk, ChunkCacheEntry)` – der komplette 256-Spalten-Scan
- `buildCacheKey(World, int, int)` und `buildCacheKey(Chunk)`
- `invalidateChunk(Chunk)` → wird öffentlich, heißt `invalidate(String key)`
- `clearCache()` → wird öffentlich
- `getCachedEntry(Chunk)` → wird `get(String key)`
- `getCache()` → wird `getCacheMap()` (für `ChunkCacheStore`)

**Was bleibt in `SnowAccumulator`:**
- Die Hilfsmethoden `isSnowCapable`, `isColumnPlaceable`, `isReplaceablePlant`, `getGroundBlock`, `removePlantAt`, `getMaxSnowHeight` sowie die Material-Sets `REPLACEABLE_PLANTS` und `DOUBLE_PLANTS`
- `tryPlaceColumn` (wird später in SnowPlacer wandern)
- `processChunk`, `growSnowInChunk`, `processMeltChunk` (wandern in Folgeschritte)

**Fix #1 (Cache-Drift):** In `scanChunkColumns` Zeile `entry.pluginSnowHeight[idx] = oldPlugin;` ändern zu `entry.pluginSnowHeight[idx] = (byte) physicalSnow;`

**Fix #5 (markDirty nach Scan):** Am Ende von `scanChunkColumns`, nach `entry.lastUpdated = System.currentTimeMillis();`, einfügen:
```java
if (entry.totalPluginSnowColumns > 0 && chunkCacheStore != null) {
    chunkCacheStore.markDirty(key);
}
```
Dafür braucht `ChunkCacheManager` eine Referenz auf `ChunkCacheStore` (oder ein `Consumer<String>` dirtyCallback).

## Aktuelles Ergebnis
`SnowAccumulator` hat ~650 Zeilen und vereint Cache, Scan, Platzierung, Wachstum und Schmelze. Cache-Drift besteht (Fix #1), fehlende Persistenz nach Scan (Fix #5). Der Cache funktioniert grundsätzlich, aber ist schwer testbar und wartbar.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheManager.java` | **NEU** – Extraktion aus SnowAccumulator |
| `src/main/java/de/ajsch/seasons/weather/SnowAccumulator.java` | Auslagerung der Cache-Methoden, Delegation an ChunkCacheManager |
| `src/main/java/de/ajsch/seasons/weather/ChunkCacheEntry.java` | Unverändert |
| `src/main/java/de/ajsch/seasons/persistence/ChunkCacheStore.java` | Muss ChunkCacheManager.getCacheMap() statt SnowAccumulator.getCache() nutzen |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Konstruktor-Aufrufe anpassen (ChunkCacheStore bekommt andere Map-Referenz) |

## Erbetene Hilfe
1. `ChunkCacheManager.java` erstellen mit:
   - Konstruktor: `(JavaPlugin, SeasonClock, TemperatureCalculator, WeatherConfig, ChunkCacheStore)` – SeasonClock und TemperatureCalculator werden für scanChunkColumns benötigt
   - `private final ConcurrentHashMap<String, ChunkCacheEntry> chunkCache = new ConcurrentHashMap<>();`
   - `getOrComputeCache(Chunk)` (komplett aus SnowAccumulator kopieren, TTL + Temp-Toleranz)
   - `scanChunkColumns(Chunk, ChunkCacheEntry)` (komplett aus SnowAccumulator kopieren)
   - `buildCacheKey(World, int, int)`, `buildCacheKey(Chunk)` (statisch)
   - `invalidate(String key)`, `clearCache()`
   - `get(String key)` (ehemals getCachedEntry)
   - `getCacheMap()` (für ChunkCacheStore)
   - Fix #1: `oldPlugin` → `(byte) physicalSnow`
   - Fix #5: `markDirty(key)` am Ende von scanChunkColumns wenn `totalPluginSnowColumns > 0`

2. `SnowAccumulator.java` anpassen:
   - Feld `ChunkCacheManager cacheManager` statt `ConcurrentHashMap<String, ChunkCacheEntry> chunkCache`
   - Konstruktor um `ChunkCacheManager`-Parameter erweitern (oder intern erzeugen)
   - `getOrComputeCache`, `scanChunkColumns`, `invalidateChunk`, `clearCache`, `getCachedEntry`, `getCache`, `buildCacheKey` entfernen – Aufrufe auf `cacheManager` umleiten
   - `invalidateChunk(Chunk)` → `cacheManager.invalidate(buildCacheKey(chunk))` (die buildCacheKey-Methode bleibt als private Delegation oder wird direkt durch cacheManager.buildCacheKey ersetzt)
   - `getCache()` → `cacheManager.getCacheMap()`

3. `ChunkCacheStore.java` anpassen:
   - Der Konstruktor erwartet aktuell `ConcurrentHashMap<String, ChunkCacheEntry> cache` – das bleibt, aber die Map kommt jetzt von `cacheManager.getCacheMap()`

4. `SeasonsPlugin.java` anpassen:
   - `ChunkCacheManager` instanziieren (vor `SnowAccumulator`)
   - `SnowAccumulator` bekommt den `ChunkCacheManager` statt eigenem Cache
   - `ChunkCacheStore` bekommt `cacheManager.getCacheMap()` statt `snowAccumulator.getCache()`
   - `snowAccumulator.setChunkCacheStore()` → entweder direkt im Konstruktor setzen oder ChunkCacheManager gibt den Store weiter

5. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
6. Kein Deployment in diesem Schritt (erst nach 1.5.7)

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Deployment-Befehle nur posten, nicht selbst ausführen**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`