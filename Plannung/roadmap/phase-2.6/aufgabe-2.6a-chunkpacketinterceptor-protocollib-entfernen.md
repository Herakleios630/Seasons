---
title: \"Arbeitsauftrag: Phase 2.6a – ChunkPacketInterceptor + ProtocolLib entfernen\"
quelle: \"roadmap.md → Phase 2.6, Sprint 2.6a\"
related-roadmap: \"Plannung/roadmap.md#phase-26-custom-biome-datapack\"
created: \"2025-07-03\"
status: done
---

# Arbeitsauftrag: Phase 2.6a – ChunkPacketInterceptor + ProtocolLib entfernen

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6a

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"Seasons\"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\Seasons`

## Auftrag
`ChunkPacketInterceptor.java` komplett loeschen (157 Zeilen toter Code – hat nie ein Biome ueberschrieben, kehrt immer `return false` zurueck). Zusaetzlich die ProtocolLib-Dependency aus `build.gradle.kts` und `plugin.yml` entfernen. Das Plugin soll nach dieser Aenderung sauber ohne ProtocolLib starten.

## Erledigt
- [x] ChunkPacketInterceptor.java geloescht
- [x] ProtocolLib-Dependency aus build.gradle.kts entfernt
- [x] ProtocolLib softdepend aus plugin.yml entfernt
- [x] SeasonsPlugin.java von allen ProtocolLib/ChunkPacketInterceptor-Referenzen bereinigt
- [x] Build erfolgreich (compileJava + shadowJar), keine ProtocolLib-Referenzen mehr

## Deployment
```powershell
scp build\\libs\\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar\"
ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"
```

## Sync (noch ausstehend)
- [ ] docs/handover.md
- [ ] Plannung/roadmap.md

## Technische Randbedingungen (gelten fuer jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss ueber eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer ueber `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien <= 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeaenderung erst `.\\gradlew.bat compileJava`, dann `.\\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\\libs\\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar\"`
  2. `ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`
