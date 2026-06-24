# Frost System – Frost-Biome + Partikel (Phase 2b)

**Status:** Überarbeitet – angepasst an Custom-Biome-Datapack-Architektur (Phase 2.6)
**Phase:** 2b (nach erfolgreichem Abschluss von Phase 2.6)
**Ziel:** Frostige Optik bei Temperaturen unter 0°C – realisiert durch **Frost-Biome** (Custom-Biome-Datapack) + `SNOWFLAKE`-Partikel. Kein dynamischer Tint-Lerp, kein `sendBlockChange()`.

---

## 1. Ziel & Motivation

- Die Welt soll bei Temperaturen unter 0°C **frostig** wirken (auch ohne Schnee-Layer).
- Schnee-Layer (Phase 1) wirken dadurch natürlicher und nicht „auf grünem Gras".
- Schnelle Reaktion auf Tag/Nacht-Temperaturschwankungen (~40 Ticks über Coordinator-Timer).
- Sehr langsame Saison-Übergänge (passend zum 365-Tage-Jahr).

---

## 2. Kernprinzipien

### 2.1 Frost-Biome (Custom-Biome-Datapack)

Der **BiomeJsonGenerator** (Phase 2.6) erzeugt pro Vanilla-Biom eine zusätzliche Frost-Variante:

```
Schema: seasons:frost_<biome_key>

Beispiele:
  seasons:frost_forest
  seasons:frost_plains
  seasons:frost_birch_forest
  seasons:frost_swamp
  seasons:frost_taiga
```

**Farbe:** Kühles Weiß/Grau, konfigurierbar in `frost.yml`:
- `target-grass-color: 0xD0D4D8`
- `target-foliage-color: 0xC0C4C8`

Die Frost-Biome-JSONs sind **1:1-Kopien des Vanilla-Originals** – nur `grass_color` und `foliage_color` werden überschrieben. Kein Sub-Stufen-Lerp, kein dynamisches Mischen zur Laufzeit.

### 2.2 Resolver wählt Frost-Biom

Der **SeasonBiomeResolver** (Phase 2.6) prüft bei jedem Resolve:

```
Wenn Temperatur < freeze-threshold → seasons:frost_<biomeKey>
Sonst → normales Saison-Biom (seasons:<variant>_<biomeKey>)
```

Das passiert automatisch im 40-Tick-Timer des `BiomeSpoofCoordinator` – keine zusätzliche Infrastruktur nötig.

### 2.3 Frost-Partikel

Der **FrostEffectManager** spawnt `SNOWFLAKE`-Partikel um Spieler, wenn Frost aktiv ist. Leichtgewichtig, kein Block-Scan, kein State-Tracking.

### 2.4 Was wir NICHT tun

- ❌ Kein dynamischer Tint-Lerp (Custom-Biomes sind statisch)
- ❌ Kein `sendBlockChange()`-Overlay
- ❌ Kein `VisualSeasonManager` / `FoliageTintManager` (existieren nicht)
- ❌ Keine Sub-Varianten für Frost (eine einzige `frost_`-Variante pro Biom reicht)

---

## 3. Config: `frost.yml`

```yaml
frost:
  enabled: true

  # Temperatur-Schwellen
  freeze-threshold: 0.0            # Temperatur, ab der Frost-Biom aktiv wird
  full-frost-threshold: -7.0       # Temperatur für maximale Partikel-Intensität

  # Frost-Biom-Farben (in die JSONs geschrieben)
  target-grass-color: 0xD0D4D8     # Kühles Weiß/Grau für Gras
  target-foliage-color: 0xC0C4C8   # Kühles Weiß/Grau für Laub

  # Partikel-System
  particles:
    enabled: true
    type: SNOWFLAKE                # Alternativ: WHITE_ASH, END_ROD
    particles-per-second: 12       # pro Spieler bei full-frost
    spread-radius: 3.0             # Block-Radius um den Spieler

  # Biome-Ausschlussliste (KEIN Frost in diesen Biomen)
  excluded-biomes:
    - DESERT
    - BADLANDS
    - ERODED_BADLANDS
    - WOODED_BADLANDS
    - SAVANNA
    - SAVANNA_PLATEAU
    - WINDSWEPT_SAVANNA
    - WARM_OCEAN
    - LUKEWARM_OCEAN
    - DEEP_LUKEWARM_OCEAN
    - NETHER_WASTES
    - SOUL_SAND_VALLEY
    - CRIMSON_FOREST
    - WARPED_FOREST
    - BASALT_DELTAS
    - THE_END
    - END_BARRENS
    - END_HIGHLANDS
    - END_MIDLANDS
    - SMALL_END_ISLANDS
```

---

## 4. Frost-Berechnung

```java
double getFrostFactor(double temperature) {
    if (temperature >= config.getFreezeThreshold()) return 0.0;

    double range = config.getFreezeThreshold() - config.getFullFrostThreshold();
    double progress = (config.getFreezeThreshold() - temperature) / range;

    return Math.clamp(progress, 0.0, 1.0);
}
```

Der `frostFactor` wird NUR für die Partikel-Intensität verwendet. Die Biom-Wahl ist binär: `temperature < freezeThreshold` → Frost-Biom.

---

## 5. Betroffene Klassen

### 5.1 BiomeJsonGenerator (erweitern)

- Liest Frost-Zielfarben aus `FrostConfig`
- Erzeugt pro `enabled_biome` eine `frost_<biome>.json`
- Nur `grass_color` und `foliage_color` werden auf Frost-Farben gesetzt
- Alle anderen Felder = 1:1-Kopie des Vanilla-Originals

