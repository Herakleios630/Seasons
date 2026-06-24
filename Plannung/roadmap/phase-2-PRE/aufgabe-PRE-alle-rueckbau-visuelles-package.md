---
title: "Arbeitsauftrag: Phase 2-PRE – Rückbau altes Visual-Seasons-Konzept"
quelle: "roadmap.md → Phase 2-PRE, alle Sprints (2-PRE.1 bis 2-PRE.4)"
related-roadmap: "roadmap.md → Phase 2-PRE"
created: "2025-02-05"
status: done
---

# Arbeitsauftrag: Phase 2-PRE – Rückbau altes Visual-Seasons-Konzept

**Quelle:** roadmap.md → Phase 2-PRE, Sprints 2-PRE.1 bis 2-PRE.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Alle Artefakte des alten NMS-basierten Phase-2-Ansatzes entfernen, Code bereinigen, Projekt auf den neuen Biome-Spoofing-Ansatz (Phase 2) vorbereiten. Es geht ausschließlich um das Löschen von totem Code – keine neuen Features.

## Aktuelles Ergebnis
- Phase 1.5 (Snow System 2.0) läuft stabil
- Alte NMS-basierte Visual-Klassen liegen noch im Projekt, werden aber nicht aktiv genutzt
- `SeasonsPlugin.java`, `PlayerJoinListener.java`, `SeasonChangeListener.java` haben noch Referenzen auf das alte `visual`-Package
- `build.gradle.kts` hat ggf. noch NMS-Abhängigkeiten

## Ursachenverdacht
Die alten Artefakte wurden beim Konzept-Wechsel von "NMS-Packet-Overrides" auf "Biome-Spoofing" nie bereinigt.

## Betroffene Schichten & Dateien

| Datei | Aktion | Status |
|---|---|---|
| `src/main/java/de/ajsch/seasons/visual/FoliageTintManager.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/visual/VisualSeasonManager.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/visual/ColorCalculator.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/visual/VisualConfig.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/visual/nms/NmsAdapter_v1_21_5.java` | Löschen | done |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | Alte Visual-Importe & Registrierungen entfernen | done |
| `src/main/java/de/ajsch/seasons/listener/PlayerJoinListener.java` | Alte Visual-Referenzen entfernen | done (keine gefunden) |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | Alte Visual-Referenzen entfernen | done (keine gefunden) |
| `src/main/resources/visual.yml` | Löschen (falls vorhanden) | n/a (nicht vorhanden) |
| `build.gradle.kts` | NMS-Abhängigkeiten prüfen/entfernen | done (netty-all entfernt) |
| `docs/visual-seasons-concept.md` | Als "verworfen" markieren, auf neues Konzept verweisen | n/a (nicht vorhanden) |

> **KEINE** neuen Dateien in dieser Phase. Nur löschen und bereinigen.

## Erbetene Hilfe

### Slice 1: Dateien löschen ✅
1. ✅ Alle 6 Java-Dateien im `visual/`-Package gelöscht
2. ✅ Leere Verzeichnisse `visual/nms/` und `visual/` entfernt
3. ✅ Keine `visual.yml` vorhanden

### Slice 2: Plugin-Registrierungen bereinigen ✅
4. ✅ `SeasonsPlugin.java`: 4 Visual-Importe, 2 Feld-Deklarationen, Init-Blöcke, Shutdown entfernt
5. ✅ `PlayerJoinListener.java`: keine Visual-Referenzen – unverändert
6. ✅ `SeasonChangeListener.java`: keine Visual-Referenzen – unverändert

### Slice 3: Build bereinigen & Build-Test ✅
7. ✅ `build.gradle.kts`: Netty-Abhängigkeit entfernt
8. ✅ Build erfolgreich: `.\gradlew.bat clean compileJava shadowJar -x test`
9. ✅ Keine Compile-Fehler

### Slice 4: Deployment & Smoke-Test (NUTZER)
10. ⬜ `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
11. ⬜ `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
12. ⬜ Smoke-Test: `/season` und `/season skip`
13. ⬜ `docs/visual-seasons-concept.md` nicht vorhanden – n/a

## Done‑Definition Phase 2-PRE
- [x] Keine NMS/Reflection-Importe mehr im gesamten Projekt
- [x] Keine `visual/` oder `nms/` Packages mehr
- [x] `SeasonsPlugin` startet sauber ohne visuelle Komponenten
- [x] Phase 1.5 funktioniert unverändert weiter
- [x] Build erfolgreich: `.\gradlew.bat compileJava shadowJar -x test`
- [ ] Server-Smoke-Test bestanden (manuell durch Nutzer)

## Technische Randbedingungen
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API (gilt auch für Bereinigung)
- **Java-Dateien ≤ 400 Zeilen:** Beim Editieren beachten
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
- **Sync nach jedem Slice:** `Plannung/roadmap.md` (Status updaten)