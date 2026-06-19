# Snow Growth – Mini-Konzept (mit Caching + Persistenz)

**Ziel:** Platzierung/Ausbreitung und Höhenwachstum strikt trennen.
Erst wenn alle platzierbaren Spalten im Chunk Schnee haben, beginnt langsames Wachstum.

---

## 1. Trennung der Concerns

| Modul | Wann | Was |
|-------|------|-----|
| `processChunk` (Platzierung) | Chunk nicht gesättigt | Shuffled `layersPerScan` Spalten, setzt Layer 1. **Kein grow mehr.** |
| `growSnowInChunk` (Wachstum) | Chunk gesättigt | Shuffled `growth-layers-per-scan` Schnee-Spalten, wächst +1 Layer. Keine Nachbar-Prüfung. |
| nichts | `isFullyGrown == true` | Chunk komplett überspringen (0ms) |

---

## 2. Sättigungs-Cache (RAM + JSON)

**Pro Chunk speichern wir:**
- `snowCapable`: Spalten, die Schnee tragen können (solider, voller Block)
- `snowCovered`: Davon haben aktuell Schnee (irgendeine Höhe)
- `snowBelowMax`: Davon liegen unter dem aktuellen Grow-Limit (können noch wachsen)
- `tempLevelMin` / `tempLevelMax`: Temperatur-Intervall, für das der Cache gültig ist
- `lastUpdated`: Timestamp (TTL-Fallback)

**Temperatur-Toleranz (spätere Tag/Nacht-Schwankung vorbereitet):**
- `getMaxSnowHeight` rechnet in 0.2°C-Schritten: `extra = (freezeThreshold - temp) / 0.2 * heightPerCold`
- Cache speichert Intervall `[floor(temp/0.2) - tolerance, floor(temp/0.2) + tolerance]`
- Nur wenn aktuelle Temperatur das Intervall verlässt: Cache invalidieren und neu scannen
- `tolerance` aus Config (Default 0 = kein Puffer, später auf z.B. 10 setzbar für ±2°C)
- Tag/Nacht-Schwankungen von mehreren Grad triggern dann **kein** Re-Scan

**Grow-Limit vs. existierender Schnee:**
- `getMaxSnowHeight(temp)` liefert das aktuelle **Grow-Limit**
- Existierender Schnee über dem Limit **bleibt** (solange `temp < meltThreshold`)
- Er wächst nur nicht weiter, bis das Limit wieder steigt
- Deshalb: `snowBelowMax` – nur Spalten unter dem aktuellen Limit → können wachsen

**Ergebnis:**
- `isSaturated = snowCovered >= snowCapable * saturationThreshold`
- `isFullyGrown = snowBelowMax == 0 && snowCapable > 0`

---

## 3. Persistenz (JSON)

**Datei:** `plugins/Seasons/chunk_cache.json`

**Format:**
```json
{
  "world": "minecraft:overworld",
  "chunks": {
    "-1099511627568": {
      "snowCapable": 220,
      "snowCovered": 218,
      "snowBelowMax": 45,
      "tempLevelMin": -3,
      "tempLevelMax": -1,
      "updated": 1713123456789
    }
  }
}
```

**Details:**
- Key = `chunk.getChunkKey()` als String
- Asynchrones Speichern: dirty-Flag, Batch alle 5s
- Laden bei Server-Start
- Modul-übergreifend lesbar (Gewächshaus, Schmelze)

---

## 4. Invalidierung

| Trigger | Was |
|---------|-----|
| `BlockPlaceEvent` / `BlockBreakEvent` im Chunk | Cache löschen, dirty für JSON |
| `ChunkUnloadEvent` | Cache löschen, final in JSON schreiben |
| Aktuelle Temperatur verlässt `[tempLevelMin, tempLevelMax]` | Cache invalidieren |
| `SeasonChangeEvent` | Gesamten Cache leeren (Temperatur-Sprung) |
| TTL (30s) | Fallback, Memory-Leak-Schutz |

---

## 5. Ablauf

