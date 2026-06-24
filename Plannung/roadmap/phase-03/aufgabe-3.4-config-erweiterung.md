---
title: "Arbeitsauftrag: Config-Erweiterung temperature-effects + frost.yml"
quelle: "roadmap.md → Phase 3, Sprint 3.4"
related-roadmap: "https://"
created: "2025-07-08"
status: in-progress
---

# Arbeitsauftrag: Config-Erweiterung temperature-effects + frost.yml

**Quelle:** roadmap.md → Phase 3, Sprint 3.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Alle Config-Dateien für Phase-3-Effekte vervollständigen, Konsistenz prüfen und Default-Werte validieren:

1. **`config.yml`** – `temperature-effects`-Sektion mit allen Schwellwerten finalisieren (bereits in 3.3 angelegt, hier Review + ggf. Korrektur)
2. **`frost.yml`** – Prüfen ob `biome-temperature` aus 3.1 korrekt drin ist; ggf. dokumentieren
3. **`season_colors.yml`** – Prüfen ob `fog_color`, `sky_color`, `water_fog_color` aus 3.1 korrekt drin sind
4. **Config-Validierung in `ConfigManager.java`**: Sicherstellen dass neue Sektionen bei fehlender Config mit sinnvollen Defaults geladen werden (keine NPE)
5. **Dokumentation** der neuen Config-Felder in `docs/developer-guide.md`

## Aktuelles Ergebnis
- `temperature-effects`-Sektion wird in Sprint 3.3 neu in `config.yml` angelegt
- `frost.yml` und `season_colors.yml` werden in Sprint 3.1 erweitert
- Keine zentrale Konsistenzprüfung über alle neuen Werte

## Ursachenverdacht
- Die Änderungen in 3.1 und 3.3 erfolgen unabhängig voneinander
- Ein dedizierter Config-Review-Sprint stellt sicher, dass nichts vergessen wurde

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | Review `temperature-effects`-Sektion |
| `src/main/resources/frost.yml` | Review `biome-temperature` Feld |
| `src/main/resources/season_colors.yml` | Review `fog_color`, `sky_color`, `water_fog_color` |
| `src/main/java/de/ajsch/seasons/config/ConfigManager.java` | Defaults für neue Sektionen sicherstellen |
| `docs/developer-guide.md` | Neue Config-Felder dokumentieren |

## Erbetene Hilfe
1. `config.yml` Review: Alle `temperature-effects`-Werte sinnvoll? Thresholds realistisch? (`hunger: -0.2`, `slowness: -0.5`, `mining-fatigue: -0.8`, `heat.exhaustion: 0.8`, `heat.slowness: 1.0`)
2. `ConfigManager.java`: Sicherstellen dass `getConfigurationSection("temperature-effects")` bei fehlender Config nicht `null` returned, sondern Default-Werte setzt
3. `frost.yml` Review: `biome-temperature: -0.5` vorhanden? Kommentar ergänzen: "Temperatur die in Frost-Biome-JSONs geschrieben wird. Muss < 0.15 sein für Vanilla-Eisbildung."
4. `season_colors.yml`: `fog_color`, `sky_color`, `water_fog_color` für alle vier Seasons vorhanden? Falls nur `fall` belegt – Kommentar für andere Seasons: "Vanilla-Default"
5. `docs/developer-guide.md`: Tabelle der neuen Config-Werte mit Pfad, Typ, Default, Beschreibung ergänzen
6. Build: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons; .\gradlew.bat compileJava` dann `; .\gradlew.bat shadowJar -x test`
7. Deployment: Geänderte Configs kopieren, Server-Neustart

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
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`