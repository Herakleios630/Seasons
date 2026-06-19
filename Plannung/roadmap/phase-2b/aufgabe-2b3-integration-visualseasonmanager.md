---
title: "Arbeitsauftrag: Integration FrostEffectManager in VisualSeasonManager"
quelle: "roadmap.md → Phase 2b, Sprint 2b.3"
related-roadmap: "Plannung/roadmap.md → Phase 2b: Frost System – Temperaturabhängiger Frost (Tint-Lerp + Partikel)"
created: "2026-01-20"
status: in-progress
---

# Arbeitsauftrag: Integration FrostEffectManager in VisualSeasonManager

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`FrostEffectManager` in den `VisualSeasonManager` integrieren:
1. `VisualSeasonManager` erhält eine Referenz auf `FrostEffectManager`
2. In `updatePlayerVisuals(Player)` den Frost-Tint-Lerp einbauen:
   - Saison-Tint von `FoliageTintManager.getSeasonalColors()` holen
   - `frostFactor` von `FrostEffectManager.getFrostFactor(player)` holen
   - Finalen Tint per Lerp mischen: `lerpColor(seasonGrass, frostTargetColor, frostFactor * tintStrength)`
3. `FrostEffectManager.onTick()` separat als eigener Task starten (nicht im gleichen Timer wie Tint-Updates)
4. Keine zirkulären Abhängigkeiten: `FrostEffectManager` kennt den `FoliageTintManager` nicht direkt

## Aktuelles Ergebnis
- `FrostEffectManager` (Sprint 2b.2) existiert mit `getFrostFactor(Player)`, `getTargetColor()`, `onTick()`
- `VisualSeasonManager` existiert und koordiniert bereits Saison-Tints über `FoliageTintManager`
- `FrostConfig` (Sprint 2b.1) stellt `tintStrength` und `target-color` bereit
- `updatePlayerVisuals()` existiert, aber ohne Frost-Lerp

## Ursachenverdacht
Kein Fehler – reine Erweiterung. Risiko: doppelte Tint-Berechnung (Saison + Frost) könnte Performance kosten, aber Lerp ist O(1).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Erweiterung: Frost-Lerp in `updatePlayerVisuals()` |
| `src/main/java/de/ajsch/seasons/visual/FrostEffectManager.java` | Wird genutzt (getFrostFactor, getTargetColor) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Falls Registrierung angepasst werden muss |

## Erbetene Hilfe
1. `VisualSeasonManager` um Feld `FrostEffectManager frostEffectManager` erweitern (via Konstruktor oder Setter)
2. In `updatePlayerVisuals(Player player)`:
   - Bestehenden Call zu `FoliageTintManager.getSeasonalColors()` identifizieren
   - `double frostFactor = frostEffectManager.getFrostFactor(player);` abrufen
   - Wenn `frostFactor > 0.0`: `int frostTarget = frostEffectManager.getTargetColor();`
   - `int tintStrength = frostEffectManager.getConfig().getTintStrength();` (oder via FrostConfig-Getter)
   - Finalen Grass-Color-Wert per `lerpColor(seasonGrass, frostTarget, frostFactor * tintStrength)` berechnen
   - Gleiches für Foliage-Color
3. `lerpColor(int from, int to, double factor)` als private Hilfsmethode in `VisualSeasonManager` (RGB-Kanäle einzeln interpolieren)
4. `FrostEffectManager.onTick()` in `SeasonsPlugin.java` als separaten Bukkit-Task starten (nicht in `VisualSeasonManager.onTick()` einbauen)
5. Sicherstellen, dass Frost nur in Overworld-Welten aktiv ist (Check in `FrostEffectManager.onTick()`)
6. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
7. Deployment: JAR per SCP kopieren, dann `sudo systemctl restart crafty`
8. Funktionstest: In kaltem Biom (Taiga) im Winter prüfen, ob Gras-Farbe richtung frostig-weiß wechselt

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** `tintStrength`, `target-color` aus `frost.yml`
- **Biome nie hardcoden:** Biome-Filter via `FrostEffectManager.excluded-biomes`
- **Season deterministisch:** Ausschließlich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **NMS/Reflection:** Für Tint-Overrides via `FoliageTintManager` bereits genutzt; Frost-Lerp ist rein mathematisch
- **Java-Dateien ≤ 400 Zeilen:** `VisualSeasonManager` könnte durch Frost-Integration wachsen → bei >350 Zeilen aufteilen
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