# Konzept: Saisonale Biome via Custom-Biome-Datapack (v3)

**Erstellt:** Juli 2025
**Letzte Änderung:** 2025-07-01
**Status:** Konzept-Phase – Proof-of-Concept validiert ✅
**Betrifft:** Phase 2.5 (visuelle Biome-Farben)

---

## 1. Historie: Was wir probiert haben

| Ansatz | Ergebnis |
|--------|----------|
| FoliageTint / Packet-Override | ❌ Minecraft's Farb-Rendering ist zu komplex |
| Cancel + Refresh (`world.setBiome` + `refreshChunk`) | ❌ `refreshChunk()` sendet keine Biome-Daten |
| NMS-LevelChunk-Manipulation | ❌ `getBiomeStorage()` existiert nicht in Paper 1.21.5 |
| **Custom-Biome via Datapack + `world.setBiome()`** | ✅ **Funktioniert!** Farbwechsel im Proof-of-Concept sichtbar |

---

## 2. Proof-of-Concept: Ergebnisse

Am 2025-07-01 getestet:

- Datapack `seasons_biomes` mit einem Biom `seasons:fall_forest` auf dem Server registriert
- `world.setBiome()` via `BiomeSpoofAdapter.captureAndApply()` setzt das Custom-Biome
- Visueller Test: Farbwechsel sichtbar! Laub braun im Kernbereich des Waldes
- Randbereiche zeigten noch Original-Farben → **Biome-Blending** mit benachbarten, nicht gespooften Chunks
- `ConfigManager.getSeasonTargetBiome()` wurde erweitert: Fallback auf `Registry.BIOME.get(NamespacedKey)` für Custom-Biomes
- `biome_spoof.yml`: `FALL: seasons:fall_forest` wird korrekt aufgelöst

**Fazit:** Der Datapack-Ansatz funktioniert. Jetzt muss der Code aufgeräumt und das Konzept auf alle Biomes + Transition + Farbvarianz skaliert werden.

---

## 3. Sanierung: Was fliegt raus

### 3.1 `ChunkPacketInterceptor.java` – ❌ Komplett löschen

- 157 Zeilen toter Code: `applyBiomeToNmsChunk()` kehrt **immer** `return false` zurück
- Nie ein einziges Biome im Packet überschrieben
- Enthält NMS-Reflection-Zombies (`createBiomeHolder`, `registryAccess`, `getChunkSource`)
- Ist die **einzige Klasse**, die ProtocolLib importiert

### 3.2 ProtocolLib-Dependency – ❌ Entfernen

- `build.gradle.kts`: `compileOnly(\"com.comphenix.protocol:ProtocolLib:5.3.0\")` entfernen
- `build.gradle.kts`: `maven(\"https://repo.dmulloy2.net/repository/public/\")` entfernen
- `plugin.yml`: `softdepend: [ProtocolLib]` entfernen oder anpassen

### 3.3 `BiomeSpoofAdapter.java` – 🔄 Refactoring

Aktuell 400+ Zeilen, eine Klasse macht alles. Soll aufgeteilt werden in:

| Neue Klasse | Verantwortlichkeit |
|-------------|-------------------|
| `BiomeSpoofCoordinator` | Timer, Spieler-Loop, Chunk-Offsets, Budget |
| `SeasonBiomeResolver` | Biome-Klassifizierung (Land/Ozean/Kalt), Target-Auswahl **inkl. Sub-Varianten** |
| `ChunkBiomeApplier` | `captureAndApply()`, `revertChunk()`, `revertAll()` |
| `TransitionManager` | 🆕 Zustandsmaschine für Sub-Varianten-Wechsel (Nacht-Check, Stufen-Tracking) |

### 3.4 `BiomeBackupStore.java` – ✅ Bleibt mit minimaler Anpassung

- Speichert Original-Biome als `Biome.name()` in JSON
- Funktioniert auch mit Custom-Biomes (deren `name()` liefert NamespacedKey als String zurück)
- **Anpassung nötig:** `Biome.valueOf()` beim Laden durch Registry-Lookup ersetzen (Custom-Biomes haben keinen Vanilla-Enum-Wert)

### 3.5 `BiomeFamily.java` – ✅ Bleibt unverändert

### 3.6 `BiomeSpoofListener.java` – ✅ Bleibt, ggf. leichte Anpassungen

### 3.7 `SpoofMode.java` – ✅ Bleibt unverändert

