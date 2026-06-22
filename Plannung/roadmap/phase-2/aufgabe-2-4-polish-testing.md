---
title: "Arbeitsauftrag: Phase 2.4 – Polish & Testing"
quelle: "roadmap.md → Phase 2, Sprint 2.4"
related-roadmap: "Plannung/roadmap.md"
created: "2025-02-20"
status: code-done
---

# Arbeitsauftrag: Phase 2.4 – Polish & Testing

**Quelle:** roadmap.md → Phase 2, Sprint 2.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Foliage-Tint-System feinschleifen: Übergänge flüssig gestalten, alle vier Seasons durchtesten, Performance-Profil erstellen, `foliage_tints.yml` Farben feintunen. Dokumentation synchronisieren und Phase 2 abhaken.

## Aktuelles Ergebnis
- Phase 2.3: Integration abgeschlossen – `VisualSeasonManager` ist an PlayerJoin, PlayerQuit, SeasonChange und Tick-Loop angebunden.
- Spieler sehen Saison-abhängige Biome-Tints.
- Noch nicht getestet: flüssige Übergänge, Performance bei vielen Spielern, Farbtreue in allen Biomen.

## Ursachenverdacht
Nicht anwendbar (Polish & Testing).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/foliage_tints.yml` | Feintuning der Saison-Farben und Übergangsparameter |
| `src/main/java/de/ajsch/seasons/visual/ColorCalculator.java` | Ggf. Interpolations-Logik nachbessern |
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Performance-Optimierung (Chunk-Caching) |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Ggf. Update-Intervall anpassen |
| `docs/developer-guide.md` | Visual-System dokumentieren |
| `docs/handover.md` | Status Phase 2 dokumentieren |
| `README.md` | Neue Features erwähnen (Foliage Tints) |
| `Plannung/roadmap.md` | Phase 2 als erledigt abhaken |

## Erbetene Hilfe

1. **Test-Matrix durchführen** (→ auf Server)
   - Alle 4 Seasons nacheinander durchschalten (`/season set SPRING`, `SUMMER`, `FALL`, `WINTER`)
   - In jeder Season folgende Biome visuell prüfen: Plains, Forest, Birch Forest, Dark Forest, Taiga, Cherry Grove, Jungle, Savanna, Swamp, Badlands
   - Prüfen: Herbst-Farben sichtbar? Winter grau/braun? Frühling frisch + Cherry rosa?
   - Screenshots für Doku machen (optional)

2. **Übergänge feintunen** (→ auf Server)
   - `transition-days` in `foliage_tints.yml` testen: 2 Tage vs. 4 Tage vs. 7 Tage
   - Sicherstellen, dass die Interpolation flüssig läuft (kein abruptes Umspringen)
   - `ColorCalculator.interpolate()` prüfen: RGB-Kanal-weise linear, Clamping auf 0–255 ✅

3. **Performance-Profil** ✅ (Code-seitig)
   - Chunk-Biome-Caching eingebaut (`ConcurrentHashMap<Long, Set<Biome>>`)
   - `biomeSampleStep = 4` → 16 statt 256 Block-Lookups pro Chunk
   - `ChunkUnloadEvent`-Listener zur Cache-Invalidation
   - `/tps`-Vergleich auf Server ausstehend

4. **Fallback-Test** (→ auf Server)
   - `foliage_tints.yml` absichtlich löschen → Plugin muss mit sinnvollen Defaults starten
   - `foliage_tints.yml` mit fehlerhaften Werten → Plugin soll Fehler loggen und Vanilla-Farben verwenden
   - NMS-Version nicht unterstützt → Klare Log-Meldung, kein Crash

5. **Dokumentation synchronisieren** ✅
   - `docs/developer-guide.md`: Abschnitt 4 (Visual Seasons) hinzugefügt – Architektur, Datenmodelle, Config, NMS, Performance ✅
   - `docs/handover.md`: Status Phase 2 auf fertig gesetzt, Phase 2.4 als in-progress, offene Baustellen aktualisiert ✅
   - `README.md`: Phase 2 als abgeschlossen, neues Feature erwähnt ✅
   - `Plannung/roadmap.md`: Phase 2 Done-Definition abgehakt, Sprint 2.4 auf [~] ✅

6. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`

7. **Deployment:** JAR + `foliage_tints.yml` kopieren, dann `sudo systemctl restart crafty`

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