```java
// In BiomeJsonGenerator.generate():
for (String biomeKey : enabledBiomes) {
    // ... normale Saison-Biomes generieren ...

    // Frost-Variante
    BiomeJson frostJson = vanillaBiomeReference.copyOf(biomeKey);
    frostJson.setGrassColor(frostConfig.getTargetGrassColor());
    frostJson.setFoliageColor(frostConfig.getTargetFoliageColor());
    writeJson("frost_" + biomeKey, frostJson);
}
```

### 5.2 SeasonBiomeResolver (erweitern)

- `resolveBiome(originalBiome, season, temperature)`:
  - Wenn `temperature < frostConfig.getFreezeThreshold()` → `seasons:frost_<biomeKey>`
  - Sonst → normales Saison-Biom

### 5.3 FrostConfig (NEU)

- Liest `frost.yml`
- Stellt `getFreezeThreshold()`, `getFullFrostThreshold()`, `getTargetGrassColor()`, `getTargetFoliageColor()`, `getParticlesPerSecond()`, `getSpreadRadius()`, `getExcludedBiomes()` bereit

### 5.4 FrostEffectManager (NEU)

- Berechnet `frostFactor` aus Temperatur
- Spawnt `SNOWFLAKE`-Partikel bei `frostFactor > 0`
- Prüft `excluded-biomes` – keine Partikel in Wüste, Nether, End
- Periodischer Task (alle 4–8 Sekunden) für aktive Spieler
- Cleanup bei `PlayerQuitEvent`

### 5.5 BiomeSpoofCoordinator (minimal)

- Übergibt Temperatur an `SeasonBiomeResolver.resolveBiome()` – bereits vorhanden, keine strukturellen Änderungen nötig

---

## 6. Datenfluss

```
1. BiomeSpoofCoordinator.run() (alle 40 Ticks)
   └── Für jeden Spieler:
       └── Temperatur = TemperatureCalculator.getTemperature(loc)
       └── SeasonBiomeResolver.resolveBiome(original, season, temperatur)
           └── temp < freezeThreshold? → seasons:frost_forest
           └── sonst → seasons:fall_forest
       └── ChunkBiomeApplier.captureAndApply(chunk, resolvedBiome)

2. FrostEffectManager (alle 4–8 Sekunden)
   └── Für jeden Spieler:
       └── frostFactor = getFrostFactor(temperature)
       └── prüfe excluded-biomes
       └── spawnParticle(SNOWFLAKE, player.getLocation(), count, spread)
```

---

## 7. Performance & Risiken

| Maßnahme                    | Effekt |
|----------------------------|--------|
| Frost-Biom = statische JSON | 0 Laufzeit-Overhead |
| Resolver-Check = 1 Vergleich | O(1) pro Chunk |
| Kein Block-Scan            | 0 Chunk-Iterationen |
| Partikel-Leichtbau         | `spawnParticle()` ist billig |
| 4–8 Sekunden Intervall     | Minimaler CPU-Verbrauch |
| Biome-Ausschlussliste      | Keine Frost-Effekte in Wüste/Nether/End |

**Risiko:** Bei 50+ Spielern könnte die Partikel-Anzahl skaliert werden müssen. Lösung: `particles-per-second` adaptiv an Spielerzahl koppeln (später).

---

## 8. ToDo für Phase 2b

- [ ] `FrostConfig` + `frost.yml` (erstellen, in `ConfigManager` registrieren)
- [ ] `BiomeJsonGenerator` erweitern: Frost-Biome erzeugen
- [ ] `SeasonBiomeResolver` erweitern: Frost-Biom bei Temperatur < Threshold wählen
- [ ] `FrostEffectManager` (Frost-Faktor, Biome-Filter, Partikel)
- [ ] Integration in `SeasonsPlugin.onEnable()`
- [ ] Build, Deploy, `/season generate-biomes force`, Server-Neustart
- [ ] Funktionstest: Winter + Nacht → Frost-Biome + Partikel sichtbar

---

## 9. Abhängigkeiten

- `TemperatureCalculator` / `TemperatureConfig` (Phase 1) – ✅ vorhanden
- `BiomeJsonGenerator` (Phase 2.6) – 🔄 muss erweitert werden
- `SeasonBiomeResolver` (Phase 2.6) – 🔄 muss erweitert werden
- `BiomeSpoofCoordinator` (Phase 2.6) – ✅ vorhanden, übergibt bereits Temperatur
- `ConfigManager` (Phase 1) – ✅ vorhanden, muss `frost.yml` registrieren
- `VanillaBiomeReference` (Phase 2.6) – ✅ vorhanden, wird als Basis für Frost-JSONs genutzt

**Fertigstellungskriterien:**
- `BiomeJsonGenerator` erzeugt `frost_*.json` für alle `enabled_biomes`
- `SeasonBiomeResolver` wählt Frost-Biom bei Temperatur < `freeze-threshold`
- Gras-/Laub-Farben frostig (kühles Weiß/Grau) bei Frost
- Schneeflocken-Partikel bei Frost sichtbar, Dichte konfigurierbar
- Keine Frost-Effekte in excluded-biomes
- Schnelle Tag/Nacht-Reaktion (< 40 Ticks über Coordinator-Timer)
- Keine Konflikte mit Snow-System (Phase 1.5) oder Custom-Biome-Datapack (Phase 2.6)