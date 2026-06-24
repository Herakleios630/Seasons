# Phase 3: Temperatureffekte & Spieler-Interaktion – Überarbeitetes Konzept

**Erstellt:** 2025-07-08
**Status:** Konzept – Freigegeben
**Betrifft:** Phase 3 in `Plannung/roadmap.md`
**Basiert auf:** Architektur nach Phase 2.6 (Custom-Biome-Datapack) + Phase 2b (Frost-System)

---

## 1. Ausgangslage nach Phase 2.6+2b

| Komponente | Status | Mechanismus |
|---|---|---|
| Custom-Biome-Datapack | ✅ Aktiv | `BiomeJsonGenerator` erzeugt JSONs (1:1-Kopie Vanilla, nur `grass_color`/`foliage_color` überschrieben) |
| Frost-Biome | ✅ Aktiv | `frost_<biome>.json` bei `temp < freezeThreshold` (nur Farben überschrieben) |
| `FrostEffectManager` | ✅ Aktiv | `SNOWFLAKE`-Partikel bei Frost |
| `BiomeSpoofCoordinator` | ✅ Aktiv | 40-Tick-Timer, wendet Biome per `world.setBiome()` an |

**Das ermöglicht massive Vereinfachungen für Phase 3.**

---

## 2. IceEffect → Ersetzt durch Biome-Temperatur

### 2.1 Prinzip

Vanilla Minecraft friert stehende Wasserquellen in Biomen mit `temperature < 0.15` ein (Random-Tick, muss dem Himmel ausgesetzt sein). Unser `BiomeJsonGenerator` erzeugt Frost-Biome aktuell **ohne** angepasste `temperature`. Wenn wir den Frost-Biome-JSONs zusätzlich `temperature` auf einen Wert unter 0 setzen, übernimmt Vanilla die Eisbildung – kein manueller Block-Scan nötig:

```json
{
  "temperature": -0.5,
  "downfall": 0.4,
  "has_precipitation": true,
  "effects": {
    "grass_color": 0xD0D4D8,
    "foliage_color": 0xC0C4C8,
    "grass_color_modifier": "none"
  }
}
```

### 2.2 Datenfluss

```
BiomeSpoofCoordinator (40 Ticks)
  └── temp < freezeThreshold → setBiome(seasons:frost_forest)
        └── Chunk hat jetzt Biome mit temperature=-0.5
              └── Vanilla Random-Tick friert stehende Wasserquellen ein

temp ≥ freezeThreshold → setBiome(seasons:winter_forest) mit temperature=0.7
  └── Vanilla Random-Tick taut Eis wieder auf
```

### 2.3 Was entfällt

- ❌ `IceEffect.java` als manueller Block-Scanner
- ❌ `setType(ICE)` / `setType(WATER)` via Plugin-Code
- ❌ State-Tracking für eingefrorene Blöcke
- ❌ Performance-Overhead durch Chunk-Iteration

### 2.4 Was bleibt / Edge Cases

| Szenario | Lösung |
|---|---|
| Stehende Gewässer | ✅ Vanilla Random-Tick |
| Flüsse (stehend) | ✅ Vanilla Random-Tick |
| Fließendes Wasser (Wasserfälle) | ❌ Friert Vanilla NIE ein → Dokumentiert als Known Limitation, später optional via `FlowingWaterFreezer` |
| Eis taut zurück | ✅ Vanilla Random-Tick bei `temperature > 0.15` |
| Verzögerung beim Frieren/Tauen | Akzeptiert – hängt von `randomTickSpeed` und Spielernähe ab |

### 2.5 Änderungen

| Datei | Änderung |
|---|---|
| `BiomeJsonGenerator.java` | Setzt `temperature` in Frost-Biome-JSONs auf Wert aus `frost.yml` |
| `frost.yml` | Neues Feld `biome-temperature: -0.5` |

---

## 3. MistEffect → Atmosphärischer Nebel + optionaler Partikel-Nebel

### 3.1 Aufteilung

| Aspekt | Mechanismus | Code-Aufwand |
|---|---|---|
| **Atmosphärischer Dunst** | `effects.fog_color` in Custom-Biome-JSONs (und optional `water_fog_color`, `sky_color`) | 0 neuer Code – nur Config + erweiterte JSON-Generierung |
| **Dichter Morgen-Nebel (optional)** | Partikel-System analog `FrostEffectManager` → `FogEffectManager` | ~100 Zeilen, kann später ergänzt werden |

### 3.2 Atmosphärischer Dunst (Phase 3)

Die Custom-Biome-JSONs bekommen saisonale `fog_color`-Werte aus `season_colors.yml`:

```yaml
defaults:
  fall:
    fog_color: 0xD0C8B8        # Leicht gräulicher Horizont-Dunst
    water_fog_color: 0x8B7D6B  # Dunkleres Wasser
  winter:
    fog_color: 0xC8D0D8        # Kaltes Blaugrau
  spring:
    fog_color: 0xC0D8FF        # Klares Hellblau
  summer:
    fog_color: 0xC0D8FF        # Vanilla-ähnlich
```

`BiomeJsonGenerator` schreibt diese Werte beim Generieren in die `effects`-Sektion der JSONs – keine Laufzeit-Logik, kein Extra-Timer.

**Fog-Effekt ist subtil:** Es färbt nur den atmosphärischen Horizont-Dunst, nicht die Sichtweite des Spielers. Perfekt für "Herbststimmung".

### 3.3 Partikel-Nebel (optional, später)

Für dichten Bodennebel (Morgenstunden, Herbst) kann später ein `FogEffectManager` analog zu `FrostEffectManager` implementiert werden:

- `CLOUD`- oder `ASH`-Partikel im Umkreis des Spielers
- Nur bei `season == FALL && isDawnDusk()`
- Config-gesteuert in `config.yml`

**Nicht Teil von Phase 3** – kommt in `Plannung/_TODO-future-features.md`.

---

## 4. TemperatureEffect – Spieler-Debuffs

### 4.1 Prinzip

Der einzige Effekt, der **zwingend eigenen Code** braucht: Potion-Effekte (`HUNGER`, `SLOWNESS`, `MINING_FATIGUE`) bei extremen Temperaturen.

### 4.2 Schwellwerte (konfigurierbar)

```yaml
temperature-effects:
  enabled: true
  interval-ticks: 40              # Alle 2 Sekunden prüfen
  cold:
    hunger-threshold: -0.2        # Leichter Hunger ab -0.2°C
    slowness-threshold: -0.5      # Slowness I ab -0.5°C
    mining-fatigue-threshold: -0.8 # Mining Fatigue ab -0.8°C
  heat:
    exhaustion-threshold: 0.8     # Hunger ab 0.8°C (Hitzewelle)
    slowness-threshold: 1.0       # Slowness bei extremer Hitze
```

### 4.3 Datenfluss

```
EffectScheduler (alle 40 Ticks)
  └── Für jeden Online-Spieler:
        └── temp = TemperatureCalculator.getTemperature(player.getLocation())
        └── Falls temp < hunger-threshold → addPotionEffect(HUNGER, ...)
        └── Falls temp < slowness-threshold → addPotionEffect(SLOWNESS, ...)
        └── Falls temp > exhaustion-threshold → addPotionEffect(HUNGER, ...)
```

### 4.4 Implementierung

- `TemperatureEffect.java` (NEU)
- `config.yml` erweitern um `temperature-effects`-Sektion
- Registrierung via `EffectScheduler` (siehe 5.)

---

## 5. SeasonalEffect-Interface + EffectScheduler

### 5.1 Motivation

`FrostEffectManager`, `TemperatureEffect` (und später `FogEffectManager`) haben alle dasselbe Muster:
- Periodischer Timer
- Pro-Spieler-Prüfung (Temperatur, Biom, Tageszeit)
- Potion-Effekte oder Partikel
- Cleanup bei `PlayerQuitEvent`

Ein **EffectScheduler** bündelt diese in einen einzigen Timer (20 Ticks), berechnet Temperatur+Season einmal pro Spieler und delegiert an alle registrierten Effekte.

### 5.2 Interface (schlank)

```java
interface SeasonalEffect {
    /**
     * Prüft ob der Effekt für diesen Spieler in dieser Situation aktiv sein soll.
     */
    boolean isApplicable(Player player, double temperature, Season season, long worldTime);

    /**
     * Wendet den Effekt an (Potion, Partikel, etc.).
     */
    void apply(Player player, double temperature, Season season);

    /**
     * Entfernt den Effekt (z.B. Potion-Effekt beim Season-Wechsel oder Quit).
     */
    void remove(Player player);
}
```

### 5.3 EffectScheduler

- Wird in `SeasonsPlugin.onEnable()` gestartet
- Hält eine `List<SeasonalEffect>`
- 20-Tick-BukkitRunnable (1 Sekunde)
- Pro Tick: Alle Online-Spieler → Temperatur+Season einmal berechnen → Effekte anwenden
- `PlayerQuitEvent`-Listener für `remove()`

### 5.4 Registrierte Effekte

| Effekt | Klasse | Intervall |
|---|---|---|
| Frost-Partikel | `FrostEffectManager` (umbauen auf `SeasonalEffect`) | Jeden Tick geprüft, aber `isApplicable` steuert Cooldown intern |
| Temperatur-Debuffs | `TemperatureEffect` (neu) | Jeden Tick |
| Kein `IceEffect` | Entfällt | — |
| Kein `FogEffectManager` | Später optional | — |

