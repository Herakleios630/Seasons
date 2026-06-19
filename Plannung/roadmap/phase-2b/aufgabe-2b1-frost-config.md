---
title: "Arbeitsauftrag: Frost-Config & frost.yml"
quelle: "roadmap.md → Phase 2b, Sprint 2b.1"
related-roadmap: "Plannung/roadmap.md → Phase 2b: Frost System – Temperaturabhängiger Frost (Tint-Lerp + Partikel)"
created: "2026-01-20"
status: in-progress
---

# Arbeitsauftrag: Frost-Config & frost.yml

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Frost-Config-Infrastruktur für Phase 2b aufbauen:
1. `frost.yml` als neue Config-Datei mit allen in `Plannung/frost-concept-phase-2b.md` definierten Werten erstellen
2. `FrostConfig.java` als Config-Wrapper-Klasse implementieren
3. `ConfigManager.java` erweitern, um `frost.yml` zu laden und als Resource bereitzustellen
4. `ResourceCopier.java` prüfen, ob `frost.yml` beim ersten Start aus dem JAR extrahiert wird

## Aktuelles Ergebnis
- Phase 1 (MVP) ist abgeschlossen – Schnee-Akkumulation, Season-Clock, Temperaturberechnung funktionieren
- Phase 2 (Foliage Tints) ist Voraussetzung – `VisualSeasonManager`, `FoliageTintManager`, `VisualConfig`, `NmsAdapter` sind vorhanden
- Es existiert noch **keine** `frost.yml` und **keine** `FrostConfig`-Klasse
- `ConfigManager.java` lädt derzeit `config.yml`, `precipitation_categories.yml`, `visual.yml`

## Ursachenverdacht
Kein Fehler – reine Neuentwicklung. Risiko: Vergessen, `frost.yml` im `ResourceCopier` zu registrieren, sodass die Datei beim ersten Start nicht extrahiert wird.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/frost.yml` | Neue Default-Config für Frost-Effekte |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | Config-Wrapper, lädt und validiert `frost.yml` |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Erweiterung: `frost.yml` laden und als Resource registrieren |
| `src/main/java/de/ajsch/seasons/config/ResourceCopier.java` | Prüfung: `frost.yml` in Extraktionsliste aufnehmen |

## Erbetene Hilfe
1. `frost.yml` nach Vorgabe aus `Plannung/frost-concept-phase-2b.md` Abschnitt 3 erstellen (alle Keys: `frost.enabled`, `frost.freeze-threshold`, `frost.full-frost-threshold`, `frost.day-night-transition-seconds`, `frost.intensity-multiplier`, `frost.target-color`, `frost.tint-strength`, `frost.particles.*`, `frost.excluded-biomes`)
2. `FrostConfig.java` implementieren – analog zu `VisualConfig.java` (laden via `ConfigManager`, Getter für alle Werte, Defaults aus YAML)
3. `ConfigManager.java` erweitern: `frost.yml` laden, `FrostConfig` instanziieren, öffentlichen Getter `getFrostConfig()` bereitstellen
4. `ResourceCopier.java` prüfen und ggf. `frost.yml` in die Liste der zu extrahierenden Ressourcen aufnehmen
5. `FrostConfig` in `SeasonsPlugin.java` initialisieren (analog zu `VisualConfig`)
6. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
7. Deployment: JAR + `frost.yml` per SCP kopieren, dann `sudo systemctl restart crafty`
8. Server-Log prüfen: `frost.yml` wird geladen und alle Werte sind zugreifbar

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` oder `frost.yml` kategorisieren
- **Season deterministisch:** Ausschließlich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **NMS/Reflection:** Ab Phase 2 erlaubt (Paper-Adapter), für Frost-Config aber irrelevant
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`