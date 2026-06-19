"# Snow Growth – Mini-Konzept (mit Caching)

**Ziel:** Platzierung/Ausbreitung und Höhenwachstum strikt trennen.
Erst wenn alle platzierbaren Spalten im Chunk Schnee haben, beginnt langsames Wachstum.

---

## 1. Trennung der Concerns

| Modul | Wann | Was |
|-------|------|-----|
| `processChunk` (Platzierung) | Chunk nicht gesättigt | Shuffled `layersPerScan` Spalten, setzt Layer 1. **Kein grow mehr.** |
| `growSnowInChunk` (Wachstum) | Chunk gesättigt | Shuffled `growth-layers-per-scan` Schnee-Spalten, wächst +1 Layer. Keine Nachbar-Prüfung. |
| nichts | Chunk gesättigt + alle Spalten auf Max-Höhe | Komplett überspringen (spart 256 Scans) |

---

## 2. Sättigungs-Prüfung + Cache

**Pro Chunk speichern wir:**
- `snowCapableColumns`: Anzahl Spalten, die Schnee tragen können (solider, voller Block, nicht zu warm)
- `snowCoveredColumns`: Davon haben aktuell Schnee
- `snowAtMaxColumns`: Davon sind auf Maximal-Höhe für aktuelle Temperatur
- `computedAtTick`: Wann berechnet
- `computedAtTemperature`: Bei welcher Temperatur

**Cache-Key:** `chunk.getChunkKey()` (long: `(x << 32) | z & 0xFFFFFFFFL`)

**Invalidierung:**
| Trigger | Was |
|---------|-----|
| `BlockPlaceEvent` / `BlockBreakEvent` | Cache für den Chunk löschen |
| `ChunkUnloadEvent` | Cache-Eintrag löschen |
| Season-Wechsel / Temperatur-Änderung | `computedAtTemperature` vs. aktuelle Temperatur → neu berechnen |
| TTL (30s) | Fallback, periodischer Refresh |

**Erneute Berechnung:** `scanChunkColumns()` zählt einmal alle 256 Spalten durch:
```
für jede Spalte:
    top = getHighestBlockAt(x,z)
    ground = findColumnGround(top)
    wenn ground != null UND isSnowCapable(ground):
        snowCapableColumns++
        wenn ground+1 == SNOW:
            snowCoveredColumns++
            wenn Schnee-Höhe >= maxHeight(temperature):
                snowAtMaxColumns++
```
Ergebnis:
- `isSaturated = snowCoveredColumns == snowCapableColumns`
- `isFullyGrown = snowCoveredColumns == snowAtMaxColumns && snowCapableColumns > 0`

---

## 3. Ablauf im Scheduler

```
accumulateSnow(world):
    für jeden geladenen Chunk:
        cache = getCache(chunk)
        wenn cache == null || cache.invalid():
            cache = scanChunkColumns(chunk)
        
        wenn cache.isFullyGrown:
            // nichts tun – alle Limits erreicht
        
        wenn cache.isSaturated:
            growSnowInChunk(chunk)       // nur Wachstum
        sonst:
            processChunk(chunk)          // nur Platzierung
```

---

## 4. Neue Config (nur Wachstum)

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

## 5. Entfernte Dinge

- `enoughNeighborsSnowOrBlocked` – wird nicht mehr gebraucht
- `min-neighbors-for-growth` Config – entfällt
- grow-Aufruf in `processColumn` – entfällt
- `growColumnSnow` bleibt, wird aber nur noch von `growSnowInChunk` gerufen

---

## 6. Performance

| Operation | Pro Chunk | Max. Kosten |
|-----------|-----------|-------------|
| `scanChunkColumns` (Cache-Fill) | 256 × `getHighestBlockAt` + `findColumnGround` | ~1–2ms, nur bei Cache-Miss |
| `processChunk` (Platzierung) | `layersPerScan` (8) Spalten | ~0,1ms |
| `growSnowInChunk` (Wachstum) | `growth.layers-per-scan` (2) Spalten | ~0,05ms |
| Cache-Hit, fully grown | Nichts | 0ms |

Bei 100 geladenen Chunks, 95% gesättigt:
- 5 × `processChunk` = ~0,5ms
- 95 × `growSnowInChunk` = ~5ms
- Total: ~5,5ms pro Scan – akzeptabel

---

## 7. ToDo

- [ ] `SaturationCache`-Klasse mit `Map<Long, CacheEntry>` in `SnowAccumulator`
- [ ] `scanChunkColumns(Chunk)` → liefert CacheEntry
- [ ] Block-Listener für Invalidierung (Break/Place) in `SnowListener` oder neuem Listener
- [ ] `growSnowInChunk(Chunk)` implementieren
- [ ] grow-Aufruf aus `processColumn` entfernen
- [ ] `enoughNeighborsSnowOrBlocked` entfernen
- [ ] `min-neighbors-for-growth` aus Config/WeatherConfig/ConfigManager entfernen
- [ ] Neue `growth.*` Config-Einträge hinzufügen
- [ ] Build & Deploy
"