---

## 6. Überarbeitete Paketstruktur

```
effects/
├── SeasonalEffect.java         (Interface)
├── EffectScheduler.java        (Ein Timer, iteriert Spieler, delegiert)
├── FrostEffectManager.java     (umbauen: implements SeasonalEffect)
└── TemperatureEffect.java      (NEU: implements SeasonalEffect)

❌ IceEffect.java               GELÖSCHT
❌ MistEffect.java              GELÖSCHT (atmosphärischer Nebel via Biome, Partikel-Nebel später)
```

---

## 7. Betroffene Dateien (Übersicht)

| Datei | Änderung | Sprint |
|---|---|---|
| `BiomeJsonGenerator.java` | Erweitert: Setzt `temperature` und `fog_color`/`sky_color`/`water_fog_color` in JSONs | 3.1 |
| `season_colors.yml` | Erweitert: `fog_color`, `sky_color`, `water_fog_color` pro Saison (optional) | 3.1 |
| `frost.yml` | Erweitert: `biome-temperature` Feld | 3.1 |
| `SeasonalEffect.java` | NEU: Interface | 3.2 |
| `EffectScheduler.java` | NEU: Timer + Spieler-Iteration | 3.2 |
| `TemperatureEffect.java` | NEU: Potion-Debuffs bei extremen Temperaturen | 3.3 |
| `FrostEffectManager.java` | Umbauen: `implements SeasonalEffect` | 3.2 |
| `config.yml` | Erweitert: `temperature-effects`-Sektion | 3.4 |
| `SeasonsPlugin.java` | EffectScheduler starten, IceEffect-Referenzen entfernen | 3.5 |
| `Plannung/roadmap.md` | Phase 3 aktualisieren | 3.0 |
| `Plannung/_TODO-future-features.md` | Flowing-Water-Freezer + Partikel-Nebel ergänzen | 3.0 |

---

## 8. Sprint-Struktur (überarbeitet)

| Sprint | Feature | Neu? |
|---|---|---|
| 3.1 | BiomeJsonGenerator: `temperature` + `fog_color` in JSONs + Configs erweitern | Ersetzt IceEffect |
| 3.2 | SeasonalEffect-Interface + EffectScheduler + FrostEffectManager umbauen | Ersetzt altes 3.1 |
| 3.3 | TemperatureEffect – Spieler-Debuffs (Hunger/Slowness) | Wie ursprünglich 3.2 |
| 3.4 | Config-Erweiterung `temperature-effects` + `frost.yml` | Wie 3.5 |
| 3.5 | Integration, Build, Deploy & Test | Wie 3.6 |

**Reduktion:** 6 → 5 Sprints. Kein manueller Ice-Scan-Code. Kein MistEffect-Code (atmosphärisch über Biome, dichter Nebel auf später verschoben).

---

## 9. Done-Definition Phase 3 (überarbeitet)

- [ ] Frost-Biome haben `temperature < 0` → stehendes Wasser friert per Vanilla Random-Tick
- [ ] `fog_color` in Custom-Biome-JSONs gesetzt → atmosphärischer Nebel sichtbar (F3-Biom-Prüfung)
- [ ] Bei `temp < -0.2` effektiver Temperatur leichter Hunger-Effekt (konfigurierbar)
- [ ] Bei `temp < -0.5` Slowness I (konfigurierbar)
- [ ] Bei `temp > 0.8` Erschöpfungs-Hunger (konfigurierbar)
- [ ] `EffectScheduler` bündelt alle Effekte in EINEM Timer
- [ ] `FrostEffectManager` implementiert `SeasonalEffect` Interface
- [ ] Keine `IceEffect.java`, kein manuelles Block-Scannen
- [ ] Keine `MistEffect.java` (atmosphärischer Nebel via Biome, Partikel-Nebel in TODO)
- [ ] Keine NMS/Reflection
- [ ] Build erfolgreich

---

## 10. Risiken & Offene Fragen

| Frage | Einschätzung | Status |
|---|---|---|
| Friert Wasser wirklich via `setBiome()` mit `temperature < 0`? | ✅ Vanilla prüft `Biome#getTemperature()` im Random-Tick | Muss im PoC getestet werden |
| Wird `fog_color` zuverlässig vom Client gerendert? | ✅ Funktioniert in Custom-Datapacks seit 1.16+ | Muss im PoC getestet werden |
| Verzögerung beim Frieren/Tauen? | Akzeptiert – hängt von `randomTickSpeed` (default 3) und Spielernähe ab | Dokumentiert |
| Fließendes Wasser? | Bekannte Vanilla-Limitation → TODO-05 | Dokumentiert |
| Performance? | `EffectScheduler` spart Timer, kein zusätzlicher Overhead | Unkritisch |