# Snow System 2.0 – Mini-Konzept

> Fokus: Neustrukturierung des Schnee-Managements aus `SnowAccumulator` (498 Zeilen, 5 Verantwortlichkeiten)
> in ein modulares System mit klaren Verantwortlichkeiten, korrekter Cache-Semantik und Temperatur-basierter
> Aktivierung.

---

## 1. Problemstellung

`SnowAccumulator.java` vereint aktuell:
- Tick-Loop (Orchestrierung)
- Cache-Management (`getOrComputeCache`, `scanChunkColumns`, `invalidate`)
- Schnee-Platzierung (`processChunk`, `tryPlaceColumn`)
- Schnee-Wachstum (`growSnowInChunk`)
- Schnee-Schmelze (`processMeltChunk`)

Dazu kommen 7 analysierte Kernfehler:

| # | Fehler | Ursache |
|---|--------|---------|
| 1 | **Cache-Drift** | `pluginSnowHeight[idx] = oldPlugin` statt `= physicalSnow` in `scanChunkColumns` – Cache glaubt an mehr Schnee als physisch da ist |
| 2 | **Schmelzen umgeht Cache** | `processMeltChunk` manipuliert Blöcke direkt, aktualisiert weder `pluginSnowHeight` noch ruft `markDirty` auf |
| 3 | **Season-basierter Trigger** | Melt läuft in ALLEN Nicht-Winter-Seasons, selbst wenn es in kalten Biomen noch friert |
| 4 | **Toter Config-Wert** | `saturationThreshold: 0.95` wird nie gelesen, `isSaturated()` nutzt immer 100% |
| 5 | **Fehlende Persistenz** | `markDirty` nur bei `placed>0`/`grown>0` – initialer Scan mit Plugin-Schnee persistiert nie |
| 6 | **Keine Plugin/Natural-Unterscheidung beim Schmelzen** | `processMeltChunk` schmilzt ALLES – auch Vanilla-Schnee |
| 7 | **Cache-Invalidation fehlt bei Season-Wechsel** | `clearCache()` wirft alles weg, aber persistierte Einträge werden beim nächsten Scan mit falschen pluginSnowHeight-Werten wieder eingelesen |

---

## 2. Neue Architektur

### 2.1 Klassenübersicht

```
weather/
├── WeatherInterceptor.java      (unverändert: Partikel, Hybrid-Lösung)
├── WeatherConfig.java           (erweitert: saturationThreshold lesen)
├── PrecipitationCategory.java   (unverändert)
│
├── SnowAccumulator.java         (Schlank: Orchestrator, ~80 Z.)
├── ChunkCacheManager.java       (Extrahiert: Cache-Logik, ~150 Z.)
├── SnowPlacer.java              (Extrahiert: Platzierung, ~120 Z.)
├── SnowGrower.java              (Extrahiert: Wachstum, ~100 Z.)
├── SnowMelter.java              (Neu: Schmelzen via Cache, ~100 Z.)
│
├── ChunkCacheEntry.java         (Unverändert bis auf Config-Fix)
├── ChunkCacheEntrySerializer.java (Hilfsklasse: toJson/fromJson aus ChunkCacheStore ausgelagert)
└── TickDiagnostics.java         (Unverändert)

persistence/
├── ChunkCacheStore.java         (Unverändert: JSON-Persistenz mit dirty-System)
└── SeasonsDataStore.java        (Unverändert)
```

### 2.2 Verantwortlichkeiten

| Klasse | Zuständigkeit | Quelle |
|--------|---------------|--------|
| `SnowAccumulator` | Tick-Loop, Welt-Iteration, Temperatur-basierte Modus-Wahl (Growth vs. Melt), Summary-Log | Neu |
| `ChunkCacheManager` | `getOrComputeCache`, `scanChunkColumns`, TTL, Temperatur-Toleranz, `invalidate` | Extrahiert aus SnowAccumulator |
| `SnowPlacer` | `processChunk`, `tryPlaceColumn`, Pflanzen-Tracking | Extrahiert aus SnowAccumulator |
| `SnowGrower` | `growSnowInChunk`, Sättigungs-Prüfung via Config-Threshold | Extrahiert aus SnowAccumulator |
| `SnowMelter` | Click-by-Click-Schmelze über Cache, Laub-Wiederherstellung | Neu implementiert |
| `ChunkCacheEntry` | Datenmodell: `pluginSnowHeight`, `naturalSnowHeight`, Zähler | Unverändert bis auf `isSaturated`-Fix |

