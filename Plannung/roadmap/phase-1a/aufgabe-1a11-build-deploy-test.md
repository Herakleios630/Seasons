---
title: "Arbeitsauftrag: Build & Deploy + Funktionstest Phase 1a"
quelle: "roadmap.md → Phase 1a, Sprint 1a.11"
related-roadmap: "Plannung/roadmap.md → Phase 1a"
created: "2026-06-19"
status: done (vorläufig)
---

# Arbeitsauftrag: Build & Deploy + Funktionstest Phase 1a

**Quelle:** roadmap.md → Phase 1a, Sprint 1a.11

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Vollständiger Build, Deployment und Funktionstest der gesamten Phase 1a. Sicherstellen, dass die Done-Definition erfüllt ist.

## Vorbedingungen
- Alle vorherigen Phase-1a-Sprints (1a.1–1a.10) sind abgeschlossen
- Code kompiliert fehlerfrei

## Done‑Definition Phase 1a
- [ ] `ChunkCacheEntry` hält `pluginSnowHeight` und `naturalSnowHeight` sauber getrennt
- [ ] `scanChunkColumns` nutzt HeightMap MOTION_BLOCKING + 1‑Block‑Fallback
- [ ] `processChunk` platziert nur noch (kein grow), `growSnowInChunk` nur Wachstum
- [ ] `isFullyGrown` Chunks werden komplett übersprungen (0ms)
- [ ] Cache wird bei BlockBreak/Place, SeasonChange und ChunkUnload korrekt invalidiert
- [ ] JSON-Persistenz lädt und speichert Chunk-Cache asynchron
- [ ] Alle neuen Werte per Config steuerbar
- [ ] Summary-Log zeigt Cache-Hits/Misses/FullyGrown
- [ ] Keine NMS/Reflection

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `build/libs/Seasons-0.1.0-SNAPSHOT.jar` | Deployment-Artefakt |
| `src/main/resources/config.yml` | Config per SCP kopieren |
| `plugins/Seasons/chunk_cache.json` | Neue Cache-Datei auf Server prüfen |

## Erbetene Hilfe

1. **Build ausführen:**
   ```powershell
   Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons
   .\gradlew.bat compileJava
   # Fehler beheben falls vorhanden
   .\gradlew.bat shadowJar
   ```

2. **Deployment:**
   ```powershell
   scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
   scp src\main\resources\config.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/config.yml"
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
   ```
   **NICHT selbst ausführen – Befehle dem Nutzer posten.**

3. **Funktionstest auf Server:**
   - `/season debug` → 20-Tage-Jahr
   - Im Winter beobachten: Schnee platziert sich (Phase 1a Growth)
   - Prüfen, dass `isFullyGrown` Chunks übersprungen werden (Log)
   - Server-Log: `[SnowAcc] summary:` mit Cache-Stats
   - BlockBreak in beschneitem Chunk → Cache invalidiert
   - `/season set WINTER` → SeasonChange → Cache geleert
   - Server restart → `chunk_cache.json` prüfen

4. **Fehler protokollieren:**
   - Alle gefundenen Bugs in `Plannung/bugfixes/` dokumentieren
   - Bei schwerwiegenden Fehlern: Neue Arbeitsanweisung erstellen

5. **Abschluss:**
   - Done-Definition abhaken
   - `Plannung/roadmap.md` Phase 1a als abgeschlossen markieren

## Technische Randbedingungen
- **Deployment-Befehle nur posten, nicht ausführen**
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `README.md` (evtl. neue Features dokumentieren)
- `docs/developer-guide.md` (Phase 1a abschließen)
- `docs/handover.md` (Status, nächste Phase)
- `Plannung/roadmap.md` (Phase 1a: [x])