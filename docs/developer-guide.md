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
- **Persistenz** speichert den Chunk-Cache (JSON/Base64) und den Year‑Start‑Offset

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
│   ├── SnowAccumulator.java        # Schnee‑Layer (Platzierung, Wachstum, Schmelze)
│   ├── ChunkCacheManager.java      # Cache‑Verwaltung (Chunk‑Cache, scanChunkColumns, TTL)
│   ├── ChunkCacheEntry.java        # Per‑Chunk Schneehöhen + Metadaten
│   ├── WeatherConfig.java          # Freeze‑Threshold etc.
│   └── PrecipitationCategory.java  # CAN_FREEZE/NO_FREEZE/NO_RAIN
├── visual/                         # PHASE 2: Laub & Farben
│   ├── FoliageTintManager.java     # Per-Spieler Biome-Tint-Berechnung + Chunk-Biome-Caching
│   ├── VisualSeasonManager.java    # Koordinator: Join/SeasonChange/Tick
│   ├── VisualConfig.java           # foliage_tints.yml Wrapper
│   ├── ColorCalculator.java        # Farb-Interpolation
│   └── nms/
│       ├── NmsAdapter.java         # NMS-Abstraktion
│       └── NmsAdapter_v1_21_5.java # Packet-Override für 1.21.4/1.21.5
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
│   ├── SeasonsDataStore.java       # Year‑Offset, Auto‑Save
│   └── ChunkCacheStore.java        # JSON-Persistenz für Chunk-Cache (Base64)
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

### 3.0 ChunkCacheManager – Extraktion aus SnowAccumulator

Die gesamte Cache‑Verwaltung wurde in `ChunkCacheManager.java` ausgelagert. Er enthält:
- `ConcurrentHashMap<String, ChunkCacheEntry> chunkCache`
- `getOrComputeCache(Chunk)` mit TTL‑ und Temperatur‑Toleranz‑Prüfung
- `scanChunkColumns(Chunk, ChunkCacheEntry)` mit Fix #1 (Cache‑Drift) und Fix #5 (markDirty nach Scan)
- Key‑Builder (`buildCacheKey`), Invalidation, Clear, Cache‑Counter
- `markDirty(String)` delegiert an `ChunkCacheStore`
- Chicken‑Egg‑Resolution: `setChunkCacheStore()` wird nachträglich von `SeasonsPlugin` aufgerufen

`SnowAccumulator` delegiert sämtliche Cache‑Zugriffe an den `ChunkCacheManager` (ca. 200 Zeilen ausgelagert).

### 3.1 SnowAccumulator – Scan & Cache

#### scanChunkColumns(Chunk)
Baut einen frischen `ChunkCacheEntry` für alle 256 Spalten eines Chunks auf.
- Nutzt `World.getHighestBlockYAt(wx, wz, HeightMap.MOTION_BLOCKING)` für O(1) Top-Block-Lookup
- 1-Block-Fallback: wenn der Top-Block nicht `isSolid()` ist (Pflanze, etc.), wird der Block darunter als Ground verwendet
- Misst aktuelle Schneehöhe über den Top-Block und den Ground-Block
- Erster Scan: `naturalSnowHeight = aktuelle Höhe`, `pluginSnowHeight = 0`
- Setzt `snowCapable`, `snowCovered`, `snowBelowMax` und Temperatur-Intervall `[tempLevelMin, tempLevelMax]`
- Timestamp `lastUpdated` via `System.currentTimeMillis()`

#### getGroundBlock(World, wx, wz)
Ersetzt die alte `findColumnGround`-Methode. Nutzt MOTION_BLOCKING für den höchsten Block und steigt bei nicht-soliden Blöcken (Wasser, Pflanze) einen Block ab. Gibt bei Wasser/Lava `null` zurück und zählt `totalSkippedOceans` hoch.

