# Snow Growth – Mini-Konzept (mit Caching + Plugin-Schnee-Unterscheidung)

**Ziel:** Platzierung/Ausbreitung und Höhenwachstum strikt trennen.
Erst wenn alle platzierbaren Spalten im Chunk Schnee haben, beginnt langsames Wachstum.
Nur Plugin-eigener Schnee wird verwaltet – natürlicher Vanilla-Schnee bleibt unangetastet.

---

## 1. Trennung der Concerns

| Modul | Wann | Was |
|-------|------|-----|
| `processChunk` (Platzierung) | Chunk nicht gesättigt | Shuffled `layersPerScan` Spalten, setzt Layer 1. **Kein grow mehr.** |
| `growSnowInChunk` (Wachstum) | Chunk gesättigt | Shuffled `growth-layers-per-scan` Plugin-Schnee-Spalten, wächst +1 Layer. Keine Nachbar-Prüfung. |
| nichts | `isFullyGrown == true` | Chunk komplett überspringen (0ms) |

**Platzierung und Wachstum laufen NUR im Winter.** Außerhalb des Winters übernimmt das Melt-System (siehe `snow-melting-concept.md`).

---

## 2. Plugin-Schnee-Markierung

**Entscheidung:** Kein PersistentDataContainer pro Block. Stattdessen speichert der Chunk-Cache pro Spalte (x,z):

- `naturalSnowHeight`: Baseline – wie viele Snow-Layer waren beim ersten Scan natürlich vorhanden
- `pluginSnowHeight`: Wie viele Snow-Layer wurden von uns zusätzlich platziert/gewachsen

**Bestimmung der Baseline beim Erst-Scan:**
1. Spalte scannen, aktuelle Schneehöhe ermitteln
2. Wenn Chunk vorher nie gecached war: `naturalSnowHeight = aktuelle Höhe`, `pluginSnowHeight = 0`
3. Bei späteren Invalidierungen (BlockBreak/Place, Temperatur): Aktuelle Höhe neu messen, **`naturalSnowHeight` explizit beibehalten**, `pluginSnowHeight = max(0, aktuelleHöhe - naturalSnowHeight)`. Das verhindert, dass abgebauter natürlicher Schnee fälschlich als Plugin-Schnee klassifiziert wird

**Speicherung:** `short[]` oder `byte[]` mit 256 Einträgen (Index = x*16 + z). Pro Eintrag 2 Bytes: 1 Byte natural, 1 Byte plugin → ~512 Bytes pro Chunk.

---

## 3. Chunk-Cache (RAM + JSON)

**Pro Chunk speichern wir:**
- `snowCapable`: Spalten, die Schnee tragen können (solider, voller Block)
- `snowCovered`: Davon haben aktuell Schnee (irgendeine Höhe, natural + plugin)
- `snowBelowMax`: Davon liegen unter dem aktuellen Grow-Limit (können noch wachsen)
- `pluginSnowHeight[]`: Plugin-Anteil pro Spalte (x*16+z)
- `naturalSnowHeight[]`: Natürlicher Anteil pro Spalte
- `tempLevelMin` / `tempLevelMax`: Temperatur-Intervall, für das der Cache gültig ist
- `lastUpdated`: Timestamp (TTL-Fallback)

**Ergebnis:**
- `isSaturated = snowCovered >= snowCapable * saturationThreshold`
- `isFullyGrown = snowBelowMax == 0 && snowCapable > 0`
- `totalPluginSnow = sum(pluginSnowHeight[])` → für Melt-System relevant
- `hasPluginSnow = totalPluginSnow > 0`

**Temperatur-Toleranz (spätere Tag/Nacht-Schwankung vorbereitet):**
- `getMaxSnowHeight` rechnet in 0.2°C-Schritten: `extra = (freezeThreshold - temp) / 0.2 * heightPerCold`
- Cache speichert Intervall `[floor(temp/0.2) - tolerance, floor(temp/0.2) + tolerance]`
- Nur wenn aktuelle Temperatur das Intervall verlässt: Cache invalidieren und neu scannen
- `tolerance` aus Config (Default 0 = kein Puffer, später auf z.B. 10 setzbar für ±2°C)

