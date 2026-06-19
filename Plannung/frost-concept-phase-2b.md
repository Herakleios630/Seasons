# Frost System – Temperaturabhängiger Frost (Phase 2b)

**Status:** Überarbeitetes Konzept nach Codebasis-Analyse  
**Phase:** 2b (nach erfolgreichem Abschluss von Phase 2 – Foliage Tints)  
**Ziel:** Eine frostige/weiße Optik der Welt bei niedrigen Temperaturen, unabhängig von echten Snow-Layern. Schnelle Tag/Nacht-Reaktion + langsame Saison-Integration.

---

## 1. Ziel & Motivation

- Die Welt soll bei Temperaturen unter 0°C **frostig** wirken (auch ohne Schnee-Layer).
- Schnee-Layer (Phase 1) wirken dadurch natürlicher und nicht „auf grünem Gras".
- Schnelle Reaktion auf Tag/Nacht-Temperaturschwankungen (30–90 Sekunden).
- Sehr langsame Saison-Übergänge (passend zum 365-Tage-Jahr).

---

## 2. Kernprinzipien (rein Plugin)

- **Pro Spieler** Ansatz (keine globalen Block-Änderungen).
- **Zwei kombinierte Techniken**:
  - **Biome Color Tint (Foliage + Grass)** – Frost-Zielfarbe wird per Lerp in die Saison-Tint gemischt.
  - **Frost-Partikel** – bei aktivem Frost schweben `SNOWFLAKE`-Partikel um den Spieler.
- **Kein `sendBlockChange()`-Overlay.** Es gibt keinen passenden Vanilla-Block-Typ, und Chunk-Updates würden die Effekte zerstören (Flackern, Relog-Verlust). Die Kombination Tint + Partikel liefert 90% der Optik ohne diese Probleme.
- **Kein permanenter Block-State** – alles client-seitig und temporär.

---

## 3. Frost-Berechnung

```java
double getFrostFactor(Location loc) {
    double temp = temperatureManager.getTemperature(loc);
    if (temp >= config.getFreezeThreshold()) return 0.0;

    double intensity = (config.getFreezeThreshold() - temp) /
                       (config.getFullFrostThreshold() - config.getFreezeThreshold());

    return Math.clamp(intensity * config.getIntensityMultiplier(), 0.0, 1.0);
}
```

**Config-Ausschnitt** (`frost.yml`):

```yaml
frost:
  enabled: true
  freeze-threshold: 0.0            # Temperatur, ab der Frost beginnt
  full-frost-threshold: -7.0       # Temperatur, bei der Frost maximal ist
  day-night-transition-seconds: 60 # Geschwindigkeit des Tag/Nacht-Frostwechsels
  intensity-multiplier: 0.75       # Gesamtstärke-Skalierung (0.0 = kein Frost)

  # Frost-Lerp-Zielfarbe (auf Gras, Laub, Foliage)
  target-color: "0xDDE4E8"         # Kühles, leicht bläuliches Weiß
  tint-strength: 0.65              # max. Einfluss des Frost-Lerp (0.0–1.0)

  # Partikel-System
  particles:
    enabled: true
    type: SNOWFLAKE                # Alternativ: WHITE_ASH, END_ROD
    particles-per-second: 12       # pro Spieler
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

## 4. FrostEffectManager

**Verantwortlichkeiten:**
- Berechnet `frostFactor` pro Spieler (aus Temperatur + Biome-Check).
- Bestimmt Partikel-Dichte und spawns `SNOWFLAKE`-Partikel.
- Meldet den Frost-Faktor an den `VisualSeasonManager`, der den Tint-Lerp durchführt.
- Prüft `excluded-biomes` – in heißen/wüsten Biomen wird kein Frost angewendet.

**Optimierungen:**
- Nur alle 4–8 Sekunden updaten (periodischer Bukkit-Task).
- Partikel-Spawns sind leichtgewichtig, kein State-Tracking nötig.
- Kein Chunk-Scan, kein Block-Iterieren, kein Cache – nur Mathematik + `spawnParticle()`.
- Cleanup bei Spieler Quit (Task-Logik prüft `player.isOnline()`).

---

## 5. Integration mit Phase 2

- Baut auf `FoliageTintManager` auf.
- `VisualSeasonManager.updatePlayerVisuals()`:
  1. Holt Saison-Tint von `FoliageTintManager.getSeasonalColors()`
  2. Holt `frostFactor` von `FrostEffectManager.getFrostFactor(player)`
  3. Mischt final: `lerp(seasonTint, frostTargetColor, frostFactor * tintStrength)`
- `VisualSeasonManager` ist der einzige Koordinator – `FrostEffectManager` kennt den Tint-Manager nicht direkt.

```java
// VisualSeasonManager.updatePlayerVisuals() in Phase 2b:
int seasonGrass = foliageTintManager.getSeasonalColors(biome, temp).grassColor();
int frostTarget = frostEffectManager.getTargetColor();
double frostFactor = frostEffectManager.getFrostFactor(player);
int finalGrass = lerpColor(seasonGrass, frostTarget, frostFactor * tintStrength);
```

---

## 6. Ablauf

- Periodischer Task (alle 4–8 Sekunden) für aktive Spieler.
- Bei SeasonChange → sanfter Übergang (Frost-Faktor folgt der Temperatur ohnehin stetig).
- **Kein `PlayerMoveEvent`-Trigger.** Die Temperatur ändert sich nur bei Biome- oder signifikantem Höhen-Wechsel, der 4s-Timer fängt das ausreichend schnell ein.

---

## 7. Performance & Risiken

| Maßnahme                    | Effekt |
|----------------------------|--------|
| Kein Block-Scan            | 0 Chunk-Iterationen |
| Tint-Lerp rein rechnerisch | O(1) pro Spieler |
| Partikel-Leichtbau         | `spawnParticle()` ist billig |
| 4–8 Sekunden Intervall     | Minimaler CPU-Verbrauch |
| Biome-Ausschlussliste      | Keine Frost-Effekte in Wüste/Nether/End |

**Risiko:** Bei 50+ Spielern könnte die Partikel-Anzahl skaliert werden müssen. Lösung: `particles-per-second` adaptiv an Spielerzahl koppeln (später).

---

## 8. ToDo für Phase 2b

- [ ] `FrostConfig` + `frost.yml` (erstellen, in `ConfigManager` registrieren)
- [ ] `FrostEffectManager` (Frost-Faktor, Biome-Filter, Partikel)
- [ ] Integration in `VisualSeasonManager` (Tint-Lerp mit Frost-Zielfarbe)
- [ ] Partikel-System (Dichte, Spread, Type)
- [ ] Performance-Tests mit vielen Spielern
- [ ] Feintuning Tag/Nacht-Übergänge (Transition-Seconds aus Config)

---

## 9. Abhängigkeiten

- `TemperatureCalculator` / `TemperatureConfig` (bereits vorhanden – Phase 1)
- `FoliageTintManager` + `VisualSeasonManager` (muss in Phase 2 gebaut werden)
- `ConfigManager` (muss `frost.yml` laden können)
- `ExcludedBiomes` – nutzt `BiomeTemperature` / `PrecipitationCategory`-Infrastruktur aus Phase 1

**Fertigstellungskriterien:**
- Deutliche frostige Optik unter 0°C (Gras/Laub bleicht aus)
- Schneeflocken-Partikel bei Frost sichtbar
- Keine Frost-Effekte in excluded-biomes
- Schnelle Tag/Nacht-Reaktion ohne Lag
- Keine spürbaren Performance-Einbußen bei 10–15 Spielern