#### getOrComputeCache(Chunk)
Zentrale Cache-Lookup-Methode mit TTL- und Temperatur-Toleranz-Prüfung:
1. Key bauen: `worldUID:chunkX:chunkZ`
2. TTL prüfen: `(System.currentTimeMillis() - entry.lastUpdated) / 1000 > cacheTTLSeconds` → MISS
3. Temperatur-Toleranz: Aktuelles Temp-Level an repräsentativer Spalte (8,8) vs `[tempLevelMin, tempLevelMax]`
4. Bei MISS: `scanChunkColumns(chunk)`, `cache.put(key, entry)`, return entry
5. Zähler `cacheHits`/`cacheMisses` werden im Summary-Log ausgegeben
6. TTL ist konfigurierbar via `weather.snow.growth.cache.ttl-seconds` (Default 30s), toleranz via `weather.snow.growth.cache.temp-level-tolerance` (Default 0)

### 3.2 SnowAccumulator – Platzierung & Wachstum (getrennt)

#### accumulateSnow(World)
1. `getOrComputeCache(chunk)` – Cache-Hit/Miss wie in 3.1
2. `cache.isFullyGrown()` → Chunk überspringen (0ms, alle 256 Spalten haben Schnee)
3. `cache.isSaturated()` → `growSnowInChunk(chunk, cache)` – existierenden Schnee wachsen lassen
4. Sonst: `processChunk(chunk, cache)` – neue Schnee-Layer platzieren

#### processChunk(Chunk, ChunkCacheEntry) – NUR Platzierung
- Arbeitet nur auf Spalten mit `pluginSnowHeight==0 && naturalSnowHeight==0`
- Baut Liste der platzierbaren Spalten aus dem Cache (kein direkter Block-Lookup nötig)
- Shuffled die Liste, arbeitet `layersPerScan` Spalten pro Tick ab
- Setzt Schnee-Layer 1, updatet `cache.pluginSnowHeight[index]=1`, `cache.snowCovered++`, `cache.totalPluginSnowColumns++`
- **Kein `tryGrowColumn` mehr** – Wachstum ist ausgegliedert in eigene Methode (1a.5)

#### growSnowInChunk(Chunk, ChunkCacheEntry) – NUR Wachstum
- Wird nur auf gesättigten Chunks aufgerufen (`cache.isSaturated()`, d.h. `snowBelowMax==0`)
- Sammelt Spalten mit `pluginSnowHeight > 0`, deren Gesamthöhe unter dem Temperatur-Limit liegt
- Misst das Temperatur-Limit pro Spalte via `tempCalc.calculate(doy, biome)`
- Shuffled die Liste, arbeitet `growthLayersPerScan` (Config, Default 2) Spalten pro Tick ab
- Pro Spalte: `pluginSnowHeight[index]++`
- Setzt den physischen Snow-Layer via `snow.setLayers()` oder wandelt zu SNOW_BLOCK+neuem Layer
- Aktualisiert `cache.snowBelowMax` wenn die Spalte das Temperatur-Limit erreicht
"- Liefert nach Abschluss eine Info-Log-Zeile (eine pro Aufruf)

#### Summary-Log
Nach jeweils `weather.snow.growth.summary-interval-scans` Durchläufen wird ein Summary-Log ausgegeben:
```
[SnowAcc] summary: placed=%d grown=%d | cache: %d hits, %d misses, %d fullyGrown
```
- **placed**: Neue Schnee-Layer (aus `processChunk` und `tryPlaceColumn`)
- **grown**: Gewachsene Layer (aus `growSnowInChunk`)
- **hits**: Cache-Treffer in diesem Zyklus
- **misses**: Cache-Fehlgriffe (TTL abgelaufen oder Temperatur außerhalb Toleranz)
- **fullyGrown**: Chunks, die wegen `isFullyGrown()` übersprungen wurden

### 3.3 ChunkCacheStore – JSON-Persistenz

#### Formata
```json
{
  \"cacheVersion\": 1,
  \"chunks\": {
    \"worldUID:chunkX:chunkZ\": {
      \"snowCapable\": 220,
      \"snowCovered\": 218,
      \"snowBelowMax\": 45,
      \"tempLevelMin\": -3,
      \"tempLevelMax\": -1,
      \"updated\": 1713123456789,
      \"pluginSnow\": \"base64...\",
      \"naturalSnow\": \"base64...\",
      \"blockedColumns\": \"base64...\"
    }
  }
}
```