---

## 4. Farbvarianz pro Biom (Neues Kernkonzept)

### 4.1 Problem

Im Proof-of-Concept wurde **ein einziges** Custom-Biome `seasons:fall_forest` für ALLE Wälder verwendet. Ein Birkenwald und ein Eichenwald sahen im Herbst identisch aus – die natürliche Vielfalt geht verloren.

**Ziel:** Jedes Vanilla-Biom bekommt eine eigene Saison-Variante, deren Farben aus den **Original-Farben des Bioms** abgeleitet und mit saisonalen Zielwerten gemischt werden.

### 4.2 Original-Farben als Basis

Jedes Vanilla-Biom hat in Minecrafts Definition feste `grass_color` und `foliage_color`-Werte. Diese werden **einmalig aus den Vanilla-Biome-JSONs extrahiert** und als Referenz gespeichert.

**Quelle:** `assets/minecraft/worldgen/biome/*.json` aus der Vanilla-Client-JAR (1.21.5). Einmaliger Dump, ~60 Dateien, ~2 MB. Ändert sich nur bei Major-Updates.

Beispiel:
```
minecraft:forest        → grass: 0x68B040, foliage: 0x3C9A1E
minecraft:birch_forest  → grass: 0x88BB66, foliage: 0x6BAA3A  (heller!)
minecraft:taiga         → grass: 0x5C9A32, foliage: 0x2C6E14  (dunkelgrün!)
```

### 4.3 Zielfarben aus Config

Statt Farbwerte in jede JSON-Datei zu schreiben, wird eine zentrale Config `season_colors.yml` zur Verfügung gestellt. Sie definiert für jede Saison Zielfarben – optional pro Biom überschreibbar.

Die **tatsächliche Biome-Farbe** entsteht durch **Interpolation** zwischen Original-Farbe und Ziel-Farbe.

### 4.4 Spezialfall Nadelbäume

Taiga und andere Nadelwälder bleiben im Winter **grün** (wie echte Nadelbäume). Das wird über einen Config-Override realisiert:

```yaml
biomes:
  minecraft:taiga:
    winter:
      foliage_color_target: 0x3C7010   # dunkelgrün, nicht grau!
```

---

## 5. Config: `season_colors.yml`

Die zentrale Steuerungsdatei für alle Biome-Farben.

```yaml
# season_colors.yml – Zentrale Farbsteuerung f~ur saisonale Biome

# Sub-Varianten pro ~Ubergang (kann pro ~Ubergang individuell sein)
transitions:
  default_steps: 4                  # Standard: 4 Sub-Varianten
  WINTER_TO_SPRING: 2
  SPRING_TO_SUMMER: 2
  SUMMER_TO_FALL: 4
  FALL_TO_WINTER: 3

# Standard-Zielfarben f~ur jede Saison (wenn kein Biom-Override)
defaults:
  spring:
    grass_color_blend: 0.0          # 0.0 = Original, 1.0 = Ziel (unten definiert)
    foliage_color_blend: 0.0        # Fr~uhling ~= Original
  summer:
    grass_color_blend: 0.0          # Sommer = Vanilla
    foliage_color_blend: 0.0
  fall:
    grass_color_target: 0xC27E22    # Goldbraun
    foliage_color_target: 0xC88814  # Orangebraun
  winter:
    grass_color_target: 0x808088    # Grau
    foliage_color_target: 0x70787A  # Grau

# Biom-spezifische Overrides (nur was vom Default abweicht)
biomes:
  minecraft:taiga:
    winter:
      grass_color_target: 0x68A030   # Nadelb~aume bleiben gr~un!
      foliage_color_target: 0x3C7010
  minecraft:snowy_taiga:
    winter:
      grass_color_target: 0x68A030
      foliage_color_target: 0x3C7010
  minecraft:old_growth_pine_taiga:
    winter:
      grass_color_target: 0x5C8A28
      foliage_color_target: 0x346010
  minecraft:old_growth_spruce_taiga:
    winter:
      grass_color_target: 0x5C8A28
      foliage_color_target: 0x346010
  minecraft:swamp:
    fall:
      grass_color_target: 0x8B6914   # Sumpf wird noch dunkler
      foliage_color_target: 0x6B4E14
  minecraft:mangrove_swamp:
    fall:
      grass_color_target: 0x8B6914
      foliage_color_target: 0x6B4E14
  minecraft:dark_forest:
    fall:
      grass_color_target: 0xA06020   # Dunkelwald dunkelbraun
      foliage_color_target: 0x8A3E10
  minecraft:plains:
    fall:
      grass_color_target: 0xD4A030   # Ebenen heller/goldiger
      foliage_color_target: 0xB88820

# Welche Biome ~uberhaupt saisonal angepasst werden sollen
# Alle Biomes, die nicht gelistet sind, behalten ihre Originalfarben
enabled_biomes:
  - minecraft:forest
  - minecraft:flower_forest
  - minecraft:birch_forest
  - minecraft:old_growth_birch_forest
  - minecraft:dark_forest
  - minecraft:taiga
  - minecraft:snowy_taiga
  - minecraft:old_growth_pine_taiga
  - minecraft:old_growth_spruce_taiga
  - minecraft:plains
  - minecraft:sunflower_plains
  - minecraft:meadow
  - minecraft:swamp
  - minecraft:mangrove_swamp
  - minecraft:river
  - minecraft:cherry_grove
  - minecraft:pale_garden
```

