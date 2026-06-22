---
title: "Arbeitsauftrag: Phase 2.2 – FoliageTintManager & VisualSeasonManager-Grundgerüst"
quelle: "roadmap.md → Phase 2, Sprint 2.2"
related-roadmap: "Plannung/roadmap.md"
created: "2025-02-20"
status: done
---

# Arbeitsauftrag: Phase 2.2 – FoliageTintManager & VisualSeasonManager-Grundgerüst

**Quelle:** roadmap.md → Phase 2, Sprint 2.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
FoliageTintManager bauen: pro Spieler die aktuellen Biome-Tints berechnen, via NmsAdapter an Clients senden. VisualSeasonManager als Koordinator, der PlayerJoin/SeasonChange-Events empfängt und periodische Updates anstösst.

## Aktuelles Ergebnis
- Phase 2.1 (Foundation): `NmsAdapter`, `VisualConfig`, `ColorCalculator` sind fertiggestellt.
- `foliage_tints.yml` liegt vor und wird von `ConfigManager` geladen.
- Es gibt noch keine laufenden Spieler-Updates – die folgen in diesem Sprint.

## Ursachenverdacht
Nicht anwendbar (neues Feature).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Kern: Biome-Color-Tinting pro Spieler, nutzt NmsAdapter |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Koordinator: hält Player-Map, Event-Handler, periodischer Task |
| `src/main/java/de/ajsch/seasons/visual/ColorCalculator.java` | Wird von FoliageTintManager genutzt (bereits vorhanden) |
| `src/main/java/de/ajsch/seasons/visual/VisualConfig.java` | Config-Lieferant (bereits vorhanden) |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | NMS-Abstraktion (bereits vorhanden) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Erweitern: VisualSeasonManager initialisieren, Tick registrieren |

## Erbetene Hilfe

1. **`FoliageTintManager.java` implementieren**
   - Methode `updatePlayerTints(Player player, Season currentSeason, double transitionProgress)`:
     - Für jedes geladene Chunk um den Spieler die Biome-Infos abrufen
     - `ColorCalculator.calculateSeasonalColor(...)` mit passenden Parametern aufrufen
     - `NmsAdapter.sendBiomeTint(...)` aufrufen, um Farben an den Client zu senden
   - Methode `resetPlayerToVanilla(Player player)`: Vanilla-Farben wiederherstellen (Sommer-Default)
   - Methode `updateAllOnlinePlayers(Season currentSeason, double transitionProgress)`: Bulk-Update
   - Nur Geladene Chunks verarbeiten – kein Chunk-Forcing

2. **`VisualSeasonManager.java` implementieren**
   - Feld: `Map<UUID, PlayerVisualState>` zur Verwaltung pro Spieler
   - `PlayerVisualState` als inner class: `currentTint`, `lastUpdateTick`, `isActive`
   - Methode `onPlayerJoin(Player player)`: Initialen Tint senden
   - Methode `onPlayerQuit(Player player)`: State aufräumen
   - Methode `onSeasonChange(Season newSeason)`: Alle Spieler mit neuem Tint versorgen
   - Methode `onTick()`: Periodisch (konfigurierbar, default 10s) `FoliageTintManager.updateAllOnlinePlayers()` aufrufen, wenn in Saison-Übergang
   - Kein PlayerMoveEvent-Trigger in 2.2 – nur Time-based

3. **`SeasonsPlugin.java` erweitern**
   - `VisualSeasonManager`-Instanz im `onEnable()` erzeugen
   - `VisualSeasonManager.onTick()` in den bestehenden Tick-Loop (oder eigenen Scheduler) einhängen
   - `VisualConfig` initialisieren und an `VisualSeasonManager` übergeben

4. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

5. **Deployment:** JAR per SCP kopieren, `foliage_tints.yml` kopieren, dann `sudo systemctl restart crafty`

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
"- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

---

## Fortschritt (2025-02-20)

| # | Aufgabe | Status |
|---|---|---|
| 1 | `FoliageTintManager.java` – updatePlayerTints, resetPlayerToVanilla, updateAllOnlinePlayers, collectBiomesFromChunk | ✅ done |
| 2 | `VisualSeasonManager.java` – Map<UUID, PlayerVisualState>, EventHandler (join/quit/seasonChange), onTick mit Transition-Berechnung, inner class PlayerVisualState | ✅ done |
| 3 | `SeasonsPlugin.java` – VisualConfig + NmsAdapter + FoliageTintManager + VisualSeasonManager initialisiert, start()/stop() in onEnable/onDisable | ✅ done |
| 3a | `VisualConfig.java` – `updateIntervalTicks` Feld, Getter, Parsing aus `foliage_tints.yml` | ✅ done |
| 3b | `foliage_tints.yml` – `update-interval-ticks: 200` Eintrag hinzugefügt | ✅ done |
| 4 | Build (`compileJava`, `shadowJar`) | ✅ BUILD SUCCESSFUL |
| 5 | Deployment (JAR + Config kopieren, Server restart) | ⬜ pending |
"