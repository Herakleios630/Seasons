---
title: "Arbeitsauftrag: Phase 3 Integration & Test"
quelle: "roadmap.md → Phase 3, Sprint 3.5"
related-roadmap: "https://"
created: "2025-07-08"
status: in-progress
---

# Arbeitsauftrag: Phase 3 Integration & Test

**Quelle:** roadmap.md → Phase 3, Sprint 3.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Alle Phase-3-Komponenten integrieren, auf dem Server deployen und einen vollständigen Funktionstest durchführen:

1. **Integration in `SeasonsPlugin.java`**: EffectScheduler, FrostEffectManager, TemperatureEffect registrieren, PlayerQuitEvent-Handler, shutdown
2. **Build + Deploy**
3. **Server-Test-Matrix** für alle Phase-3-Features abarbeiten
4. **Fehlerbehebung**
5. **Dokumentations-Sync**

## Aktuelles Ergebnis
- Sprints 3.1–3.4 sind einzeln implementiert
- Noch kein Integrationstest über das komplette System
- Mögliche unentdeckte Konflikte zwischen EffectScheduler, BiomeSpoofCoordinator und Snow-System

## Ursachenverdacht
- Integrationstest ist per Definition der letzte Sprint einer Phase
- Mögliche Konflikte: Timer-Überschneidungen, Config-Defaults fehlen, NPE bei fehlender Config-Sektion

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Integration aller Phase-3-Komponenten |
| `src/main/resources/config.yml` | Defaults prüfen |
| `src/main/resources/frost.yml` | `biome-temperature` prüfen |
| `src/main/resources/season_colors.yml` | `fog_color` etc. prüfen |
| `docs/handover.md` | Status, offene Punkte aktualisieren |
| `docs/developer-guide.md` | Neue Schichten dokumentieren |
| `Plannung/roadmap.md` | Phase 3 als erledigt markieren |
| `README.md` | Neue Commands/Effekte dokumentieren |

## Erbetene Hilfe
1. **Integration in `SeasonsPlugin.java`**:
   - `EffectScheduler` als Feld, in `onEnable()` nach `BiomeSpoofCoordinator` initialisieren
   - `FrostEffectManager` anpassen (kein eigener Timer mehr) → in `EffectScheduler` registrieren
   - `TemperatureEffect` mit Config-Werten instanziieren → in `EffectScheduler` registrieren
   - `PlayerQuitEvent`-Handler: `effectScheduler.cleanupPlayer(event.getPlayer())`
   - `onDisable()`: `effectScheduler.shutdown()` vor anderen Komponenten
2. **Build**: `compileJava` + `shadowJar -x test`
3. **Deployment**: JAR + alle geänderten Configs kopieren, `/season generate-biomes force`, Server-Neustart
4. **Test-Matrix**:
   - [ ] Plugin startet sauber, keine NPE in Logs
   - [ ] `/season debug` zeigt korrekte Season + Temperatur
   - [ ] Im Winter + kaltem Biom: Frost-Partikel sichtbar?
   - [ ] Bei `temp < -0.2`: HUNGER-Effekt sichtbar?
   - [ ] Bei `temp < -0.5`: SLOWNESS-Effekt sichtbar?
   - [ ] Bei Season-Wechsel (`/season skip`): Effekte wechseln sauber (keine Hänger)
   - [ ] Frost-Biome aktiv → stehendes Wasser friert ein (Vanilla Random-Tick, kann dauern)
   - [ ] `fog_color` in generierten JSONs vorhanden? (mit F3 prüfen: Biome-Effekte)
   - [ ] Keine Konflikte mit Snow-System (Schnee fällt weiter im Winter)
   - [ ] Player quit → Cleanup ohne Fehler
   - [ ] Server stop → keine Leak-Warnings
5. **Fehlerbehebung**: Alle in der Test-Matrix gefundenen Probleme dokumentieren und fixen
6. **Dokumentations-Sync**:
   - `docs/handover.md`: Phase 3 Status, offene Punkte, bekannte Limitations (fliessendes Wasser etc.)
   - `docs/developer-guide.md`: Neue Pakete `effects/`, `SeasonalEffect`-Interface, `EffectScheduler`
   - `Plannung/roadmap.md`: Phase 3 Done-Definition abhaken
   - `README.md`: Neue Features (Frost-Partikel, Eisbildung, Temperatur-Debuffs) ergänzen

## Test-Tabelle (im Feld testen)
| Test | Soll | Ist | Status |
|---|---|---|---|
| Plugin-Start | Keine NPE, keine Errors | | [ ] |
| Frost-Partikel bei temp < 0 | SNOWFLAKE sichtbar | | [ ] |
| HUNGER bei temp < -0.2 | Potion-Symbol + Hunger | | [ ] |
| SLOWNESS bei temp < -0.5 | Potion-Symbol + Slowness | | [ ] |
| Wasser friert in Frost-Biom | Eisbildung per Vanilla-Tick | | [ ] |
| Fog-Color in Custom-Biome-JSON | Mit F3 im Biome-Effekt sichtbar | | [ ] |
| Season-Wechsel sauber | Keine doppelten Effekte | | [ ] |
| Snow-System unverändert | Schnee fällt normal | | [ ] |
| Player-Quit Cleanup | Keine Leaks | | [ ] |
| Server-Stop | Sauber, keine Warnings | | [ ] |

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