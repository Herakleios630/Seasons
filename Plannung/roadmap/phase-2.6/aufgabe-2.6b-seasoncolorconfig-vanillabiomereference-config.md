---
title: "Arbeitsauftrag: Phase 2.6b – SeasonColorConfig + VanillaBiomeReference + season_colors.yml"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6b"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6b – SeasonColorConfig + VanillaBiomeReference + season_colors.yml

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6b

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
**A) `SeasonColorConfig.java`** – Neue Klasse im `visual`-Package, die `season_colors.yml` lädt und wrapped. Stellt Methoden bereit:
- `getGrassColorTarget(Biome original, Season season)` – liefert RGB-Zielwert (Default oder Biom-Override)
- `getFoliageColorTarget(Biome original, Season season)` – dito für Laub
- `getBlendFactor(Season forSeason)` – liefert `blend_factor` für Saison (bei SPRING/SUMMER = 0.0)
- `getTransitionSteps(Season from, Season to)` – Anzahl Sub-Varianten (Default aus `transitions.default_steps`, pro Übergang überschreibbar)
- `getEnabledBiomes()` – Liste aller Biomes, für die Custom-Biomes generiert werden sollen
- `hasBiomeOverride(Biome biome, Season season)` – ob ein Biom jeweils eigenen Eintrag hat

**B) `VanillaBiomeReference.java`** – Neue Klasse, die beim Plugin-Start die Vanilla-Biome-JSONs aus dem Plugin-JAR liest (Ressource `vanilla_biomes/`). Stellt pro `Biome` die originalen `grass_color` und `foliage_color` bereit.
- `loadFromResources(Plugin plugin)` – liest alle JSONs aus dem JAR-Ordner `vanilla_biomes/`
- `getGrassColor(Biome biome)` – liefert Vanilla-Farbwert (RGB int)
- `getFoliageColor(Biome biome)` – dito für foliage
- `hasColor(Biome biome)` – ob Daten für dieses Biom existieren
- Fallback-Werte für unbekannte Biomes (z.B. `0x68B040` / `0x3C9A1E` = Forest)

**C) `season_colors.yml`** – Neue Config-Ressource anlegen (wie im Konzept definiert), wird via `ResourceCopier` nach `plugins/Seasons/` kopiert.

## Aktuelles Ergebnis
- Keine Farb-Config existiert – alle Saison-→Biome-Mappings laufen hart über `Biome.valueOf()` in `biome_spoof.yml`
- Keine Vanilla-Farb-Referenz – keine Möglichkeit, interpolierte Farbwerte zu berechnen
- `ConfigManager` kennt nur `biome_spoof.yml`, nicht `season_colors.yml`

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/SeasonColorConfig.java` | 🆕 Neu: Liest `season_colors.yml` |
| `src/main/java/de/ajsch/seasons/visual/VanillaBiomeReference.java` | 🆕 Neu: Liest Vanilla-Biome-JSONs aus JAR |
| `src/main/resources/season_colors.yml` | 🆕 Neu: Zentrale Farbsteuerung |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | 🔄 Erweitern: `season_colors.yml` registrieren |
| `src/main/java/de/ajsch/seasons/config/ResourceCopier.java` | 🔄 Erweitern: `season_colors.yml` kopieren |
| `vanilla_biomes/*.json` | 🆕 JAR-Ressource: Dump der Original-Biome-JSONs |

## Erbetene Hilfe
1. [x] **Vanilla-Biome-JSONs beschaffen:** 65 Biome-JSONs aus Vanilla-26.1.2-JAR extrahiert (`data/minecraft/worldgen/biome/*.json`) und in `src/main/resources/vanilla_biomes/` abgelegt
2. [x] **`season_colors.yml` anlegen** – Vollständige Config nach Konzept (defaults + alle Nadelbaum-Overrides + enabled_biomes-Liste)
3. [x] **`ResourceCopier.java` erweitern:** `season_colors.yml` in die Liste der zu kopierenden Ressourcen aufgenommen
4. [x] **`ConfigManager.java` erweitern:**
   - `getSeasonColors()` – Lazy-Init + `reloadFromConfig()`
   - `reloadAll()` ruft `SeasonColorConfig.reloadFromConfig()` auf
5. [x] **`SeasonColorConfig.java` implementieren:**
   - Liest YAML via SnakeYAML (Bukkit-intern)
   - Parst `defaults.<season>.grass_color_target` / `foliage_color_target`
   - Parst `biomes.<namespace:key>.<season>.grass_color_target` / `foliage_color_target`
   - Parst `transitions.*`
   - Parst `enabled_biomes`
6. [x] **`VanillaBiomeReference.java` implementieren:**
   - `loadFromResources(Plugin plugin)`: Iteriert über `Biome.values()`, parst jede JSON via Gson, extrahiert `effects.grass_color` und `effects.foliage_color`
   - `getGrassColor(Biome)`: Lookup in interner Map, Fallback auf Forest-Farben
   - `getFoliageColor(Biome)`: dito
7. [ ] **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
8. [ ] **Deployment:** JAR + `season_colors.yml` kopieren, Server restart
9. [ ] Test: Nach Start muss im Log erscheinen, wie viele Vanilla-Biomes geladen wurden (z.B. "VanillaBiomeReference: 62 Biomes geladen")
10. [ ] Sync: `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über Config-Dateien kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn Configs geändert: `scp src\main\resources\season_colors.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/season_colors.yml"`
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`