---
title: "Arbeitsauftrag: FrostEffectManager – Frost-Faktor + Partikel"
quelle: "roadmap.md → Phase 2b, Sprint 2b.2"
related-roadmap: "Plannung/roadmap.md → Phase 2b: Frost System – Temperaturabhängiger Frost (Tint-Lerp + Partikel)"
created: "2026-01-20"
status: in-progress
---

# Arbeitsauftrag: FrostEffectManager – Frost-Faktor + Partikel

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`FrostEffectManager` als neuen Service implementieren. Verantwortlich für:
1. Berechnung des `frostFactor` pro Spieler aus `TemperatureCalculator.getTemperature(loc)`
2. Prüfung der `excluded-biomes` aus `FrostConfig` – in ausgeschlossenen Biomen immer `frostFactor = 0.0`
3. Periodisches Spawnen von `SNOWFLAKE`-Partikeln um aktive Spieler bei `frostFactor > 0`
4. Cleanup bei `PlayerQuitEvent`
5. Öffentliche Methoden: `getFrostFactor(Player)`, `getTargetColor()`, `onTick()` (Bukkit-Task)

## Aktuelles Ergebnis
- Phase 2 (Foliage Tints) ist abgeschlossen – `VisualSeasonManager` existiert und koordiniert Spieler-Visuals
- `TemperatureCalculator.getTemperature(loc)` funktioniert und liefert korrekte Werte
- `FrostConfig` (aus Sprint 2b.1) stellt alle Config-Werte bereit
- Es existiert **noch kein** `FrostEffectManager`
- Package `de.ajsch.seasons.visual` enthält bereits `VisualSeasonManager`, `FoliageTintManager`, `SeasonalColorCalculator`, `VisualConfig`

## Ursachenverdacht
Kein Fehler – reine Neuentwicklung.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/FrostEffectManager.java` | Neue Service-Klasse |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | Wird genutzt (Getter für alle Frost-Werte) |
| `src/main/java/de/ajsch/seasons/temperature/TemperatureCalculator.java` | Wird genutzt (getTemperature) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Registrierung des FrostEffectManager, PlayerQuitEvent |

## Erbetene Hilfe
1. `FrostEffectManager.java` im Package `de.ajsch.seasons.visual` erstellen
2. Konstruktor: `FrostConfig`, `TemperatureCalculator`, `Plugin` (für Bukkit-Tasks) übergeben
3. `getFrostFactor(Player player)` implementieren:
   - `TemperatureCalculator.getTemperature(player.getLocation())` abrufen
   - Wenn Temperatur ≥ `freezeThreshold` → return 0.0
   - Prüfen: Biome-Name in `excluded-biomes` → return 0.0
   - Intensität berechnen: `(freezeThreshold - temp) / (freezeThreshold - fullFrostThreshold)`
   - Clamp auf [0.0, 1.0], mit `intensityMultiplier` multiplizieren
4. `getTargetColor()` implementieren: parst `target-color` aus Config (String wie "0xDDE4E8" → int)
5. `onTick()` als periodischer Bukkit-Task (alle 4–8 Sekunden, Intervall aus Config):
   - Für jeden Online-Spieler `frostFactor` berechnen
   - Wenn `frostFactor > 0` und Partikel enabled: `SNOWFLAKE`-Partikel um Spieler spawnen
   - `particles-per-second`, `spread-radius` aus Config nutzen
   - `World.spawnParticle()` mit Offset/Spread verwenden
6. `PlayerQuitEvent`-Handler: Kein expliziter Cleanup nötig (periodischer Task prüft `player.isOnline()`), aber sicherstellen dass keine Leaks entstehen
7. In `SeasonsPlugin.java` registrieren (analog zu `VisualSeasonManager`)
8. Build: `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
9. Deployment: JAR per SCP kopieren, dann `sudo systemctl restart crafty`
10. Funktionstest: Im Winter oder per `/season set winter` prüfen, ob SNOWFLAKE-Partikel erscheinen und in excluded-biomes (Desert) nicht

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `frost.yml` (`excluded-biomes`) filtern
- **Season deterministisch:** Ausschließlich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **NMS/Reflection:** Ab Phase 2 erlaubt, für Partikel nicht nötig (reine Paper-API)
- **Java-Dateien ≤ 400 Zeilen:** `FrostEffectManager` sollte deutlich unter 400 Zeilen bleiben
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