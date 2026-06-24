---
title: "Arbeitsauftrag: Phase 2.6c – BiomeJsonGenerator + /season generate-biomes Command"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6c"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6c – BiomeJsonGenerator + /season generate-biomes Command

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6c

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
**`BiomeJsonGenerator.java`** – Neue Klasse im `visual`-Package. Generiert alle Custom-Biome-JSONs für das Datapack `seasons_biomes` und schreibt sie nach `world/datapacks/seasons_biomes/`.

**`/season generate-biomes [force]`** – Command-Erweiterung in `MainSeasonCommand`, die den Generator sofort ausführt.

## Aktuelles Ergebnis
- Datapack existiert nur als manueller Proof-of-Concept (`datapacks/seasons_biomes/`) mit EINER Biome-JSON (`fall_forest.json`)
- Kein Generator, keine automatische Erstellung aus Config
- Keine Farb-Interpolation, keine Sub-Varianten

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeJsonGenerator.java` | 🆕 Neu: Generator-Logik |
| `src/main/java/de/ajsch/seasons/commands/MainSeasonCommand.java` | 🔄 Erweitern: `generate-biomes` Subcommand |
| `world/datapacks/seasons_biomes/` (Server-Pfad) | 🔄 Zielverzeichnis für generierte JSONs |
| `src/main/java/de/ajsch/seasons/visual/VanillaBiomeReference.java` | Nutzt aus 2.6b |
| `src/main/java/de/ajsch/seasons/visual/SeasonColorConfig.java` | Nutzt aus 2.6b |

## Erbetene Hilfe
1. **`BiomeJsonGenerator.java` implementieren:**
   - Konstruktor: `BiomeJsonGenerator(SeasonsPlugin, SeasonColorConfig, VanillaBiomeReference)`
   - `generate(boolean force)`: Hauptmethode
     - Hash der `season_colors.yml` berechnen → mit gespeichertem Hash vergleichen → nur bei Änderung (oder force) generieren
     - Für jedes Vanilla-Biom in `SeasonColorConfig.getEnabledBiomes()` (z.B. `minecraft:forest`, `minecraft:swamp`):
       - Vanilla-Original-JSON aus `VanillaBiomeReference` als Basis laden (komplette Original-JSON)
       - **1:1-Kopie:** ALLE Felder (temperature, downfall, precipitation, effects, carvers, features, spawn_costs, spawners) unverändert übernehmen
       - **AUSSCHLIESSLICH** `effects.grass_color` und `effects.foliage_color` mit interpolierten Werten überschreiben
       - Sub-Varianten pro Übergang erzeugen (SUMMER→FALL, FALL→WINTER, WINTER→SPRING, SPRING→SUMMER)
         mit N Sub-Varianten aus `SeasonColorConfig.getTransitionSteps(from, to)`
       - **Namenskonvention (muss mit Resolver + TransitionManager identisch sein):**
         `seasons:<variant>_<biome_key>`
         - `<biome_key>` = `Biome.getKey().getKey()` – Kleinschreibung, Unterstriche (z.B. `birch_forest`, `dark_forest`)
         - `<variant>` = z.B. `spring`, `early_fall`, `mid_fall`, `fall`, `winter`
         - Dateipfad: `world/datapacks/seasons_biomes/data/seasons/worldgen/biome/<biome_key>/<variant>.json`
       - Nadelbaum-Overrides aus `season_colors.yml` `biomes.<key>.<season>` beachten
     - `pack.mcmeta` aktualisieren (pack_format = aktuell, description)
     - Hash speichern für nächsten Vergleich
     - Log: Anzahl generierter JSONs, Liste der Biomes
2. **Farb-Interpolation implementieren:**
   - `lerpColor(int original, int target, double factor)` → faktor auf R/G/B-Kanäle anwenden
   - `blendFactor` pro Schritt: `step / totalSteps` (1.0 / 4 = 0.25, usw.)
   - Nadelbaum-Overrides aus `season_colors.yml` beachten
3. **Vollständige 1:1-Kopie je JSON:** JEDE generierte JSON ist eine exakte Kopie des Vanilla-Originals. NUR `grass_color` und `foliage_color` werden überschrieben. Keine anderen Felder anfassen.
4. **`/season generate-biomes [force]` implementieren:**
   - Subcommand in `MainSeasonCommand.java` registrieren
   - Permission: `seasons.command.generate` (oder `seasons.admin`)
   - Ruft `BiomeJsonGenerator.generate(force)` auf
   - Log-Ausgabe: Anzahl generierter JSONs, Hash-Status
5. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
6. **Deployment:** JAR kopieren, Server restart → Generator wird NICHT automatisch in onEnable ausgeführt (erst auf Command)
7. **Test:** `/season generate-biomes` auf Server ausführen → prüfen ob Dateien in `world/datapacks/seasons_biomes/` erscheinen
8. Sync: `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über Config-Dateien kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
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
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`