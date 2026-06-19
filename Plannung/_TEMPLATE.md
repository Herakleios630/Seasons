---
title: "Arbeitsauftrag: {KURZTITEL}"
quelle: "{roadmap.md → Phase X, Aufgabe Y | ToDo → Item Z | Ad-hoc}"
related-roadmap: "{LINK ZUM ROADMAP-ITEM}"
created: "{DATUM}"
status: in-progress
---

# Arbeitsauftrag: {KURZTITEL}

**Quelle:** {roadmap.md → Phase X, Aufgabe Y | ToDo → Item Z | Ad-hoc}

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
<!-- Präzise Beschreibung des gewünschten Features/Fixes -->
{...}

## Aktuelles Ergebnis
<!-- Was funktioniert bereits, was (noch) nicht? -->
{...}

## Ursachenverdacht
<!-- Hypothesen, warum es nicht wie erwartet läuft -->
{...}

## Betroffene Schichten & Dateien
<!-- Konkrete Dateiliste mit kurzer Rollenbeschreibung -->
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/.../Foo.java` | ... |
| `src/main/resources/config.yml` | nur wenn nötig |
| `src/main/resources/precipitation_categories.yml` | nur wenn Kategorien geändert werden |

## Erbetene Hilfe
<!-- Klare, umsetzbare ToDo-Liste -->
1. {...}
2. {...}
3. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
4. Deployment: JAR per SCP kopieren, dann `sudo systemctl restart crafty`

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