**Grow-Limit vs. existierender Schnee:**
- `getMaxSnowHeight(temp)` liefert das aktuelle **Grow-Limit**
- Existierender Schnee über dem Limit **bleibt** (solange `temp < meltThreshold`)
- Er wächst nur nicht weiter, bis das Limit wieder steigt
- Deshalb: `snowBelowMax` – nur Spalten, bei denen `naturalSnowHeight + pluginSnowHeight < getMaxSnowHeight(temp)` → können wachsen

---

## 4. scanChunkColumns – Optimiert

**Statt `findColumnGround` (32-Block-Scan):**
```java
int topY = chunk.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
Block top = chunk.getBlock(x, topY, z);
if (top.getType().isSolid()) {
    ground = top;  // Fast-Path, 90%+ der Fälle
} else {
    // Fallback: 1 Block nach unten schauen (Pflanzen, Torch, etc.)
    ground = chunk.getBlock(x, topY - 1, z);
}
```

`HeightMap.MOTION_BLOCKING` ignoriert Snow-Layer (kein Motion-Blocking), liefert also den Block UNTER dem Schnee. Perfekt.

**>8 Layer Regel:** Snow-Layer werden auf maximal 8 gekappt (Minecraft-Limit). Höheres Wachstum (theoretisch) wird ignoriert. Plugin produziert nie >8 Layer.

---

## 5. Persistenz (JSON)

**Datei:** `plugins/Seasons/chunk_cache.json`

**Cache-Key:** `"worldUID:chunkKey"` (z.B. `"abc123:-1099511627568"`)

**Format:**
```json
{
  "cacheVersion": 1,
  "chunks": {
    "abc123:-1099511627568": {
      "snowCapable": 220,
      "snowCovered": 218,
      "snowBelowMax": 45,
      "tempLevelMin": -3,
      "tempLevelMax": -1,
      "updated": 1713123456789,
      "pluginSnow": "base64...",
      "naturalSnow": "base64..."
    }
  }
}
```

**Details:**
- Key = `world.getUID() + ":" + chunk.getChunkKey()`
- `pluginSnow` und `naturalSnow` als Base64-kodierte Byte-Arrays (256 Bytes je Array)
- Asynchrones Speichern: dirty-Flag, Batch alle 5s
- Laden bei Server-Start mit `cacheVersion`-Check (unbekannte Version → Cache verwerfen, Neuscan)
- Modul-übergreifend lesbar (SnowAccumulator, SnowMeltManager, Gewächshaus)

---

## 6. Invalidierung

| Trigger | Was |
|---------|-----|
| `BlockPlaceEvent` / `BlockBreakEvent` im Chunk | Cache löschen, dirty für JSON |
| `ChunkUnloadEvent` | Cache löschen, final in JSON schreiben |
| Aktuelle Temperatur verlässt `[tempLevelMin, tempLevelMax]` | Cache invalidieren |
| `SeasonChangeEvent` | Gesamten Cache leeren (Temperatur-Sprung) |
| TTL (30s) | Fallback, Memory-Leak-Schutz |

---

## 7. Ablauf

```
accumulateSnow(world):  // NUR im Winter
    für jeden geladenen Chunk:
        cache = getOrComputeCache(chunk)
        
        wenn cache.isFullyGrown:
            continue  // 0ms
        
        wenn cache.isSaturated:
            growSnowInChunk(chunk, cache)
        sonst:
            processChunk(chunk, cache)
```

---

## 8. Config

