---
title: "Arbeitsauftrag: Phase 2.6f – BiomeBackupStore Registry-Lookup statt Biome.valueOf()"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6f"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: done
---

# Arbeitsauftrag: Phase 2.6f – BiomeBackupStore Registry-Lookup statt Biome.valueOf()

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6f

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`BiomeBackupStore.java` beim Laden der JSON-Backups von `Biome.valueOf()` (Enum) auf Registry-Lookup (`Registry.BIOME.get(NamespacedKey)`) umstellen. Custom-Biomes (wie `seasons:fall_forest`) haben keinen Vanilla-Enum-Wert und müssen via Registry geladen werden.

Rückwärtskompatibilität: Alte Backups mit Vanilla-Namen (z.B. `"PLAINS"`) müssen weiterhin lesbar sein.

## Aktuelles Ergebnis
- `BiomeBackupStore.java` verwendet `Biome.valueOf(String)` (Enum) zum Deserialisieren der gespeicherten Biome-Namen
- Custom-Biomes (`seasons:fall_forest`) scheitern daran: `IllegalArgumentException: No enum constant Biome.SEASONS:FALL_FOREST`
- Ohne Registry-Lookup können Custom-Biomes nicht aus Backups wiederhergestellt werden
- Chunk-Revert würde bei Custom-Biomes fehlschlagen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/BiomeBackupStore.java` | 🔄 `valueOf()` → Registry-Lookup |

## Erbetene Hilfe
1. **`BiomeBackupStore`-Lademethode analysieren:** Datei mit `filesystem_read_text_file` lesen
2. **Deserialisierung umbauen:**
   - Alte Logik: `Biome.valueOf(name)` → `IllegalArgumentException` bei Custom-Biomes
   - Neue Logik:
     1. Zuerst `Biome.valueOf(name)` versuchen (Vanilla-Biome, schneller Pfad)
     2. Bei `IllegalArgumentException`: `NamespacedKey.fromString(name)` parsen
     3. Dann `Registry.BIOME.get(NamespacedKey)` – liefert das Biome oder `null`
     4. Bei `null`: Warnung loggen, ggf. Fallback auf `Biome.PLAINS` (oder einfach `null` an Backup-Array weitergeben)
   - Extrahiere Lookup in Hilfsmethode `resolveBiome(String name)`
3. **Rückwärtskompatibilität testen:**
   - Alte JSONs (z.B. `"PLAINS"`, `"FOREST"`, `"WARM_OCEAN"`) müssen weiterhin via `valueOf` geladen werden
   - Neue Namespaced-Strings (z.B. `"seasons:fall_forest"`) müssen via Registry geladen werden
4. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
5. **Deployment:** JAR kopieren, Server restart
6. **Test:**
   - Mit bestehenden Backups starten → keine Fehler beim Laden (Rückwärtskompatibilität)
   - Neue Custom-Biomes spoofen → Backup schreiben → Server restart → Backup laden → kein Fehler
7. Sync: `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

## Technische Randbedingungen
- **Keine NMS/Reflection:** Nur Paper-API (Registry ist Paper-API)
- **Keine Magic Numbers**
- **Terminal:** PowerShell-Syntax
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`