---

## 3. Datenfluss

```
SnowAccumulator.start()  → Tick-Loop (alle 20 Ticks)
│
├─ for each World:
│   └─ for each loaded Chunk (max maxChunksPerTick):
│       │
│       ├─ ChunkCacheManager.getOrComputeCache(chunk)  → ChunkCacheEntry
│       │   │
│       │   ├─ Cache HIT? → return entry
│       │   ├─ Cache MISS? → scanChunkColumns(chunk, oldEntry)
│       │   │   ├─ pluginSnowHeight[idx] = physicalSnow (FIX #1)
│       │   │   ├─ naturalSnowHeight[idx] = physicalSnow wenn plugin==0
│       │   │   ├─ snowCapable, snowCovered, etc.
│       │   │   └─ markDirty(key)  (FIX #5)
│       │   └─ chunkCache.put(key, newEntry)
│       │
│       ├─ Temperatur an repräsentativer Spalte berechnen
│       │
│       ├─ if (temp < freezeThreshold && world.hasStorm()):
│       │   ├─ if (cache.snowCovered < cache.snowCapable * saturationThreshold):
│       │   │   └─ SnowPlacer.processChunk(chunk, cache)  → placed=N
│       │   │       ├─ tryPlaceColumn(wx,wz) → blockData setzen
│       │   │       ├─ cache.pluginSnowHeight[idx] = 1
│       │   │       ├─ cache.snowCovered++, cache.totalPluginSnowColumns++
│       │   │       ├─ removedPlants tracken (FIX #7)
│       │   │       └─ markDirty(key) wenn placed>0
│       │   └─ else:
│       │       └─ SnowGrower.growSnowInChunk(chunk, cache)  → grown=N
│       │           ├─ cache.pluginSnowHeight[idx]++
│       │           └─ markDirty(key) wenn grown>0
│       │
│       └─ if (temp >= meltThreshold):
│           └─ SnowMelter.processMeltChunk(chunk, cache)  → melted=N
│               ├─ cache.pluginSnowHeight[idx] dekrementieren
│               ├─ Block.setBlockData() auf physischer Welt
│               ├─ wenn pluginSnowHeight[idx]==0: removedPlants wiederherstellen
│               └─ markDirty(key) wenn melted>0 (FIX #2, #6)
│
└─ Summary-Log alle summaryIntervalScans
```

**Prinzip: IMMER zuerst den Cache manipulieren, dann mit der physischen Welt synchronisieren. Nie umgekehrt.**

---

## 4. Cache-Semantik (korrigiert)

### 4.1 Wahrheitsquelle
- **Cache (`ConcurrentHashMap`) ist die alleinige Wahrheitsquelle** für "welche Spalte hat wie viel plugin/natural Schnee"
- Die physische Welt wird als Folge von Cache-Änderungen synchronisiert
- Bei Unsicherheit (stale) → `scanChunkColumns` baut den Cache aus der physischen Welt neu auf

### 4.2 pluginSnowHeight
- **pluginSnowHeight[idx] = physisch vorhandener Schnee, wenn von uns platziert**
- Bei Platzierung: `pluginSnowHeight[idx] = 1`
- Bei Wachstum: `pluginSnowHeight[idx]++`
- Bei Schmelze: `pluginSnowHeight[idx]--`
- Bei Re-Scan mit oldPlugin>0 und physicalSnow>0: `pluginSnowHeight[idx] = (byte) physicalSnow` (nicht oldPlugin!)

### 4.3 naturalSnowHeight
- Schnee, der bereits vor Plugin-Start existierte oder von Vanilla-Mechaniken erzeugt wurde
- Wird NUR in `scanChunkColumns` gesetzt, nie von Platzierung/Wachstum/Schmelze verändert
- Plugin adoptiert natural-Schnee beim ersten Wachstum (`justAdopted = true`)