---

## 6. Farb-Interpolation

### 6.1 Formel

```
blended = lerp(original_color, target_color, blend_factor)
```

`blend_factor` bewegt sich von 0.0 (Original) bis 1.0 (Ziel). Für Sub-Varianten wird der Faktor gleichmäßig verteilt:

```
4 Sub-Varianten: t ∈ {0.25, 0.50, 0.75, 1.00}
```

### 6.2 Beispiel: FOREST Sommer → Herbst

```
original grass = 0x68B040 (dunkelgrün)
target  grass = 0xC27E22 (goldbraun)

late_summer  (t=0.25): 0x7EA939  → noch grünlich, leicht entsättigt
early_fall   (t=0.50): 0x94A332  → gelblich-grün
mid_fall     (t=0.75): 0xAA922B  → orange-gold
fall         (t=1.00): 0xC27E22  → goldbraun, Endzustand
```

---

## 7. Biome-JSON-Generator (Plugin-Komponente)

### 7.1 Prinzip

Der Generator ist **Teil des Plugins** und wird beim ersten Start oder per Command ausgeführt. Er liest:
- Die Vanilla-Biome-JSONs (als Referenzdatei im Plugin-JAR)
- `season_colors.yml` (aus `plugins/Seasons/`)
- `biome_spoof.yml` (für die Liste der zu behandelnden Biomes)

Und schreibt die generierten Custom-Biome-JSONs nach `world/datapacks/seasons_biomes/`.

### 7.2 Timing

```
Start 1 (frisch):  Plugin lädt → Generator erstellt JSONs → Datapack existiert noch nicht zur Welt-Ladezeit
                    → Biome bleiben Vanilla
Start 2:            Datapack existiert bereits → Welt lädt Custom-Biomes → Farben aktiv
```

**Kein Problem für ein privates Setup.** Änderungen an `season_colors.yml` werden mit `/season reload && /season generate-biomes` umgesetzt und sind nach dem nächsten Serverstart aktiv.

### 7.3 Hash-Check

Um unnötige Neugenerierung zu vermeiden, speichert das Plugin einen Hash der Config. Bei `onEnable`: Hash vergleichen → nur bei Änderung neu generieren. Ein `force`-Flag (per Command oder Config) kann die Generierung erzwingen.

### 7.4 Command

```
/season generate-biomes [force]
```

Führt den Generator sofort aus und loggt das Ergebnis. Nützlich vor einem geplanten Server-Restart.

---

## 8. Datapack-Struktur (generiert)

```
world/datapacks/seasons_biomes/
├── pack.mcmeta
├── data/
│   └── seasons/
│       └── worldgen/
│           └── biome/
│               ├── forest/
│               │   ├── early_spring.json
│               │   ├── spring.json
│               │   ├── early_summer.json
│               │   ├── summer.json
│               │   ├── late_summer.json
│               │   ├── early_fall.json
│               │   ├── mid_fall.json
│               │   ├── fall.json
│               │   ├── early_winter.json
│               │   ├── mid_winter.json
│               │   └── winter.json
│               ├── birch_forest/
│               │   └── ... (gleiche Struktur)
│               ├── taiga/
│               │   └── ...
│               ├── plains/
│               │   └── ...
│               ├── swamp/
│               │   └── ...
│               └── ... (f~ur jedes konfigurierte Biom)
```