```yaml
weather:
  snow:
    growth:
      enabled: true
      layers-per-scan: 8
      saturation-threshold: 0.95
      cache:
        temp-level-tolerance: 0   # ±Level Puffer, später erhöhen für Tag/Nacht
        ttl-seconds: 30
        save-interval-seconds: 5
```

---

## 9. Entfernte Dinge

- `enoughNeighborsSnowOrBlocked`
- `min-neighbors-for-growth` Config
- grow-Aufruf in `processColumn`
- `findColumnGround` (ersetzt durch HeightMap + 1-Block-Fallback)

---

## 10. Performance

| Operation | Pro Chunk | Max. Kosten |
|-----------|-----------|-------------|
| `scanChunkColumns` (Cache-Fill) | 256 × `getHighestBlockYAt` (O(1)) + Fallback | ~0.5ms, nur bei Cache-Miss |
| `processChunk` (Platzierung) | `layersPerScan` (8) Spalten | ~0.1ms |
| `growSnowInChunk` (Wachstum) | `growth.layers-per-scan` (2) Spalten | ~0.05ms |
| Cache-Hit, fully grown | Nichts | 0ms |

Bei 100 Chunks, 95% gesättigt, 50% fully grown: ~2.7ms pro Scan.

---

## 11. Identifizierte Schwächen & Gegenmaßnahmen

| Schwäche | Risiko | Lösung |
|----------|--------|--------|
| `saturation-threshold: 0.95` könnte in Rand-Chunks zu spät greifen | Schneeflächen mit Lücken | Config-Wert, bei Bedarf auf 0.92 senkbar |
| JSON-Persistenz kann bei Crash Daten verlieren | Letzte ~5s Änderungen weg | Akzeptabel für Schnee-Cache |
| Cache nicht thread-safe | ConcurrentModification bei Async-Save | `ConcurrentHashMap` + Kopie für JSON-Serialisierung |
| Tag/Nacht-Schwankung > Toleranz | Cache wird zu oft invalidiert | Später `temp-level-tolerance` in Config erhöhen |
| `SNOW_BLOCK` ist solid → `getHighestBlockYAt` stoppt darauf | Kann Schnee auf Schneeblock als "Ground" erkennen | Schnee DARF auf Schneeblock wachsen – korrekt |
| `HeightMap.MOTION_BLOCKING` ignoriert Snow-Layer | Kein Ground gefunden bei 8 Layern Schnee | Genau das gewünschte Verhalten – liefert Block UNTER dem Schnee |

---

## 12. Summary-Log-Erweiterung

Der bestehende Summary-Log (alle 50 Scans) bekommt Cache-Statistiken:
```
[SnowAcc] summary: placed=380 grown=12 | cache: 87 hits, 3 misses, 52 fullyGrown
```

---

## 13. ToDo

- [ ] `ChunkCacheEntry`-Klasse (mit pluginSnowHeight/naturalSnowHeight)
- [ ] `ConcurrentHashMap<String, ChunkCacheEntry>` in `SnowAccumulator` (Key = worldUID:chunkKey)
- [ ] `scanChunkColumns(Chunk)` mit HeightMap MOTION_BLOCKING + 1-Block-Fallback
- [ ] `getOrComputeCache(World, Chunk)`
- [ ] JSON-Persistenz: `ChunkCacheStore` (laden/speichern/asynchron, Base64 für Byte-Arrays)
- [ ] Block-Listener für Invalidierung (Break/Place)
- [ ] `SeasonChangeEvent`-Handler: Cache leeren
- [ ] `growSnowInChunk(Chunk, cache)` implementieren
- [ ] grow-Aufruf aus `processColumn` entfernen
- [ ] `enoughNeighborsSnowOrBlocked` entfernen
- [ ] `min-neighbors-for-growth` aus Config/WeatherConfig/ConfigManager entfernen
- [ ] Neue `growth.*` + `growth.cache.*` Config-Einträge
- [ ] Summary-Log um Cache-Stats erweitern
- [ ] Build & Deploy