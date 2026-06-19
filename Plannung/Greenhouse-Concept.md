# Gewächshäuser & Saisonales Pflanzenwachstum – Konzept

> Ausgearbeitete Spec für die Seasons-Plugin-Erweiterung.
> Stand: noch nicht in Roadmap eingepflegt – Ziel: Phase 3 (Temperatur-Effekte) ergänzen.

---

## 1. Saisonales Pflanzenwachstum

### 1.1 Grundidee
Pflanzen wachsen temperaturabhängig schneller oder langsamer. Über ein Jahr gemittelt bleibt der Ertrag identisch zu Vanilla.

### 1.2 Saisonale Wachstums-Modifier

| Season | Default-Modifier | Effekt |
|---|---|---|
| Frühling | 1.0 | Normal |
| Sommer | 1.75 | Deutlich schneller |
| Herbst | 1.0 | Normal |
| Winter | 0.5 | Halbe Geschwindigkeit |

### 1.3 Betroffene Pflanzen
Konfigurierbare Liste. Default:
`WHEAT`, `CARROTS`, `POTATOES`, `BEETROOTS`, `MELON_STEM`, `PUMPKIN_STEM`, `SWEET_BERRY_BUSH`, `COCOA`

Nicht betroffen (diskutabel):
`SUGAR_CANE` (Wasserpflanze), `CACTUS` (Wüste), `NETHER_WART` (Nether), `CHORUS_FLOWER` (End), `BAMBOO`, `KELP`

### 1.4 Hook
`BlockGrowEvent` oder Paper's `CropGrowthEvent`:
- Prüfen: Ist der Crop in einer registrierten `GreenhouseRegion`? Wenn ja → Gewächshaus-Bonus addieren.
- Sonst: Nur saisonalen Modifier anwenden.
- Umsetzung: `Age` mit berechneter Wahrscheinlichkeit ein zweites Mal inkrementieren, oder BoneMeal-artiger Minibonus.

---

## 2. Gewächshäuser

### 2.1 Grundkonzept
- Berechnet vom **Glas aus**, nicht von den Pflanzen.
- Spieler bauen ein Gewächshaus aus Glas → Pflanzen drinnen erhalten Wachstumsbonus.
- Erkennung läuft **einmal pro Ingame-Tag** (morgens, konfigurierbar).

### 2.2 Definition – Was ist ein Gewächshaus?

| Eigenschaft | Regel |
|---|---|
| **Dach** | Mindestens 3×3 horizontal zusammenhängende Glasfläche |
| **Grundfläche** | Mindestens 3×3 Innenraum (9 Blöcke), beliebige Form (auch L‑förmig) |
| **Wandhöhe** | 2 bis 5 Blöcke Innenraumhöhe |
| **Glasanteil** | ≥80 % aller Wand‑ + Dachblöcke müssen Glas sein |
| **Sonstige Blöcke** | Erlaubt (Rahmen, Türen, Naturstein) – zählen zu den 20 % |
| **Luft in Wänden** | Erlaubt (offene Fenster), zählt als Nicht‑Glas‑Wandblock |
| **Über dem Dach** | Nur Himmel / transparente Blöcke (keine soliden Blöcke) |
| **Bodenbelag** | Egal (Stein, Erde, Holz – alles okay) |
| **Geteilte Wände** | Erlaubt zwischen benachbarten Gewächshäusern |
| **Überlappung** | Größeres gewinnt; disjunkte Gewächshäuser im selben Chunk zählen einzeln |

### 2.3 Glas-Typen
Alle Glasblöcke, die **kein Tageslicht blocken** (`blockLightLevel ≥ 15`).
Per Config-Liste – Default:

```
GLASS, WHITE_STAINED_GLASS, ORANGE_STAINED_GLASS, MAGENTA_STAINED_GLASS,
LIGHT_BLUE_STAINED_GLASS, YELLOW_STAINED_GLASS, LIME_STAINED_GLASS,
PINK_STAINED_GLASS, GRAY_STAINED_GLASS, LIGHT_GRAY_STAINED_GLASS,
CYAN_STAINED_GLASS, PURPLE_STAINED_GLASS, BLUE_STAINED_GLASS,
BROWN_STAINED_GLASS, GREEN_STAINED_GLASS, RED_STAINED_GLASS,
BLACK_STAINED_GLASS   (alle Glasscheiben-Varianten analog)
```

Nicht enthalten: `TINTED_GLASS` (blockt Licht).

### 2.4 Gewächshaus-Bonus
- **+0.5 additiv** zum saisonalen Wachstums-Modifier.
- Winter: 0.5 + 0.5 = **1.0** (Normalgeschwindigkeit)
- Sommer: 1.75 + 0.5 = **2.25** (sehr schnell)
- Alles über Config einstellbar.

### 2.5 Partikel-Indikator
- Im aktiven Gewächshaus: Alle 5 Sekunden grüne Spore-Partikel (`COMPOSTER` oder `HAPPY_VILLAGER`) an zufälliger Innenraum-Position.
- Sichtbar für Spieler innerhalb 32 Blöcke.
- Dient als Feedback – Spieler sieht sofort ob das Gewächshaus "funktioniert".

---

## 3. Erkennungsalgorithmus

### 3.1 Phase A – Dach-Kandidaten finden
1. Chunk von oben nach unten scannen.
2. Jeder Glasblock (laut Config) triggert horizontales Flood-Fill auf **gleicher Y‑Ebene**.
3. Flood-Fill erfasst nur Glasblöcke gleicher Höhe.
4. Ist die Fläche ≥ `min_roof_size` (Default 9) → **Dach-Kandidat**.

### 3.2 Phase B – Innenraum aus Dach-Kandidat ableiten
1. Suche innerhalb des Dach-Kandidaten einen 3×3-Teilbereich.
2. Von diesem Teilbereich aus: **3D‑Flood‑Fill nach unten**, das nur **Luftblöcke** erfasst.
3. Maximal 5 Blöcke nach unten, mindestens 2.
4. Auf jeder Y‑Ebene horizontal wachsen lassen → erlaubt L‑förmige und organische Grundrisse.
5. Ergebnis: Menge aller Luftblöcke = **Innenraum**.

### 3.3 Phase C – Außenkontur & Glasanteil prüfen
1. Alle Blöcke, die direkt an den Innenraum grenzen (6‑face‑Nachbarn) und nicht Teil des Innenraums sind = **Außenkontur**.
2. Aufteilen in: Wände (Y unterhalb Dach, oberhalb Boden) und Dach (Y = Dachhöhe).
3. Boden wird **ignoriert**.
4. Zählen: `total_wand_dach_blöcke` vs `glass_blöcke`.
5. Wenn `glass_blöcke / total_wand_dach_blöcke ≥ 0.80` → gültig.

### 3.4 Phase D – Himmels-Sicht
1. Von jedem Block des Daches Raycast nach oben bis `world.getMaxHeight()`.
2. Einzige erlaubte Blöcke: Luft, Glas, andere transparente Blöcke.
3. Wenn irgendwo ein solider Block → kein Gewächshaus.

### 3.5 Phase E – Registrierung & Cache
- Gültige Region → `GreenhouseRegion` in `Map<ChunkKey, Set<GreenhouseRegion>>` speichern.
- **Cache-Invalidierung:** Bei `BlockPlaceEvent` / `BlockBreakEvent` im Chunk → für Neu‑Scan beim nächsten Morgen vormerken.
- **Kein sofortiger Re‑Scan** – Scan nur 1× täglich.

---

## 4. Datenstruktur

```java
public class GreenhouseRegion {
    private final String worldName;
    private final Set<BlockPos> interiorBlocks;   // alle (x,y,z) im Innenraum
    private final int minX, maxX, minY, maxY, minZ, maxZ;
    private final double glassRatio;

    public boolean contains(BlockPos pos) {
        return interiorBlocks.contains(pos);
    }
}
```