#### Lebenszyklus
1. **`load()`** beim Server-Start: liest `chunk_cache.json`, prüft `cacheVersion` → bei Match Base64-dekodieren und in `ConcurrentHashMap` laden
2. **`markDirty(key)`** bei jeder Cache-Invalidierung (BlockBreak/Place, ChunkUnload, SeasonChange)
3. **Async-Task** alle `cache.persistence.save-interval-seconds` (Default 5s): nur dirty Einträge in JSON schreiben
"4. **`save()`** bei `onDisable`: synchroner Full-Save aller Cache-Einträge

---

## 4. Phase 2 – Visual Seasons (Foliage Tints)

### 4.1 Architektur

Das Visual-System überschreibt die Biome-Farben (Foliage + Grass) clientseitig per NMS-Packet-Override.
Jeder Spieler erhält bei Join, Season-Wechsel und periodisch korrigierte Chunk-Daten, die seine
Sicht auf die Welt färben – ohne Modding, ohne Resource-Pack-Zwang.

```
VisualSeasonManager (Koordinator)
├── PlayerJoinEvent    → sendet Initial-Tints
├── PlayerQuitEvent    → räumt PlayerVisualState auf
├── SeasonChangeEvent  → startet Übergang (transitionProgress=0)
└── onTick (alle 200 ticks) → transitionProgress fortschreiben,
     FoliageTintManager.updateAllOnlinePlayers()
          │
          └── FoliageTintManager (pro Spieler)
               ├── Chunk-Biome-Caching (ConcurrentHashMap<Long, Set<Biome>>)
               ├── Biome-Sampling (alle 4 Blöcke pro Chunk)
               ├── ColorCalculator.calculateSeasonalColor(...)
               └── NmsAdapter.sendBiomeTint(player, biomeKey, foliage, grass)
```

### 4.2 Datenmodelle

**PlayerVisualState** (innere Klasse in `VisualSeasonManager`):
- `lastUpdateTick`: Zeitstempel der letzten Aktualisierung
- `isActive`: Spieler ist online und empfängt Updates

**Chunk-Biome-Cache** (in `FoliageTintManager`):
- Key: `long chunkKey = ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL)`
- Value: `Set<Biome>` – einmalig beim ersten Scan ermittelt
- Invalidation bei `ChunkUnloadEvent`

### 4.3 Config: `foliage_tints.yml`

```yaml
foliage:
  transition-days: 4.0          # Dauer des Farbübergangs in Minecraft-Tagen
  update-interval-ticks: 200     # Wie oft während des Übergangs aktualisieren (200=10s)
  seasons:
    SUMMER:
      default-tint: \"0x7C9E4F\"   # Vanilla-Grün
      overrides:                  # Pro-Biome Overrides
        BIRCH: \"0xA0C05F\"
    FALL:
      default-tint: \"0xC68A3F\"   # Orange-Braun
      overrides:
        OAK: \"0xD45A2E\"
        DARK_OAK: \"0x9B3A1C\"
        BIRCH: \"0xF0C040\"
        CHERRY: \"0xE8706A\"
    WINTER:
      default-tint: \"0xA8B0B8\"   # Grau-Blau
    SPRING:
      default-tint: \"0x8FD15F\"   # Frisches Grün
      overrides:
        CHERRY: \"0xFF99CC\"       # Kirschblüte rosa
```

### 4.4 NMS-Abstraktion

- `NmsAdapter`: abstrakte Basisklasse mit `sendBiomeTint(Player, biomeKey, foliage, grass)`
- `NmsAdapter_v1_21_5`: Implementierung für Paper 1.21.x
- Factory-Methode `NmsAdapter.create(plugin)` detektiert Server-Version
- Bei nicht unterstützter Version: `UnsupportedOperationException` → Visual-Features deaktiviert

### 4.5 Performance

- Chunk-Biome-Caching verhindert wiederholtes Abtasten desselben Chunks
- `biomeSampleStep = 4` → 16 statt 256 Block-Lookups pro Chunk
- Update-Intervall: 200 ticks (10s) während Übergängen, sonst keine Ticks
- Messlatte: <5% Tick-Auslastung durch Visual-System""