```
accumulateSnow(world):
    für jeden geladenen Chunk:
        cache = getOrComputeCache(chunk)
        
        wenn cache.isFullyGrown:
            continue  // 0ms
        
        wenn cache.isSaturated:
            growSnowInChunk(chunk)
        sonst:
            processChunk(chunk)
```

---

## 6. Config

```yaml
weather:
  snow:
    growth:
      enabled: true
      layers-per-scan: 2
      saturation-threshold: 1.0
      cache:
        temp-level-tolerance: 0   # ±Level Puffer, später erhöhen für Tag/Nacht
        ttl-seconds: 30
        save-interval-seconds: 5
```

---

## 7. Entfernte Dinge

- `enoughNeighborsSnowOrBlocked`
- `min-neighbors-for-growth` Config
- grow-Aufruf in `processColumn`

---

## 8. Performance

| Operation | Pro Chunk | Max. Kosten |
|-----------|-----------|-------------|
| `scanChunkColumns` (Cache-Fill) | 256 × `getHighestBlockAt` + `findColumnGround` | ~1–2ms, nur bei Cache-Miss |
| `processChunk` (Platzierung) | `layersPerScan` (8) Spalten | ~0,1ms |
| `growSnowInChunk` (Wachstum) | `growth.layers-per-scan` (2) Spalten | ~0,05ms |
| Cache-Hit, fully grown | Nichts | 0ms |

Bei 100 Chunks, 95% gesättigt, 50% fully grown: ~2,7ms pro Scan.

---

## 9. Identifizierte Schwächen & Gegenmaßnahmen

| Schwäche | Risiko | Lösung |
|----------|--------|--------|
| `findColumnGround` scannt 32 Blöcke bei tiefen Schluchten | Cache-Miss wird teuer | `if (top.isSolid()) return top` als Fast-Path (90% aller Spalten) |
| `saturation-threshold: 1.0` zu streng | Rand-Chunks nie gesättigt | Config-Wert, justierbar |
| JSON-Persistenz kann bei Crash Daten verlieren | Letzte ~5s Änderungen weg | Akzeptabel für Schnee-Cache |
| Cache nicht thread-safe | ConcurrentModification bei Async-Save | `ConcurrentHashMap` + Kopie für JSON-Serialisierung |
| Tag/Nacht-Schwankung > Toleranz | Cache wird zu oft invalidiert | Später `temp-level-tolerance` in Config erhöhen |
| `SNOW_BLOCK` ist solid → `findColumnGround` stoppt darauf | Kann Schnee auf Schnee als "Ground" erkennen | Ist korrekt, Schnee darf auf Schnee wachsen |

---

## 10. Summary-Log-Erweiterung

Der bestehende Summary-Log (alle 50 Scans) bekommt Cache-Statistiken:
```
[SnowAcc] summary: placed=380 grown=12 melted=0 | cache: 87 hits, 3 misses, 52 fullyGrown
```

---

## 11. ToDo

- [ ] `ChunkCacheEntry`-Klasse
- [ ] `ConcurrentHashMap<Long, ChunkCacheEntry>` in `SnowAccumulator`
- [ ] `scanChunkColumns(Chunk)` mit Fast-Path für soliden Top-Block
- [ ] `getOrComputeCache(Chunk)`
- [ ] JSON-Persistenz: `ChunkCacheStore` (laden/speichern/asynchron)
- [ ] Block-Listener für Invalidierung (Break/Place)
- [ ] `SeasonChangeEvent`-Handler: Cache leeren
- [ ] `growSnowInChunk(Chunk)` implementieren
- [ ] grow-Aufruf aus `processColumn` entfernen
- [ ] `enoughNeighborsSnowOrBlocked` entfernen
- [ ] `min-neighbors-for-growth` aus Config/WeatherConfig/ConfigManager entfernen
- [ ] Neue `growth.*` + `growth.cache.*` Config-Einträge
- [ ] Summary-Log um Cache-Stats erweitern
- [ ] Build & Deploy