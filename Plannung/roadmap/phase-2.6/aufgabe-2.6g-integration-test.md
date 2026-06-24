"---
title: "Arbeitsauftrag: Phase 2.6g – Integration, Build, Deploy & Funktionstest"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6g"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6g – Integration, Build, Deploy & Funktionstest

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6g

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Nachdem alle neuen Klassen aus 2.6a–2.6f erstellt sind: Finale Integration in `SeasonsPlugin.java`, sauberer Build, Deployment auf Server, und umfassender Funktionstest aller Saisons mit Farbprüfung.

## Aktuelles Ergebnis
- 2.6a–2.6f sind einzeln compilierbar, aber noch nicht im Live-Betrieb getestet
- Alte `BiomeSpoofAdapter`-Referenzen könnten noch im Plugin-Code existieren
- Generator und Datapack sind noch nicht zusammen mit dem Spoofing-System auf dem Server gelaufen
- Der Übergang Start 1 → Start 2 (Datapack-Aktivierung) muss dokumentiert werden

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | 🔄 Init aller neuen Komponenten |
| `src/main/resources/biome_spoof.yml` | 🔄 Ggf. aktualisieren |
| `src/main/resources/season_colors.yml` | 🔄 Final befüllen |
| `world/datapacks/seasons_biomes/` (Server) | 🔄 Generator ausführen |
| `docs/handover.md` | 🔄 Phase-2.6-Erfolg dokumentieren |
| `Plannung/roadmap.md` | 🔄 Phase 2.6 als done markieren |

## Erbetene Hilfe
1. **`SeasonsPlugin.java`-Integration prüfen:**
   - `BiomeSpoofAdapter`-Feld → `BiomeSpoofCoordinator` (aus 2.6d1/d2)
   - Alle neuen Klassen instanziieren: `VanillaBiomeReference` (laden), `SeasonColorConfig` (laden), `BiomeJsonGenerator`, `SeasonBiomeResolver`, `ChunkBiomeApplier`, `TransitionManager`
   - `onEnable()`: In richtiger Reihenfolge initialisieren
   - `onDisable()`: `coordinator.unregister()` + `backupStore.saveAll()`
2. **Build:** `.\\gradlew.bat clean` dann `.\\gradlew.bat compileJava` dann `.\\gradlew.bat shadowJar -x test`
   - KEINE Compile-Fehler, KEINE ProtocolLib-Referenzen
   - Keine toten Importe
3. **Deployment Vorbereitung:**
   - Altes Plugin-JAR auf Server sichern
   - Neue JAR + alle Config-Dateien (`biome_spoof.yml`, `season_colors.yml`, `precipitation_categories.yml`) kopieren
4. **Start 1 (Datapack-Generierung):**
   - Server starten → Plugin lädt
   - `/season generate-biomes` ausführen → prüfen ob JSONs in `world/datapacks/seasons_biomes/` erscheinen
   - Server stoppen
5. **Start 2 (Datapack aktiv):**
   - Server starten → Welt lädt jetzt Custom-Biomes
   - Einloggen → prüfen ob Plugin sauber lädt (keine Errors im Log)
   - `/season status` → Season anzeigen
6. **Test-Matrix – Farben prüfen:**
   - Biome-Spoofing muss laufen (Coordinator-Heartbeat im Log)
   - `/season set summer` → warten → F3 drücken: Biome = `seasons:summer_*`?
   - `/season set fall` → warten (ggf. Nächte überspringen) → Prüfen: Laub braun/orange?
   - `/season set winter` → warten → Prüfen: Laub grau? Nadelbäume grün?
   - `/season set spring` → warten → Prüfen: Zurück zu Original-Farben?
   - Mit `/time set night` + `/time set day` den Nacht-Wechsel testen
7. **Fehlerprotokoll:**
   - Alle Warnings/Errors im Server-Log dokumentieren
   - Bei visuellen Fehlern: Screenshot + F3-Debug
8. **Abschluss:**
   - `README.md` aktualisieren (neue Commands)
   - `docs/developer-guide.md` aktualisieren (neue Architektur, Generator)
   - `docs/handover.md` aktualisieren (Status Phase 2.6)
   - `Plannung/roadmap.md`: Phase-2.6-Sprints als done markieren

## Technische Randbedingungen
- **Keine NMS/Reflection:** Nur Paper-API + Registry
- **Keine ProtocolLib-Abhängigkeit mehr**
- **Datapack-Modell:** Start 1 = Generator, Start 2 = aktiv. Dokumentieren!
- **Terminal:** PowerShell-Syntax
- **Build:** `.\\gradlew.bat clean`, `.\\gradlew.bat compileJava`, `.\\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\\libs\\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `scp src\\main\\resources\\season_colors.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/season_colors.yml"`
  3. `scp src\\main\\resources\\biome_spoof.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/biome_spoof.yml"`
  4. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`"