**Größenordnung:** ~20 Biomes × ~11 Sub-Varianten = ~220 JSON-Dateien. Generator erzeugt sie in <1 Sekunde. Client-Performance nicht beeinträchtigt.

---

## 9. Neue Plugin-Architektur

```
visual/
├── BiomeFamily.java              ✅ unverändert
├── SpoofMode.java                ✅ unverändert
├── BiomeBackupStore.java         🔄 Registry-Lookup statt Biome.valueOf()
├── BiomeSpoofListener.java       🔄 Leichte Anpassungen
├── BiomeSpoofCoordinator.java    🆕 Timer, Spieler-Loop, Budget
├── SeasonBiomeResolver.java      🆕 Klassifizierung + Target + Sub-Varianten
├── ChunkBiomeApplier.java        🆕 captureAndApply/revert
├── TransitionManager.java        🆕 Nacht-Check, Stufen-Tracking
├── SeasonColorConfig.java        🆕 Liest season_colors.yml
├── VanillaBiomeReference.java    🆕 Hält Dump der Original-Biom-Farben (im JAR)
└── BiomeJsonGenerator.java       🆕 Erzeugt alle Custom-Biome-JSONs

❌ ChunkPacketInterceptor.java    GELÖSCHT
```

### 9.1 `SeasonColorConfig`

Liest `season_colors.yml`, stellt Default-Werte und Overrides bereit:

```java
public int getGrassColorTarget(Biome original, Season season);
public int getFoliageColorTarget(Biome original, Season season);
public int getTransitionSteps(Season from, Season to);
```

### 9.2 `VanillaBiomeReference`

Liest beim Plugin-Start die Vanilla-Biome-JSONs aus dem Plugin-JAR (Ressource `vanilla_biomes/`). Stellt pro `Biome` die originalen `grass_color` und `foliage_color` bereit. Fallback-Werte für unbekannte Biomes.

### 9.3 `BiomeJsonGenerator`

- Iteriert über alle `enabled_biomes` aus der Config
- Für jedes Biom und jeden Übergang: Interpoliert Farben für N Sub-Varianten
- Schreibt JSONs nach `world/datapacks/seasons_biomes/data/seasons/worldgen/biome/`
- Aktualisiert `pack.mcmeta` (pack_format)
- Loggt Zusammenfassung

---

## 10. Transition-Konzept: Sub-Varianten mit Nacht-Wechsel

### 10.1 Grundprinzip

Statt räumlichem Staffeln (Chunk für Chunk über Tage) wechseln **alle sichtbaren Chunks gleichzeitig** ihre Biome. Der sanfte Übergang entsteht durch mehrere **Farb-Zwischenstufen** (Sub-Varianten), die in aufeinanderfolgenden **Nächten** getauscht werden.

Weil Farbänderungen im Dunkeln kaum auffallen, merkt der Spieler den Wechsel erst am nächsten Morgen – und sieht eine einheitliche, leicht veränderte Landschaft.

### 10.2 Ablauf (Beispiel Sommer → Herbst mit 4 Stufen)

```
Nacht 1: Alle Chunks → seasons:late_summer_forest
Nacht 2: Alle Chunks → seasons:early_fall_forest
Nacht 3: Alle Chunks → seasons:mid_fall_forest
Nacht 4: Alle Chunks → seasons:fall_forest
```

### 10.3 TransitionManager – Zustandsmaschine

```java
class TransitionManager {
    Season fromSeason, toSeason;
    int totalSteps;
    int currentStep;               // 0 = noch nicht gestartet
    long nextTransitionTick;       // Welt-Tick f~ur n~achsten Schritt
    NamespacedKey[] stepBiomes;    // Sub-Varianten in Reihenfolge
}
```

**Trigger:** `SeasonChangeEvent` → TransitionManager initialisiert, setzt `nextTransitionTick` auf die nächste Nacht.

**Nacht-Check:** Alle 40 Ticks prüft `BiomeSpoofCoordinator`, ob die Nacht erreicht ist. Wenn ja: `currentStep++`, alle Chunks auf die nächste Sub-Variante aktualisieren.

---

## 11. `biome_spoof.yml` – Neue Struktur

