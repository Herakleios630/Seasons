---
title: "Arbeitsauftrag: Build & Deploy + Funktionstest Phase 1b"
quelle: "roadmap.md → Phase 1b, Sprint 1b.7"
related-roadmap: "Plannung/roadmap.md → Phase 1b"
created: "2026-06-19"
status: offen
---

# Arbeitsauftrag: Build & Deploy + Funktionstest Phase 1b

**Quelle:** roadmap.md → Phase 1b, Sprint 1b.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Vollständiger Build, Deployment und Funktionstest der gesamten Phase 1b. Sicherstellen, dass die Done-Definition erfüllt ist.

## Vorbedingungen
- Alle vorherigen Phase-1b-Sprints (1b.1–1b.6) sind abgeschlossen
- Code kompiliert fehlerfrei

## Done‑Definition Phase 1b
- [ ] Nur `pluginSnowHeight > 0` schmilzt; natürlicher Vanilla-Schnee bleibt
- [ ] Schmelze läuft nur in Frühling/Sommer/Herbst, nicht im Winter
- [ ] Pro Scan genau 1 Layer pro Spalte abgebaut
- [ ] `only-plugin-snow: true` als Default, Legacy-Mode per Config möglich
- [ ] Cache aus Phase 1a wird geteilt (ConcurrentHashMap)
- [ ] Summary-Log zeigt Melt-Statistiken
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
   - Mit `/season debug` auf 20-Tage-Jahr schalten
   - Winter abwarten: Schnee wächst (Phase 1a Growth)
   - Frühling starten: Prüfen, dass Plugin-Schnee layerweise schmilzt
   - Natürlichen Schnee in Schnee-Biom prüfen: DARF NICHT schmelzen
   - `/season set WINTER` → wieder Growth
   - `/season set SUMMER` → Melt läuft
   - Server-Log prüfen: `[Melt] summary:` Zeilen vorhanden

4. **Fehler protokollieren:**
   - Alle gefundenen Bugs in `Plannung/bugfixes/` dokumentieren
   - Bei schwerwiegenden Fehlern: Neue Arbeitsanweisung erstellen

5. **Abschluss:**
   - Done-Definition abhaken
   - `Plannung/roadmap.md` Phase 1b als abgeschlossen markieren

## Technische Randbedingungen
- **Deployment-Befehle nur posten, nicht ausführen**
- **Keine NMS/Reflection**
- **Terminal:** PowerShell-Syntax

## Sync nach Abschluss
- `README.md` (evtl. neue Config-Doku)
- `docs/developer-guide.md` (Phase 1b abschließen)
- `docs/handover.md` (Status, nächste Phase)
- `Plannung/roadmap.md` (Phase 1b: [x])