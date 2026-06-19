# Snow Melting – Mini-Konzept (Plugin-Schnee, nicht natürlicher Schnee)

**Ziel:** Nur Plugin-eigenen Schnee in Nicht-Winter-Saisons schmelzen.
Natürlicher Vanilla-Schnee (Schneebiome, Berge) bleibt unangetastet.
Nutzt denselben Chunk-Cache wie Snow Growth.

---

## 1. Kernprinzip

| Eigenschaft | Regel |
|-------------|-------|
| Was schmilzt? | Nur Schnee, der durch das Plugin platziert/gewachsen wurde (`pluginSnowHeight > 0`) |
| Was bleibt? | Natürlicher Vanilla-Schnee (`naturalSnowHeight`) |
| Wann? | Frühling, Sommer, Herbst – **nicht im Winter** |
| Wie? | **Layer by Layer:** Pro Scan wird pro Spalte genau 1 Layer abgebaut. Kein sofortiges Löschen ganzer Spalten – das simuliert natürliches, langsames Abschmelzen von oben nach unten. |
| Winter-Regel | Growth-System übernimmt, kein Melt |
| Regrowth | Gras-/Blumen-Nachwachsen nach Schneeschmelze ist ein **eigenständiges Feature** (späteres Konzept). Wird hier nicht behandelt. |

---

## 2. Ablauf

```
accumulateMelt(world):  // NUR in Nicht-Winter-Saisons
    für jeden geladenen Chunk:
        cache = getOrComputeCache(chunk)
        
        wenn !cache.hasPluginSnow():
            continue  // 0ms, nichts zu schmelzen
        
        processMeltChunk(chunk, cache)
```

**processMeltChunk:**
1. `pluginColumns` = alle Spalten mit `pluginSnowHeight > 0`
2. Shufflen (wie bei Placement)
3. `layersPerScan` Spalten abarbeiten:
   - `pluginSnowHeight--`
   - Wenn `pluginSnowHeight == 0`: Schnee komplett entfernen (Block auf Air setzen)
   - Sonst: Snow-Layer auf neue Höhe setzen (`naturalSnowHeight + pluginSnowHeight`)
   - `snowCovered` und `snowBelowMax` im Cache aktualisieren

---

## 3. Zusammenspiel mit Growth-Cache

**Cache wird geteilt.** `SnowAccumulator` und `SnowMeltManager` greifen auf dieselbe `ConcurrentHashMap` zu.

| Feld | Wer schreibt? | Wer liest? |
|------|---------------|------------|
| `snowCapable` | Scan (Growth) | Growth, Melt |
| `snowCovered` | Growth, Melt | Growth, Melt |
| `snowBelowMax` | Growth, Melt | Growth |
| `pluginSnowHeight[]` | Growth (++), Melt (--) | Growth, Melt |
| `naturalSnowHeight[]` | Scan (einmalig) | Melt (untere Grenze) |
| `tempLevelMin/Max` | Scan | Growth, Melt |

---

## 4. Schmelz-Logik pro Spalte

```java
void meltColumn(World world, Chunk chunk, int x, int z, ChunkCacheEntry cache) {
    int index = x * 16 + z;
    int plugin = cache.pluginSnowHeight[index];
    int natural = cache.naturalSnowHeight[index];
    
    if (plugin <= 0) return;  // nichts zu schmelzen
    
    int currentTotal = natural + plugin;
    int newPlugin = plugin - 1;
    int newTotal = natural + newPlugin;
    
    Block bottomBlock = chunk.getBlock(x, 0, z);  // Dummy, wir brauchen nur die Spalte
    int groundY = chunk.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
    Block ground = chunk.getBlock(x, groundY, z);
    if (!ground.getType().isSolid()) groundY--;  // Fallback
    
    int snowY = groundY + 1;
    
    if (newPlugin == 0 && natural == 0) {
        // Kein Schnee mehr – Block entfernen
        chunk.getBlock(x, snowY, z).setType(Material.AIR);
    } else if (newTotal > 0 && newTotal <= 8) {
        // Snow-Layer auf neue Gesamthöhe setzen
        Block snowBlock = chunk.getBlock(x, snowY, z);
        snowBlock.setType(Material.SNOW);
        Snow snow = (Snow) snowBlock.getBlockData();
        snow.setLayers(newTotal);
        snowBlock.setBlockData(snow);
    }
    // Wenn newTotal > 8: Theoretisch möglich, aber in Praxis selten. Layer auf 8 capen.
    // Plugin sollte nie >8 Layer produzieren (Config-Limit).
    
    cache.pluginSnowHeight[index] = newPlugin;
    cache.totalPluginSnowColumns--;
    
    if (newTotal == 0) {
        cache.snowCovered--;
    }
}
```

