"# Seasons Plugin – Handover

> Status, offene Baustellen und Prioritäten für die nächste Arbeitssitzung.

---

## Aktueller Stand

- **Phase 1 MVP fertig** – Seasons, Temperatur, Schnee-Akkumulation, Commands
- **Phase 2-PRE abgeschlossen** – altes NMS-basiertes Visual-System komplett entfernt, Netty-Dependency gelöscht
- **Bugfix 0/X Placements abgeschlossen** – `SnowAccumulator` auf per-Column-Logik umgebaut
- **18 aktive Regeln**, Build erfolgreich
- **Phase 2.4 (Polish & Testing) in Arbeit** – Performance-Optimierung abgeschlossen, Dokumentation wird synchronisiert

---

## Zuletzt abgeschlossen

"### Phase 2.4: Chunk-Biome-Caching (2025-07-21)
- FoliageTintManager: ConcurrentHashMap für Chunk-Biome-Caching
- ChunkUnloadEvent-Listener zur Cache-Invalidation
- Biome-Sample-Schritt 4 (16 statt 256 Lookups pro Chunk)
- Build: BUILD SUCCESSFUL

### Bugfix: Eligible-Liste auf bebaubare Spalten einschränken (2025-07-11)"
- `ChunkCacheManager.isColumnPlaceable()` um `isReplaceablePlant()` erweitert (inkl. EnumSet – gleiche Logik wie SnowPlacer)
- `blockedColumns` in `scanChunkColumns()` gefüllt: wenn `isSnowCapable(ground)` aber `!isColumnPlaceable(aboveGround)` → `blockedColumns.set(idx)`
- `SnowPlacer.processChunk()`: Eligible-Liste filtert jetzt `!cache.blockedColumns.get(idx)`
- `ChunkCacheStore` persistiert `blockedColumns` (Base64 wie die Height-Arrays)
- `cache.version` von 1 auf 2 inkrementiert
- Build: `BUILD SUCCESSFUL`

### Phase 1.5, Sprint 1.5.2: saturationThreshold-Fix (2025-07-09)
- `ChunkCacheEntry.isSaturated(double threshold)` hinzugefügt: `snowCovered >= (int)(snowCapable * threshold)`
- Alte `isSaturated()` delegiert an `isSaturated(1.0)`
- `SnowAccumulator.accumulateSnow()`: `cache.isSaturated()` → `cache.isSaturated(weatherConfig.getSaturationThreshold())`
- Build: `BUILD SUCCESSFUL`, Shadow-JAR: `BUILD SUCCESSFUL`

### Phase 1.5, Sprint 1.5.1: ChunkCacheManager extrahieren
- `ChunkCacheManager.java` erstellt: Cache-Map, `getOrComputeCache`, `scanChunkColumns`, Key-Builder, `markDirty`
- Fix #1 (Cache-Drift): `oldPlugin` → `(byte) physicalSnow` in `scanChunkColumns`
- Fix #5 (markDirty nach Scan): `markDirty(key)` wenn `totalPluginSnowColumns > 0` am Ende von `scanChunkColumns`
- `SnowAccumulator` von ~650Z auf ~450Z reduziert: Cache, Scan, `getOrComputeCache` an `ChunkCacheManager` delegiert
- `ChunkCacheManager.setChunkCacheStore()` für Chicken-Egg-Resolution
- `BlockEventListener`: Nutzt jetzt `ChunkCacheManager` statt direkt `ChunkCacheStore`
- `SeasonsPlugin`: `ChunkCacheManager` vor `SnowAccumulator` und `ChunkCacheStore` instanziiert
- Build: `BUILD SUCCESSFUL`

### Bugfixes aus Phase-1a-Funktionstest (aufgabe-1a11-fixes) – Stand 2025-06-19
- Bug 2 (Pflanzen/Leaf Litter): LEAF_LITTER, PINK_PETALS, WILDFLOWERS, CACTUS_FLOWER zu REPLACEABLE_PLANTS hinzugefügt
- Bug 3a (isSaturated): Prüft jetzt `snowCovered >= snowCapable` (zählt nat. + Plugin-Schnee)
- Bug 3b (growSnowInChunk): Sammelt jetzt ALLE Spalten mit Schnee, nicht nur Plugin-Schnee. Spalten mit nur natürlichem Schnee werden beim ersten Grow adoptiert.
- SeasonChangeListener-NPE behoben (Reihenfolge in onEnable korrigiert)
- Build: BUILD SUCCESSFUL, steht zum Deployen bereit