---

## 5. Config-Struktur

```yaml
# ─── Saisonales Wachstum ───
crop_growth:
  enabled: true
  seasonal_modifiers:
    SPRING: 1.0
    SUMMER: 1.75
    FALL: 1.0
    WINTER: 0.5
  affected_crops:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    - MELON_STEM
    - PUMPKIN_STEM
    - SWEET_BERRY_BUSH
    - COCOA

# ─── Gewächshäuser ───
greenhouse:
  enabled: true
  scan_at_day_start: true               # 1× täglich morgens
  min_roof_size: 9                      # zusammenhängende Glasfläche ≥ 9
  min_interior_area: 9                  # 3×3
  min_wall_height: 2
  max_wall_height: 5
  min_glass_ratio: 0.80                 # ≥ 80 %
  growth_bonus: 0.5                     # additiv zum saisonalen Modifier
  particle_interval_ticks: 100          # 5 Sekunden
  glass_blocks:
    - GLASS
    - WHITE_STAINED_GLASS
    - ORANGE_STAINED_GLASS
    - MAGENTA_STAINED_GLASS
    - LIGHT_BLUE_STAINED_GLASS
    - YELLOW_STAINED_GLASS
    - LIME_STAINED_GLASS
    - PINK_STAINED_GLASS
    - GRAY_STAINED_GLASS
    - LIGHT_GRAY_STAINED_GLASS
    - CYAN_STAINED_GLASS
    - PURPLE_STAINED_GLASS
    - BLUE_STAINED_GLASS
    - BROWN_STAINED_GLASS
    - GREEN_STAINED_GLASS
    - RED_STAINED_GLASS
    - BLACK_STAINED_GLASS
    # auch Glasscheiben (GLASS_PANE etc.) können ergänzt werden
  # glass_blocks in der Config sind die einzige Quelle –
  # kein Hardcoding im Java-Code.
```

---

## 6. Paketstruktur (neue Klassen)

```
┌── de.ajsch.seasons/
│   ├── greenhouse/
│   │   ├── GreenhouseScanner.java      (Erkennungsalgorithmus A–E)
│   │   ├── GreenhouseRegion.java       (Datenmodell)
│   │   ├── GreenhouseCache.java        (Map<ChunkKey, Set<GreenhouseRegion>>)
│   │   ├── GreenhouseConfig.java       (Config-Wrapper)
│   │   └── GreenhouseParticleTask.java (Partikel-Indikator)
│   │
│   ├── crop/                                  (neu)
│   │   ├── CropGrowthModifier.java            (Saisonaler Modifier)
│   │   ├── CropGrowthConfig.java              (Config-Wrapper)
│   │   └── CropGrowthListener.java            (BlockGrowEvent-Hook)
```

---

## 7. Abhängigkeiten

| Benötigt von Phase | Klasse |
|---|---|
| Phase 1 (SeasonClock) | `SeasonClock.getCurrentSeason()` für CropGrowth |
| Phase 1 (TemperatureCalculator) | `TemperatureCalculator.calculate()` für saisonalen Modifier |
| Keine NMS/Reflection | Reine Paper-API in Phase 1 |

Passt daher als Erweiterung in **Phase 3** (Temperatur-Effekte), da dort der `TemperatureCalculator` schon vollständig ist.

---

## 8. Offene Fragen & Entscheidungspunkte

| Frage | Status |
|---|---|
| Erkennungsalgorithmus | ✅ Flood-Fill mit 3×3-Anker |
| Bonus-Berechnung | ✅ Additiv +0.5 |
| Lichtlevel-Check | ✅ Nein |
| Scan-Zeitpunkt | ✅ 1× täglich morgens |
| Geteilte Wände | ✅ Erlaubt |
| Boden-Anforderung | ✅ Keine |
| Einordnung in Roadmap | ⬜ Noch offen – Vorschlag: Phase 3.5/3.6 |