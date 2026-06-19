---
title: "Arbeitsauftrag: Phase 2.3 – Integration (PlayerJoinListener, SeasonChangeListener, Tick-Loop)"
quelle: "roadmap.md → Phase 2, Sprint 2.3"
related-roadmap: "Plannung/roadmap.md"
created: "2025-02-20"
status: pending
---

# Arbeitsauftrag: Phase 2.3 – Integration (PlayerJoinListener, SeasonChangeListener, Tick-Loop)

**Quelle:** roadmap.md → Phase 2, Sprint 2.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
VisualSeasonManager an die bestehenden Event-Listener und den Plugin-Tick anbinden. PlayerJoin, PlayerQuit und SeasonChange korrekt verdrahten. Periodischen Task im Plugin registrieren. Sicherstellen, dass das Visual-System mit Snow Growth/Melting (Phase 1a/1b) koexistiert.

## Aktuelles Ergebnis
- Phase 2.2: `FoliageTintManager` und `VisualSeasonManager` sind fertig.
- `VisualSeasonManager.onPlayerJoin()`, `.onPlayerQuit()`, `.onSeasonChange()`, `.onTick()` sind implementiert.
- Noch nicht verdrahtet: Keine Event-Listener rufen diese Methoden auf.
- Phase 1a/1b läuft parallel – darf nicht gestört werden.

## Ursachenverdacht
Nicht anwendbar (Verdrahtung, kein Bugfix).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/listener/PlayerJoinListener.java` | Erweitern: `VisualSeasonManager.onPlayerJoin(player)` aufrufen |
| `src/main/java/de/ajsch/seasons/listener/PlayerMoveListener.java` | Prüfen: Kein Visual-Trigger nötig (nur Time-based) |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | Erweitern: `VisualSeasonManager.onSeasonChange(newSeason)` aufrufen |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Tick-Loop: `visualSeasonManager.onTick()` einhängen; `PlayerQuitEvent`-Listener für Cleanup registrieren |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Ggf. minimale Anpassungen für Event-Signaturen |

## Erbetene Hilfe

1. **`SeasonChangeListener.java` erweitern**
   - Nach bestehender Season-Change-Logik (Schnee etc.) `visualSeasonManager.onSeasonChange(event.getNewSeason())` aufrufen
   - `VisualSeasonManager` per Constructor-Injection oder Plugin-Getter beziehen
   - Null-Check: Falls Visual-System deaktiviert/nicht initialisiert, überspringen

2. **`PlayerJoinListener.java` erweitern**
   - Nach bestehendem Join-Handling `visualSeasonManager.onPlayerJoin(event.getPlayer())` aufrufen
   - Sicherstellen, dass der initiale Tint erst nach vollständigem Join gesendet wird (1–2 Ticks Delay oder synchron ok)

3. **`PlayerQuitEvent`-Listener registrieren (in `SeasonsPlugin.java` oder eigenem Listener)**
   - `visualSeasonManager.onPlayerQuit(event.getPlayer())` aufrufen
   - Kann als anonyme Lambda-Registrierung in `onEnable()` erfolgen oder als Methode in `PlayerJoinListener`

4. **Tick-Loop in `SeasonsPlugin.java` verdrahten**
   - `visualSeasonManager.onTick()` in den bestehenden `SnowAccumulator`-Tick oder einen eigenen `BukkitScheduler.runTaskTimer()` einhängen
   - Intervall: 10 Sekunden (200 Ticks) – konfigurierbar via `foliage_tints.yml` → `update-interval-ticks`

5. **Koexistenz prüfen**
   - Sicherstellen, dass `SnowAccumulator` und `VisualSeasonManager` sich keine Ressourcen streitig machen
   - Keine doppelten Tick-Registrierungen
   - Beide Systeme nutzen unterschiedliche Configs (keine Überschneidungen)

6. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

7. **Deployment:** JAR per SCP kopieren, ggf. `foliage_tints.yml` kopieren, dann `sudo systemctl restart crafty`

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