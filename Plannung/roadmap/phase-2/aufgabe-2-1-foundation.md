---
title: "Arbeitsauftrag: Phase 2.1 – Foundation (NmsAdapter, VisualConfig, ColorCalculator)"
quelle: "roadmap.md → Phase 2, Sprint 2.1"
related-roadmap: "Plannung/roadmap.md"
created: "2025-02-20"
status: pending
---

# Arbeitsauftrag: Phase 2.1 – Foundation (NmsAdapter, VisualConfig, ColorCalculator)

**Quelle:** roadmap.md → Phase 2, Sprint 2.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Fundament für das Visual-Seasons-System legen: NMS-Abstraktion, Config-Loader für `foliage_tints.yml` und Farbinterpolations-Rechner erstellen. Keine aktiven Spieler-Updates in diesem Sprint – nur die drei Infrastruktur-Klassen.

## Aktuelles Ergebnis
- Phase 1 (MVP) ist fertig: Jahreszeiten-Kreislauf, Schnee-Akkumulation, Weather-Interception laufen.
- Phase 1a/1b (Snow Growth/Melting) sind in Arbeit/geplant.
- Visual-System ist komplett neu, kein bestehender Code vorhanden.
- Keine NMS/Reflection bisher im Projekt – Phase 2 ist der erste Einsatz.

## Ursachenverdacht
Nicht anwendbar (neues Feature, kein Bugfix).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | Abstrakte Basisklasse für versionierte NMS-Packet-Manipulation |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter_v1_21_5.java` | Konkrete Implementierung für Paper 1.21.5 (PacketPlayOutMapChunk) |
| `src/main/java/de/ajsch/seasons/visual/VisualConfig.java` | Lädt und wrappt `foliage_tints.yml` |
| `src/main/java/de/ajsch/seasons/visual/ColorCalculator.java` | Lineare Interpolation zwischen Saison-Farben, Biome-Multiplier |
| `src/main/resources/foliage_tints.yml` | Default-Config mit Season-Farben pro Baumart |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Registriert `foliage_tints.yml` als neue Config-Ressource |

## Erbetene Hilfe

1. **`foliage_tints.yml` erstellen** – Default-Config mit vollständiger Saison-Farbpalette (SUMMER, FALL, WINTER, SPRING) inkl. `transition-days`, `default-tint` und `overrides` für Birch, Oak, Dark Oak, Cherry, Spruce, Jungle, Acacia, Mangrove laut Konzept.

2. **`NmsAdapter.java` (abstrakt) + `NmsAdapter_v1_21_5.java` implementieren**
   - Abstrakte Methoden: `sendBiomeTint(Player, BiomeBase, int foliageColor, int grassColor)`, `onEnable()`, `onDisable()`
   - Versionierte Implementierung: Paper 1.21.5 – PacketPlayOutMapChunk abfangen und Biome-Farben überschreiben
   - Fallback: Bei Fehlern oder nicht unterstützten Versionen sauber loggen, keine Exception-Würfe
   - Kein `sendBlockChange()` – nur Packet-Overrides

3. **`VisualConfig.java` implementieren**
   - Lädt `plugins/Seasons/foliage_tints.yml`
   - Wrappt `foliage.seasons.<SAISON>.default-tint`, `overrides`, `transition-days`
   - Getter: `getDefaultTint(Season)`, `getOverrideTint(Season, Biome)`, `getTransitionDays()`
   - Bei fehlender Config sinnvolle Defaults hart-codiert (Fallback-Farben)

4. **`ColorCalculator.java` implementieren**
   - `interpolate(int colorA, int colorB, double factor)` → RGB-Kanal-weise lineare Interpolation
   - `calculateSeasonalColor(Season current, Season target, double transitionProgress, Biome biome, VisualConfig config)` → liefert finalen `int`-Farbwert
   - Biome-Multiplier einbauen (optional in 2.1, Pflicht ab 2.2): `getBiomeMultiplier(Biome)` → 1.0 Default, später per Config

5. **`ConfigManager.java` erweitern** – `foliage_tints.yml` in die Liste der zu kopierenden Ressourcen und zu ladenden Configs aufnehmen.

6. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

7. **Deployment:** JAR per SCP kopieren, dann `sudo systemctl restart crafty`

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `foliage_tints.yml` Overrides steuern – keine Enum-Switches
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **NMS/Reflection erst ab Phase 2 erlaubt:** Package `nms/` klar kapseln, Adapter-Pattern nutzen
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`