### 4.4 Persistenz
- `markDirty(key)` wird aufgerufen bei:
  - `scanChunkColumns` nach jedem neuen Eintrag MIT `pluginSnowHeight > 0`
  - `processChunk` wenn `placed > 0`
  - `growSnowInChunk` wenn `grown > 0`
  - `processMeltChunk` wenn `melted > 0`
- `ChunkCacheStore.startAsyncSaveTask()` schreibt alle dirty Einträge periodisch in `chunk_cache.json`

---

## 5. Korrekturen im Detail

### Fix #1: Cache-Drift
```java
// Alte Logik (scanChunkColumns):
if (physicalSnow > 0 && oldPlugin > 0) {
    entry.pluginSnowHeight[idx] = oldPlugin; // ← fehlerhaft
}

// Neue Logik:
if (physicalSnow > 0 && oldPlugin > 0) {
    entry.pluginSnowHeight[idx] = (byte) physicalSnow; // physischer Ist-Zustand
}
```

### Fix #2: Schmelzen über Cache (SnowMelter)
```java
// Alte Logik (processMeltChunk):
Block highest = world.getHighestBlockAt(cx+x, cz+z);
if (highest.getType() != Material.SNOW) continue;
// ... direkt Block manipulieren, kein Cache-Update

// Neue Logik:
// 1. Nur Spalten mit pluginSnowHeight[idx] > 0
// 2. cache.pluginSnowHeight[idx]--
// 3. Block.setBlockData() (oder Air) auf physischer Welt
// 4. markDirty(key)
```

### Fix #3: Temperatur-basierter Trigger
```java
// Alte Logik (start()):
if (clock.getCurrentSeason() == Season.WINTER) {
    accumulateSnow(world);
} else {
    meltSnow(world); // ← immer in Nicht-Winter, egal wie kalt
}

// Neue Logik:
double temp = tempCalc.calculate(doy, representativeBiome);
if (temp < freezeThreshold && world.hasStorm()) {
    // Growth/Platzierung
} else if (temp >= meltThreshold) {
    // Schmelze (nur wenn es zu warm ist)
}
// Sonst: nichts tun (z.B. kalter Herbsttag ohne Regen)
```

### Fix #4: saturationThreshold aus Config
```java
// WeatherConfig.java (neu):
public double getSaturationThreshold() {
    return config.getDouble("weather.snow.growth.saturation-threshold", 0.95);
}

// ChunkCacheEntry.java:
public boolean isSaturated(double threshold) {
    return snowCapable > 0 && snowCovered >= (int)(snowCapable * threshold);
}
```

### Fix #5: markDirty nach jedem Scan
```java
// ChunkCacheManager.scanChunkColumns() – am Ende:
entry.lastUpdated = System.currentTimeMillis();
if (entry.totalPluginSnowColumns > 0 && chunkCacheStore != null) {
    chunkCacheStore.markDirty(key);
}
return entry;
```

### Fix #6: Nur Plugin-Schnee schmelzen
```java
// SnowMelter.processMeltChunk():
// Iteriert nur über Spalten mit pluginSnowHeight[idx] > 0
// Schmilzt genau 1 Layer pro betroffener Spalte
// naturalSnowHeight bleibt unangetastet
```

### Fix #7: Laub-Tracking
```java
// SnowPlacer – bei Pflanzenentfernung:
List<Block> removedPlants = new ArrayList<>();
// ... bei removePlantAt(): removedPlants.add(plant)
// In ChunkCacheEntry: public final List<RemovedPlant> removedPlants = new ArrayList<>();

// SnowMelter – nach Schmelze (pluginSnowHeight[idx]==0):
// removedPlants wiederherstellen (setType() zurück)
```

---

## 6. Config-Erweiterungen

### Bestehende Config-Werte (weiterhin genutzt)
```yaml
weather.snow.freeze-threshold: 0.0
weather.snow.melt-threshold: 0.5
weather.snow.melt-speed: 1
weather.snow.layers-per-scan: 8
weather.snow.growth.layers-per-scan: 2
weather.snow.growth.saturation-threshold: 0.95  # ← wird jetzt tatsächlich gelesen
weather.snow.growth.cache.ttl-seconds: 30
weather.snow.growth.cache.save-interval-seconds: 5
performance.chunk-scan-interval-ticks: 20
performance.max-snow-chunks-per-tick: 32
performance.melt-chunks-per-tick: 8
performance.summary-interval-scans: 50
cache.persistence.file: chunk_cache.json
cache.persistence.version: 1
```