### Phase 1a, Aufgabe 1a.9: Alte Config-Werte + Methoden aufräumen
- `min-neighbors-for-growth: 3` aus `config.yml` entfernt
- `getMinNeighborsForGrowth()` aus `ConfigManager` und `WeatherConfig` entfernt
- `enoughNeighborsSnowOrBlocked()` aus `SnowAccumulator` entfernt
- `tryGrowColumn()` und `growColumnSnow()` (tote Caller) ebenfalls entfernt
- `NEIGHBORS`-Array entfernt (wurde nur von `enoughNeighborsSnowOrBlocked` genutzt)
- Veralteten Kommentar in `tryPlaceColumn` bereinigt
- Nicht genutzten `ChunkCacheStore`-Import aus `SnowAccumulator` entfernt
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.8: Growth-Cache-Config-Einträge
- `config.yml`: Neue verschachtelte Sektion `weather.snow.growth` mit `layers-per-scan: 2`, `saturation-threshold: 0.95`, `growth.cache.temp-level-tolerance: 0`, `growth.cache.ttl-seconds: 30`, `growth.cache.save-interval-seconds: 5`
- `ConfigManager`: Pfad von `growthLayersPerScan` auf `weather.snow.growth.layers-per-scan` umgebogen; neue Getter `getSaturationThreshold()`, `getCacheTempLevelTolerance()`; `getCacheTTLSeconds()` von `performance.cache-ttl-seconds` auf `weather.snow.growth.cache.ttl-seconds` umgebogen; `getCacheSaveIntervalSeconds()` von `cache.persistence.save-interval-seconds` auf `weather.snow.growth.cache.save-interval-seconds` umgebogen
- `WeatherConfig`: Neue delegierende Getter `getSaturationThreshold()`, `getCacheTempLevelTolerance()`
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.7: ChunkCacheStore – JSON-Persistenz
- `ChunkCacheStore.java` erstellt (JSON-Persistenz mit Gson, Base64 für pluginSnow/naturalSnow Byte-Arrays)
- `ConfigManager` um `getCacheFile()`, `getCacheSaveIntervalSeconds()`, `getCacheVersion()` erweitert
- `SnowAccumulator.getCache()` gibt `ConcurrentHashMap<String, ChunkCacheEntry>` nach außen
- `SeasonsPlugin`: `chunkCacheStore.load()` in onEnable, `chunkCacheStore.save()` in onDisable, `startAsyncSaveTask()`
- `BlockEventListener`: `markDirty(key)` bei jeder Invalidierung
- `config.yml`: neue Sektion `cache.persistence` mit `file`, `save-interval-seconds`, `version`
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.5: growSnowInChunk implementieren
- `growSnowInChunk(Chunk, ChunkCacheEntry)`: Neue Methode für Wachstum auf gesättigten Chunks
  - Sammelt Spalten mit `pluginSnowHeight > 0` und Gesamthöhe unter Temperatur-Limit
  - Shuffled, `growthLayersPerScan` (Config, Default 2) pro Tick
  - Pro Spalte: `pluginSnowHeight[index]++`, physischer Layer erhöht oder zu SNOW_BLOCK+neuem Layer konvertiert
  - Aktualisiert `cache.snowBelowMax` wenn Spalte das Temperatur-Limit erreicht
- Config: `weather.snow.growth-layers-per-scan` (Default 2) in ConfigManager, WeatherConfig, config.yml
- Aufruf in `accumulateSnow` eingebaut (ersetzt TODO-Platzhalter)
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.4: processChunk (Platzierung) umbauen + grow entfernen
- `accumulateSnow`: Holt jetzt Cache → `isFullyGrown()` → skip, `isSaturated()` → (leer, TODO für 1a.5), sonst `processChunk(chunk, cache)`
- `processChunk(Chunk, ChunkCacheEntry)`: Nur noch Platzierung, kein `tryGrowColumn` mehr
  - Arbeitet nur auf Spalten mit `pluginSnowHeight==0 && naturalSnowHeight==0`
  - Baut Liste aus Cache (kein Block-Lookup), shuffled, `layersPerScan` pro Tick
  - Setzt Schnee-Layer 1, updatet `cache.pluginSnowHeight`, `cache.snowCovered`, `cache.totalPluginSnowColumns`
  - `enoughNeighborsSnowOrBlocked` in 1a.9 entfernt
