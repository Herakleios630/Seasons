---
title: "Arbeitsauftrag: Temperatur-PhaseShift korrigieren & meltThreshold senken"
quelle: "Ad-hoc – Analyse Temperatursystem"
created: "2025-07-11"
status: done
---

# Arbeitsauftrag: Temperatur-PhaseShift korrigieren & meltThreshold senken

**Quelle:** Ad-hoc – Analyse des Temperatursystems

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag

Drei präzise Korrekturen am Temperatursystem:

### A – PhaseShift fixen (wärmster Tag = Hochsommer statt Frühlingsbeginn)
Der `phaseShift` in `TemperatureCalculator.calculate()` ist auf `yearLength * 0.25` gesetzt.
Das bewirkt, dass der Kosinus sein Maximum an Tag 91 (≈ Frühlingsbeginn) erreicht – nicht im Hochsommer.
Korrekt ist `yearLength * 0.5`, damit das Temperaturmaximum in der Jahresmitte liegt.

### B – meltThreshold von 0.5 auf 0.0 senken
`weather.snow.melt-threshold` (config.yml) ist 0.5. Im Wertebereich −0.5…0.8 bedeutet das:
Schnee schmilzt erst, wenn die Basistemperatur 0.5 übersteigt – ein sehr warmer Wert.
Da der freezeThreshold ebenfalls 0.0 ist, sollte Schnee bei Überschreiten der Frostgrenze schmelzen.
Neuer Default: **0.0**.

### C – DayNight-Amplitude aus calculateWithDayTime entfernen oder dokumentieren
`day-night-amplitude` (0.15) wird nur in `calculateWithDayTime()` verwendet, das aktuell nirgends
im Hauptprozess aufgerufen wird. Kein Bug, aber potenzielle Verwirrung in der Config. Optional
in config.yml einen Kommentar ergänzen, dass dieser Wert derzeit ungenutzt ist.

## Aktuelles Ergebnis
- Wärmster Tag des Jahres ≈ Frühlingsbeginn (Tag 91) → Winter endet mit plötzlichem Peak
- meltThreshold 0.5 → Frühlingsschmelze tritt viel zu spät ein (Temperatur muss weit über Null steigen)
- Nutzer beobachtet: Temperatur im Frühling um 0.35° → kein Schmelzen weil < 0.5

## Ursachenverdacht
1. `phaseShift = yearLength * 0.25` ist falsch gewählt (sollte 0.5 sein)
2. `meltThreshold = 0.5` ist für eine −0.5…0.8-Skala viel zu hoch angesetzt

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/temperature/TemperatureCalculator.java` | PhaseShift-Korrektur Zeile 37 |
| `src/main/resources/config.yml` | meltThreshold 0.5 → 0.0, Kommentar für day-night-amplitude |

## Erbetene Hilfe

1. **TemperatureCalculator.java Zeile 37** – `yearLength * 0.25` → `yearLength * 0.5`
2. **config.yml** – `melt-threshold: 0.5` → `melt-threshold: 0.0`
3. **config.yml** – Kommentar `# day-night-amplitude wird derzeit nur in calculateWithDayTime() verwendet (nicht im Hauptprozess)` ergänzen
4. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
5. Deployment-Befehle posten
6. Nach Server-Neustart testen: `/season set spring` → sofortige Schmelze sollte einsetzen

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