```yaml
# Biome-Spoofing-Config v2
enabled: true
mode: GLOBAL_RING
radius_chunks: 8
budget_chunks_per_tick: 16

# Transition-Steuerung
transition:
  enabled: true
  nights_per_step: 1

# Ozean-Behandlung (unverändert)
oceans:
  enabled: true
  keep_deep_variants: true

# Welche Biomes vom Spoofing ausgeschlossen sind
excluded_biomes:
  - MUSHROOM_FIELDS
  - DEEP_DARK
  - THE_VOID

disabled_fx_worlds:
  - world_nether
  - world_the_end

disk_backup:
  enabled: true
```

**Wegfall der `seasons`- und `oceans.seasons`-Sektionen:**
Das alte Per-Family-Mapping (`SPRING: seasons:spring_forest` für ALLE Land-Biome) ist **entfernt**, weil es die Biome-Vielfalt zerstört und Gameplay-Nebenwirkungen hat (Mob-Spawns, Temperatur, Downfall ändern sich).

Stattdessen wird das Ziel-Biom **dynamisch aus dem Original-Biom abgeleitet** (siehe Abschnitt 11a). Die Liste der zu behandelnden Biomes steht ausschließlich in `season_colors.yml` (`enabled_biomes`).

---

## 11a. Namenskonvention für Custom-Biomes (verbindlich)

Jedes Vanilla-Biom bekommt eine **eigene** Custom-Biome-Familie. Der Name wird dynamisch gebildet:

```
Schema: seasons:<variant>_<biome_key>

Beispiele:
  seasons:fall_forest               (minecraft:forest im Herbst)
  seasons:fall_birch_forest         (minecraft:birch_forest im Herbst)
  seasons:fall_swamp                (minecraft:swamp im Herbst)
  seasons:fall_plains               (minecraft:plains im Herbst)
  seasons:early_fall_dark_forest    (minecraft:dark_forest, Sub-Variante)
  seasons:winter_taiga              (minecraft:taiga im Winter – Nadelbaum grün!)
```

**Regeln:**
- `<biome_key>` = `Biome.getKey().getKey()` (Kleinschreibung, Unterstriche), z.B. `birch_forest`, `dark_forest`
- `<variant>` = Saison-Name + ggf. Sub-Stufen-Präfix, z.B. `spring`, `early_fall`, `mid_winter`
- Der **Generator** (BiomeJsonGenerator) und der **Resolver** (SeasonBiomeResolver) sowie der **TransitionManager** verwenden **dieselbe Konvention** – keine manuelle Pflege von Mappings nötig

**Warum Per-Biome-Mapping?**

| Aspekt | Per-Family (alt) | Per-Biome (neu) |
|--------|-------------------|------------------|
| Mob-Spawns | ❌ Swamp verliert Slimes | ✅ Swamp behält Slimes |
| Temperatur | ❌ Taiga wird warm | ✅ Taiga bleibt kalt |
| Downfall/Precipitation | ❌ Wüste bekommt Regen | ✅ Wüste bleibt trocken |
| Spezialregeln | ❌ Mushroom Fields verliert No-Monster | ✅ Bleibt erhalten (wird ausgeschlossen) |
| Farbvielfalt | ❌ Alle Wälder gleiche Farbe | ✅ Birke heller, Eiche dunkler |

**Ozeane:** Für Ozeane gilt dasselbe Prinzip – `seasons:winter_ocean`, `seasons:fall_warm_ocean` etc. Der `SeasonBiomeResolver` prüft, ob das Original-Biom ein Ozean ist (`name.contains("OCEAN")`), und wählt dann das entsprechende Custom-Ozean-Biome. Deep-Varianten werden durch den `_deep_`-Präfix im Key automatisch erkannt (`deep_ocean`, `deep_cold_ocean` etc.).

---

## 12. Implementierungs-Phasen (aktualisiert)

### Phase 2.5a – Aufräumen ✅ geplant
1. `ChunkPacketInterceptor.java` löschen
2. ProtocolLib-Dependency entfernen (`build.gradle.kts`, `plugin.yml`)
3. `BiomeSpoofAdapter`-Referenzen auf Interceptor entfernen
4. Build & Deploy & Test (Plugin startet ohne ProtocolLib)

