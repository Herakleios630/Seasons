---
title: "Arbeitsauftrag: Frost Testing & Feintuning"
quelle: "roadmap.md → Phase 2b, Sprint 2b.4"
related-roadmap: "Plannung/roadmap.md → Phase 2b: Frost System – Temperaturabhängiger Frost (Tint-Lerp + Partikel)"
created: "2026-01-20"
status: in-progress
---

# Arbeitsauftrag: Frost Testing & Feintuning

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Phase 2b abschließend testen und feintunen:
1. Tag/Nacht-Frost-Übergang muss flüssig sein (60s Transition aus Config)
2. Partikel-Dichte bei 10–15 Spielern testen (keine Performance-Einbrüche)
3. Biome-Ausschlussliste prüfen: Keine Frost-Partikel in Desert, Savanna, Nether, End
4. Tint-Lerp visuell prüfen: Gras/Laub bleicht bei Frost zu kühlem Weiß aus
5. Zusammenspiel mit Snow Growth/Melting aus Phase 1: Keine Konflikte
6. Config-Werte in `frost.yml` final justieren (Defaults passen für 365-Tage-Jahr)
7. Performance-Profil: Tick-Auslastung durch Frost-System messen

## Aktuelles Ergebnis
- Phase 2b.1–2b.3 sind abgeschlossen: `FrostConfig`, `frost.yml`, `FrostEffectManager`, Integration in `VisualSeasonManager`
- Grundfunktionalität sollte funktionieren, aber Feintuning fehlt
- Noch nicht auf dem Live-Server getestet

## Ursachenverdacht
- Partikel könnten bei vielen Spielern zu dicht sein → `particles-per-second` evtl. senken
- Tint-Lerp könnte zu stark/zu schwach wirken → `tint-strength` justieren
- Biome-Filter könnte Lücken haben (unbekannte Mod-Biome?)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/frost.yml` | Feintuning der Default-Werte |
| `src/main/java/de/ajsch/seasons/visual/FrostEffectManager.java` | Ggf. Anpassung Partikel-Logik |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Ggf. Anpassung Tint-Lerp |
| `docs/developer-guide.md` | Dokumentation des Frost-Systems |
| `docs/handover.md` | Status-Update |
| `Plannung/roadmap.md` | Phase 2b als erledigt markieren |

## Erbetene Hilfe
1. Build & Deploy auf Server: `.\gradlew.bat shadowJar -x test` + SCP + Restart
2. Test-Matrix durchführen:
   - `/season set winter` → Frost-Effekt in Taiga/Snowy-Biomen prüfen
   - `/season set summer` → Kein Frost-Effekt
   - In Desert/Savanna auch im Winter kein Frost
   - Tag/Nacht-Wechsel: Frost kommt/geht innerhalb ~60s
3. Partikel-Dichte bewerten: `particles-per-second: 12` ggf. auf 8 senken falls zu dicht
4. Tint visuell prüfen: Grasfarbe in kaltem Biom im Winter → frostig-weißlich; im Sommer normal
5. Performance mit `/tps` oder Spark-Profiler messen: <5% Tick-Auslastung durch Frost-System
6. `frost.yml` Defaults ggf. anpassen (Transition-Seconds, Intensity-Multiplier, Tint-Strength)
7. `docs/developer-guide.md` um Frost-System ergänzen (neue Klassen, Config, Architektur)
8. `docs/handover.md` aktualisieren: Phase 2b Status, offene Punkte
9. `Plannung/roadmap.md`: Phase 2b alle Sprints abhaken
10. Build & finales Deploy

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über `frost.yml` steuerbar sein
- **Biome nie hardcoden:** Biome-Ausschluss ausschließlich über `frost.yml excluded-biomes`
- **Season deterministisch:** Ausschließlich aus `world.getFullTime()` + `yearStartOffset` berechnen
- **NMS/Reflection:** Ab Phase 2 erlaubt; Frost nutzt nur Paper-API (Partikel) + mathematischen Lerp
- **Java-Dateien ≤ 400 Zeilen:** Nach Abschluss prüfen, ob alle Klassen unter Limit sind
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