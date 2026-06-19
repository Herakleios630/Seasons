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

## 2. Sättigungs-Prüfung (Cache + Persistenz)

**Pro Chunk speichern wir:**
- `snowCapableColumns`: Spalten, die Schnee tragen können (solider, voller Block, nicht zu warm)
- `snowCoveredColumns`: Davon haben aktuell Schnee (irgendeine Höhe)
- `snowBelowMaxColumns`: Davon haben Schnee unter dem aktuellen Grow-Limit (können noch wachsen)
- `computedAtTempLevel`: `floor(temperatur / 0.2)`, nur bei Level-Wechsel → Cache-Invalidierung
- `lastUpdated`: Timestamp (für TTL-Fallback)

**Grow-Limit vs. existierender Schnee:**
- `getMaxSnowHeight(temp)` liefert das aktuelle **Grow-Limit**
- Existierender Schnee über dem Limit **bleibt** (solange `temp < meltThreshold`)
- Er wächst nur nicht weiter, bis das Limit wieder steigt
- Deshalb: `snowBelowMaxColumns` – Spalten, die unter dem aktuellen Limit sind → können wachsen

**Ergebnis:**
- `isSaturated = snowCoveredColumns >= snowCapableColumns * saturationThreshold`
- `isFullyGrown = snowBelowMaxColumns == 0 && snowCapableColumns > 0`

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
      "tempLevel": -2,
      "updated": 1713123456789
    }
  }
}
```

- **Key:** `chunk.getChunkKey()` als String (long: `(x << 32) | z & 0xFFFFFFFFL`)
- **Asynchrones Speichern:** Bei jedem Cache-Miss oder Block-Update wird die JSON in den nächsten 5s asynchron geschrieben (Batch-Schutz)
- **Laden:** Beim Server-Start einmalig – Cache-Misses für fehlende Chunks werden live berechnet
- **Modul-übergreifend:** Andere Module (Gewächshaus, Schmelze) können die JSON lesen

---

## 4. Invalidierung

| Trigger | Was |
|---------|-----|
| `BlockPlaceEvent` / `BlockBreakEvent` | Cache für den Chunk löschen, `dirty` flag für JSON-Sync |
| `ChunkUnloadEvent` | Cache-Eintrag löschen, in JSON schreiben (letzter Stand) |
| `computedAtTempLevel != floor(temperatur / 0.2)` | Nur bei Level-Änderung → Cache-Eintrag invalidieren |
| TTL (30s) | Fallback, periodischer Refresh gegen Memory-Leak |

---

## 5. Ablauf im Scheduler

```
accumulateSnow(world):
    für jeden geladenen Chunk:
        cache = getOrComputeCache(chunk)
        
        wenn cache.isFullyGrown:
            // nichts tun – alle Limits erreicht
            continue
        
        wenn cache.isSaturated:
            growSnowInChunk(chunk)       // nur Wachstum
        sonst:
            processChunk(chunk)          // nur Platzierung
```

---

## 6. Neue Config (nur Wachstum)

```yaml
weather:
  snow:
    growth:
      enabled: true
      layers-per-scan: 2               # max +1 Layer pro Chunk-Scan
      saturation-threshold: 1.0        # 1.0 = alle Spalten müssen Schnee haben
      max-height:
        base: 2                        # Schichten bei freeze-threshold
        per-cold-level: 1              # +1 Layer pro 0.2°C unter freeze
```

---

## 7. Entfernte Dinge

- `enoughNeighborsSnowOrBlocked` – wird nicht mehr gebraucht
- `min-neighbors-for-growth` Config – entfällt
- grow-Aufruf in `processColumn` – entfällt
- `growColumnSnow` bleibt, wird aber nur noch von `growSnowInChunk` gerufen

---

## 8. Performance

| Operation | Pro Chunk | Max. Kosten |
|-----------|-----------|-------------|
| `scanChunkColumns` (Cache-Fill) | 256 × `getHighestBlockAt` + `findColumnGround` | ~1–2ms, nur bei Cache-Miss |
| `processChunk` (Platzierung) | `layersPerScan` (8) Spalten | ~0,1ms |
| `growSnowInChunk` (Wachstum) | `growth.layers-per-scan` (2) Spalten | ~0,05ms |
| Cache-Hit, fully grown | Nichts | 0ms |

Bei 100 geladenen Chunks, 95% gesättigt, 50% fully grown:
- 5 × `processChunk` = ~0,5ms
- 45 × `growSnowInChunk` = ~2,2ms
- 50 × fully grown = 0ms
- Total: ~2,7ms pro Scan – sehr akzeptabel

**Tag/Nacht-Schwankung:** `computedAtTempLevel` ändert sich nur bei Temperatur-Sprung >0.2°C.
Die dayNightAmplitude (~0.15°C) triggert kein Re-Scan. Keine spürbare Last.

---

## 9. ToDo

- [ ] `ChunkCacheEntry`-Klasse (Datenmodell)
- [ ] `HashMap<Long, ChunkCacheEntry>` in `SnowAccumulator`
- [ ] `scanChunkColumns(Chunk)` → liefert `ChunkCacheEntry`
- [ ] `getOrComputeCache(Chunk)` → Cache-Hit oder Neuberechnung
- [ ] JSON-Persistenz: `ChunkCacheStore` (laden/speichern/asynchron)
- [ ] Block-Listener für Invalidierung (Break/Place) in `SnowListener` oder neuem Listener
- [ ] `growSnowInChunk(Chunk)` implementieren
- [ ] grow-Aufruf aus `processColumn` entfernen
- [ ] `enoughNeighborsSnowOrBlocked` entfernen
- [ ] `min-neighbors-for-growth` aus Config/WeatherConfig/ConfigManager entfernen
- [ ] Neue `growth.*` Config-Einträge hinzufügen
- [ ] Build & Deploy