### Phase 2.5b – Refactoring `BiomeSpoofAdapter`
1. `SeasonColorConfig` implementieren (liest `season_colors.yml`)
2. `VanillaBiomeReference` implementieren (Dump der Original-Biom-Farben aus JAR)
3. `BiomeJsonGenerator` implementieren (erzeugt Datapack)
4. `SeasonBiomeResolver` extrahieren (Klassifizierung + Target)
5. `ChunkBiomeApplier` extrahieren (captureAndApply/revert)
6. `BiomeSpoofCoordinator` als dünne Koordinator-Klasse
7. `BiomeBackupStore.loadAll()`: `Biome.valueOf()` → Registry-Lookup
8. `/season generate-biomes` Command
9. Build & Deploy & Test (Datapack wird generiert, nach 2. Start aktiv)

### Phase 2.5c – Transition-Manager
1. `TransitionManager` implementieren (Nacht-Check, Stufen-Tracking)
2. Integration in `BiomeSpoofCoordinator`
3. Test: Vollständiger Saison-Zyklus mit `/season skip`

### Phase 2.5d – Datapack-Ausbau & Feinschliff
1. `season_colors.yml` final befüllen (alle relevanten Biomes)
2. Ozean-Biome-Varianten definieren
3. Visueller Test aller Saisons
4. F3-Debug-Biom-Anzeige dokumentieren (akzeptierter Kompromiss)

---

## 13. Neue Datei-Übersicht

| Datei | Neu/Geändert | Zweck |
|-------|-------------|-------|
| `season_colors.yml` | 🆕 | Zentrale Farbsteuerung |
| `vanilla_biomes/` (JAR-Ressource) | 🆕 | Dump der Original-Biome-JSONs |
| `BiomeJsonGenerator.java` | 🆕 | Generiert Datapack-JSONs |
| `SeasonColorConfig.java` | 🆕 | Liest `season_colors.yml` |
| `VanillaBiomeReference.java` | 🆕 | Stellt Original-Farben bereit |
| `BiomeSpoofCoordinator.java` | 🆕 | Timer, Koordination |
| `SeasonBiomeResolver.java` | 🆕 | Target- + Sub-Varianten-Auswahl |
| `ChunkBiomeApplier.java` | 🆕 | Biome anwenden/zurücksetzen |
| `TransitionManager.java` | 🆕 | Nacht-Wechsel-Logik |
| `BiomeBackupStore.java` | 🔄 | Registry-Lookup statt `valueOf()` |
| `ChunkPacketInterceptor.java` | ❌ | Gelöscht |
| `BiomeSpoofAdapter.java` | ❌ | Aufgeteilt |

---

## 14. Risiken & Offene Fragen

| Risiko | Mitigation | Status |
|--------|------------|--------|
| Custom-Biome werden nicht gerendert | ✅ PoC bestätigt | Erledigt |
| `world.setBiome()` mit Custom-Biomes | ✅ PoC bestätigt | Erledigt |
| Generator läuft zu spät (Start 1) | Akzeptiert: Start 2 aktiv | Akzeptiert |
| 220 JSON-Dateien Wartungsaufwand | Generator aus Config, kein Handaufwand | Gelöst |
| `BiomeBackupStore` kann Custom-Biomes nicht laden | Registry-Lookup statt `valueOf()` | Phase 2.5b |
| Nächtlicher Bulk-Wechsel verursacht Lag | Budget-Mechanismus existiert bereits | Prüfen |
| F3-Debug zeigt falsches Biom | Akzeptabel | Akzeptiert |
| Ozean-Biome brauchen eigene Farben | Separate Sektion in `season_colors.yml` | Phase 2.5d |
| ProtocolLib-Abhängigkeit komplett entfernt | `ChunkPacketInterceptor` fliegt raus | Phase 2.5a |
| Generator kopiert nicht alle JSON-Felder | Muss **komplette 1:1-Kopie** sein (temperature, downfall, precipitation, effects, carvers, features, spawn_costs, spawners). Nur grass_color/foliage_color überschreiben. | In Konzept nachgeschärft |
| Himmel-/Wasser-Farben fehlen | sky_color, water_color, water_fog_color, fog_color in season_colors.yml ergänzbar | ➡️ Plannung/_TODO-future-features.md TODO-01 |
| Schneefall-Wolken fehlen | Echter Schnee-Wetter-Typ statt clear+Partikel-Krücke | ➡️ Plannung/_TODO-future-features.md TODO-02 |
| grass_color_modifier bei Spezial-Biomen | Cherry Grove, Pale Garden nutzen modifier statt festen color-Wert | ➡️ Plannung/_TODO-future-features.md TODO-03 |"