- `ChunkCacheEntry`: Feld `totalPluginSnowColumns` hinzugefügt
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.1: ChunkCacheEntry + Cache in SnowAccumulator
- `ChunkCacheEntry`: Datenklasse mit `pluginSnowHeight`/`naturalSnowHeight` (256 Byte-Arrays) und Metadaten
- `SnowAccumulator`: `ConcurrentHashMap<String, ChunkCacheEntry> chunkCache` eingeführt
- `buildCacheKey()`: Key-Format `worldUID:chunkX:chunkZ`
- Build: `BUILD SUCCESSFUL`

### Phase 1a, Aufgabe 1a.2: scanChunkColumns mit HeightMap MOTION_BLOCKING
- `scanChunkColumns(Chunk)`: Scant alle 256 Spalten via `World.getHighestBlockYAt(wx,wz,MOTION_BLOCKING)` (O(1))
- `getGroundBlock(World,wx,wz)`: Ersetzt alte `findColumnGround`, 1-Block-Fallback bei nicht-soliden Top-Blöcken
- Temperatur-Intervall `[tempLevelMin, tempLevelMax]` im ChunkCacheEntry gesetzt
- Alte `findColumnGround` vollständig entfernt, einziger Aufrufer `tryPlaceColumn` auf `getGroundBlock` umgestellt
- Build: `BUILD SUCCESSFUL`

---

### Phase 1a, Aufgabe 1a.3: getOrComputeCache + TTL
- `getOrComputeCache(Chunk)` implementiert: TTL-Prüfung (30s default, config-gesteuert), Temperatur-Toleranz-Prüfung
- `cacheHits`/`cacheMisses` Zähler, Summary-Log erweitert
- `scanChunkColumns` Timestamp auf `System.currentTimeMillis()` umgestellt
- Config: `performance.cache-ttl-seconds` (Default 30) in ConfigManager, WeatherConfig, config.yml
- Build: Erfolgreich (wird im nächsten Schritt verifiziert)

## Nächste Prioritäten

1. **Phase 2.4 abschließen:** Build, Deployment, Server-Test
2. **Phase 2b (Frost System):** FrostConfig, FrostEffectManager, Integration in VisualSeasonManager
3. **Phase 1.5 fortsetzen:** SnowGrower (1.5.4), SnowMelter (1.5.5), Accumulator verschlanken (1.5.6), Integrationstest (1.5.7)

---

## Offene Baustellen

- [x] Phase 2.4 (Polish & Testing) – Chunk-Biome-Caching implementiert
- [ ] Phase 2.4 (Polish & Testing) – Build, Deploy, Server-Test ausstehend
- [ ] Phase 2b (Frost System) – komplett offen
- [x] 1.5.1 ChunkCacheManager extrahiert – `SnowAccumulator` jetzt ~450Z
- [x] Fix #1 (Cache-Drift) und Fix #5 (markDirty nach Scan) umgesetzt
- [x] 1.5.2 SaturationThreshold-Fix: `saturation-threshold` aus Config tatsächlich nutzen
- [x] 1.5.3 SnowPlacer extrahieren: `tryPlaceColumn` und Hilfsmethoden auslagern
- [ ] 1.5.4 SnowGrower extrahieren: `growSnowInChunk` auslagern
- [ ] 1.5.5 SnowMelter extrahieren: `meltSnow`+`processMeltChunk` auslagern
- [ ] 1.5.6 SnowAccumulator verschlanken: nach Extraktionen durchchecken, ggf. unter 400Z
- [ ] 1.5.7 Integrationstest auf Server
- [ ] Testplan noch nicht auf per-Column-Logik aktualisiert

---

## Wichtige Server-Pfade

- **Plugin-JAR:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` → `/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/`
- **Configs:** `plugins/Seasons/config.yml` (auto-kopiert aus JAR)
- **Crafty-Restart:** `sudo systemctl restart crafty`

---

## Deployment (nach diesem Bugfix)

```powershell
scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar\"
```

Danach Server neustarten und Logs beobachten.
"