---
title: "Arbeitsauftrag 2.5.5: Build, Deploy, Funktionstest"
quelle: "roadmap.md → Phase 2.5, Sprint 2.5.5"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: open
---

# Arbeitsauftrag 2.5.5: Build, Deploy, Funktionstest

**Quelle:** roadmap.md → Phase 2.5, Sprint 2.5.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
Kompletter Build, Deployment auf den Server, Funktionstest mit visueller Prüfung der Biome-Farben bei Season-Wechseln.

## Voraussetzungen
- ProtocolLib ist auf dem Server installiert
- Sprint 2.5.1–2.5.4 sind abgeschlossen

## ToDo-Liste
1. Build: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons ; .\gradlew.bat compileJava ; .\gradlew.bat shadowJar -x test`
2. ProtocolLib auf Server prüfen:
   ```
   ssh mc@10.0.0.86 "ls /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/ProtocolLib.jar"
   ```
   Falls nicht vorhanden: ProtocolLib 5.3.0 von https://www.spigotmc.org/resources/protocollib.1997/ herunterladen und installieren
3. JAR deployen:
   ```
   scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
   ```
4. Config deployen:
   ```
   scp src\main\resources\biome_spoof.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/biome_spoof.yml"
   ```
5. Server neu starten: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
6. Test-Matrix durchführen:
   - Einloggen, 30s warten → `captureAndApply`-Logs und `ChunkPacketInterceptor`-Logs prüfen
   - `/season set fall` → **Braune/Orange Laubfarben sichtbar?**
   - `/season set winter` → **Weiße/Graue Laubfarben sichtbar?**
   - `/season set spring` → **Hellgrüne Laubfarben sichtbar?**
   - `/season set summer` → **Normale Vanilla-Farben wiederhergestellt?**
   - Wegfliegen (>128 Blöcke) und zurück – Farben bleiben?
7. Logs prüfen: `cat /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/logs/latest.log | grep -E "ChunkPacketInterceptor|BiomeSpoofAdapter"`

## Akzeptanzkriterien
- [ ] ProtocolLib wird im Log als geladen erkannt
- [ ] `ChunkPacketInterceptor`-Logs erscheinen ("Intercepted chunk=...")
- [ ] **Laubfarben ändern sich sichtbar bei jedem Season-Wechsel**
- [ ] Keine Exceptions im Log
- [ ] Keine Performance-Einbrüche (Tick-Rate bleibt stabil)