### Entfallende Config-Werte
```yaml
# Werden entfernt, da sie nicht mehr verwendet werden:
weather.snow.max-natural-height     ← Nur in Phase 1, jetzt temperatur-abhängig
weather.snow.height-per-cold        ← Nur in Phase 1
weather.snow.first-snow-min-layers  ← Nicht mehr relevant
weather.snow.first-snow-max-layers  ← Nicht mehr relevant
weather.snow.max-attempts-multiplier ← Nicht mehr relevant
weather.snow.max-down-search        ← Nicht mehr relevant
```

---

## 7. Abgrenzung zu anderen Systemen

### Was dieses Refactoring NICHT ändert
- **WeatherInterceptor**: Die Hybrid-Lösung (setPlayerWeather + Partikel) bleibt unverändert. Erst Phase 4 (Pro-Chunk-Wetter) würde hier eingreifen.
- **SeasonClock**: Unverändert. Season-Berechnung bleibt deterministisch.
- **Temperatur-Modell**: Unverändert. `TemperatureCalculator`, `BiomeTemperature`, `TemperatureConfig` bleiben wie sie sind.
- **Commands**: Unverändert.
- **SeasonsDataStore**: Unverändert.
- **ChunkCacheStore**: Persistenz-Mechanismus bleibt gleich, wird nur häufiger aufgerufen (weil `markDirty` jetzt überall korrekt).

### Vorbereitung für zukünftige Phasen
- **Phase 3 (Effekte)**: Temperatur-Effekte können auf `TemperatureCalculator` aufsetzen, den `SnowAccumulator` aber ignorieren.
- **Phase 4 (Pro-Chunk-Wetter)**: `WeatherInterceptor` müsste angepasst werden, aber das Schnee-System bleibt davon unberührt.
- **Block-Wärmequellen** (deine Idee mit Fackeln/Öfen): Wenn `TemperatureCalculator` einen `getTemperatureAt(Block)`-Angebot bekommt, profitieren `SnowPlacer`, `SnowGrower` und `SnowMelter` automatisch davon.

---

## 8. Ablauf des Refactorings

### Phase 0: Konzept & Planung (dieses Dokument)
- [x] Fehleranalyse
- [x] Architektur-Skizze
- [x] Mini-Konzept

### Phase 1.5: Implementierung (7 Arbeitskarten)
1. **ChunkCacheManager extrahieren** → Datei
2. **saturationThreshold-Fix** → ChunkCacheEntry + WeatherConfig
3. **SnowPlacer extrahieren** → Datei
4. **SnowGrower extrahieren** → Datei
5. **SnowMelter neu schreiben** → Datei
6. **SnowAccumulator verschlanken** → Orchestrator
7. **Integration & Test** → Listener anpassen, Build, Deploy

---

## 9. Erfolgskriterien

Nach Abschluss des Refactorings:
- [ ] `SnowAccumulator.java` ≤ 100 Zeilen (nur Orchestrierung)
- [ ] Keine Klasse > 250 Zeilen (ChunkCacheManager = ~150, SnowPlacer = ~120, SnowGrower = ~100, SnowMelter = ~100)
- [ ] `chunk_cache.json` wird regelmäßig geschrieben und enthält korrekte `pluginSnowHeight`-Daten
- [ ] Logs zeigen: `alreadySnow` nahe 0, `placed` > 0 während Regen in kalten Gebieten
- [ ] Schmelzen funktioniert nur für Plugin-Schnee, Vanilla-Schnee bleibt
- [ ] `saturationThreshold: 0.95` wird wirksam (Wachstum startet bei 95% Sättigung)
- [ ] Kein Cache-Drift mehr: Nach Schmelze + Re-Scan sind pluginSnowHeight und physischer Schnee synchron
- [ ] Keine NMS/Reflection (Phase 1 Regel)
- [ ] Build erfolgreich, Server-Test bestanden