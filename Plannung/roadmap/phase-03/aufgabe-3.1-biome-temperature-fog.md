"---
title: "Arbeitsauftrag: BiomeJsonGenerator – temperature + fog_color"
quelle: "roadmap.md → Phase 3, Sprint 3.1"
related-roadmap: "https://"
created: "2025-07-08"
status: in-progress
---

# Arbeitsauftrag: BiomeJsonGenerator – temperature + fog_color in Custom-Biome-JSONs

**Quelle:** roadmap.md → Phase 3, Sprint 3.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"Seasons\"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\Seasons`

## Auftrag
1. `frost.yml` um Feld `biome-temperature` erweitern (Default -0.5)
2. `FrostConfig.java` um `getFrostBiomeTemperature()` erweitern
3. `BiomeJsonGenerator.java`: Frost-Biome-JSONs erhalten `temperature` aus `FrostConfig.getFrostBiomeTemperature()`
4. `season_colors.yml` um optionale Felder `fog_color`, `sky_color`, `water_fog_color` pro Saison erweitern (nur `fall` befüllen, andere auf Vanilla-Default)
5. `BiomeJsonGenerator.java`: Saison-Biome-JSONs erhalten `fog_color` etc. aus `season_colors.yml` (falls konfiguriert)
6. `SeasonColorConfig.java` um Getter für Fog/Sky/Water-Farben erweitern

## Aktuelles Ergebnis
- `BiomeJsonGenerator` erzeugt `frost_*.json` nur mit `grass_color`/`foliage_color`
- `temperature`, `fog_color`, `sky_color`, `water_fog_color` werden NICHT in JSONs geschrieben
- Frost-Biome haben Vanilla-Temperatur → Wasser friert nicht
- Kein atmosphärischer Nebel sichtbar

## Ursachenverdacht
- Generator kopiert 1:1 vom Vanilla-Original – `temperature` und `fog_color` werden nie überschrieben
- `FrostConfig` hat kein `biome-temperature`-Feld
- `SeasonColorConfig` hat keine Fog/Sky/Water-Getter

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/frost.yml` | Neues Feld `biome-temperature: -0.5` |
| `src/main/resources/season_colors.yml` | Neue Felder `fog_color`, `sky_color`, `water_fog_color` unter `defaults.<season>` |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | Getter `getFrostBiomeTemperature()` |
| `src/main/java/de/ajsch/seasons/visual/SeasonColorConfig.java` | Getter für Fog/Sky/Water-Farben |
| `src/main/java/de/ajsch/seasons/visual/BiomeJsonGenerator.java` | Setzt `temperature`, `fog_color`, `sky_color`, `water_fog_color` in generierten JSONs |

## Erbetene Hilfe
1. `frost.yml` um `biome-temperature: -0.5` erweitern (unter `freeze-threshold`)
2. `FrostConfig.java`: Feld `frostBiomeTemperature` (double, Default -0.5) + Getter + aus YAML lesen
3. `SeasonColorConfig.java`: Getter `getFogColor(Season)`, `getSkyColor(Season)`, `getWaterFogColor(Season)` mit Fallback auf `null`/Optional
4. `BiomeJsonGenerator.java`:
   - Frost-Biome: `json.setTemperature(frostConfig.getFrostBiomeTemperature())`
   - Saison-Biome: Wenn `seasonColorConfig.getFogColor(season) != null` → in effects schreiben; analog `sky_color`, `water_fog_color`
5. `season_colors.yml` um `fog_color`, `sky_color`, `water_fog_color` unter `defaults.fall` ergänzen (andere Seasons leer lassen – Vanilla-Default)
6. Build: `Set-Location C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\Seasons; .\\gradlew.bat compileJava` dann `; .\\gradlew.bat shadowJar -x test`
7. Deployment: JAR + geänderte Configs kopieren, `/season generate-biomes force`, Server-Neustart

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API, Packet-Overrides erst ab Phase 2
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar\"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`
"