---

## 5. Saisonabhängigkeit

```java
public void tick(World world) {
    Season season = getCurrentSeason(world);
    
    if (season == Season.WINTER) {
        snowAccumulator.accumulateSnow(world);  // Growth-System
    } else {
        snowMeltManager.accumulateMelt(world);  // Melt-System
    }
}
```

**Warum kein Melt im Winter?**  
Weil der Winter-Schnee dann sofort wieder schmilzt. Winter = Growth-Phase, kein Gleichzeitigkeits-Chaos.

---

## 6. Config

```yaml
weather:
  snow:
    melting:
      enabled: true
      layers-per-scan: 4          # 4 Spalten pro Scan (schneller als Growth, damit Schnee zügig verschwindet)
      only-plugin-snow: true      # HAUPT-SCHALTER: true = nur Plugin-Schnee, false = ALLER Schnee
```

**`only-plugin-snow: false`** ist der Legacy-Modus (altes Verhalten). Default ist `true`.

---

## 7. Cache-Invalidierung (geteilt mit Growth)

| Trigger | Was |
|---------|-----|
| `BlockPlaceEvent` / `BlockBreakEvent` im Chunk | Cache löschen, dirty für JSON |
| `ChunkUnloadEvent` | Cache löschen, final in JSON schreiben |
| `SeasonChangeEvent` | Gesamten Cache leeren (Temperatur-Sprung, Melt↔Growth-Wechsel) |
| TTL (30s) | Fallback, Memory-Leak-Schutz |

**Beachte:** Bei Season-Wechsel von Winter→Frühling wird der Cache geleert → `naturalSnowHeight` wird neu gescannt. Der im Winter gewachsene Plugin-Schnee wird dann "korrekt als \"plugin\" erkannt und schmilzt.

**Bei Cache-Invalidierung (BlockBreak/Place, Temperatur) gilt dieselbe Regel wie im Growth-Konzept:**
`naturalSnowHeight` wird **explizit beibehalten**, `pluginSnowHeight = max(0, aktuelleHöhe - naturalSnowHeight)`.
Das verhindert, dass abgebauter natürlicher Schnee fälschlich als Plugin-Schnee klassifiziert wird."

---

## 8. Summary-Log-Erweiterung

```
[Melt] summary: melted=48 pluginColumnsRemaining=320 | cache: 90 hits, 0 misses
```

---

## 9. Performance

| Operation | Pro Chunk | Max. Kosten |
|-----------|-----------|-------------|
| `processMeltChunk` | `layersPerScan` (4) Spalten | ~0.05ms |
| Cache-Hit, kein Plugin-Schnee | Nichts | 0ms |

---

## 10. Identifizierte Schwächen & Gegenmaßnahmen

| Schwäche | Risiko | Lösung |
|----------|--------|--------|
| Spieler baut im Winter Schnee ab → Cache denkt, Plugin-Schnee ist noch da | `pluginSnowHeight` zu hoch, Schmelze dauert länger | Beim BlockBreak wird Cache gelöscht → Neuscan. Akzeptabel. |
| Natürlicher Schnee schmilzt im Biome durch Vanilla-Mechanik | `naturalSnowHeight` stimmt nicht mehr | Cache wird bei Temperatur-Änderung invalidiert → Neuscan. |
| `only-plugin-snow: false` als Legacy-Mode | Könnte Verwirrung stiften | Default bleibt `true`, Legacy-Mode per Config dokumentiert. |
| Schmelzgeschwindigkeit zu langsam bei vielen Plugin-Spalten | Schnee bleibt zu lange | `layers-per-scan` auf 4 gesetzt (doppelt so schnell wie Growth). Bei Bedarf erhöhbar. |

---

## 11. ToDo

- [ ] `SnowMeltManager`-Klasse erstellen (analog zu `SnowAccumulator`)
- [ ] `processMeltChunk(Chunk, ChunkCacheEntry)` implementieren
- [ ] Saison-Check in `SeasonsPlugin.tick()`: Winter → Growth, sonst → Melt
- [ ] `only-plugin-snow` Config-Eintrag in `WeatherConfig` + `ConfigManager`
- [ ] Bestehendes `processMeltChunk` in `SnowAccumulator` entfernen/migrieren
- [ ] Summary-Log für Melt hinzufügen
- [ ] Build & Deploy