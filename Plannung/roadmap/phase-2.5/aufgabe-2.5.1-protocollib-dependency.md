---
title: "Arbeitsauftrag 2.5.1: ProtocolLib-Dependency + plugin.yml"
quelle: "roadmap.md → Phase 2.5, Sprint 2.5.1"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-22"
status: done
---

# Arbeitsauftrag 2.5.1: ProtocolLib-Dependency + plugin.yml

**Quelle:** roadmap.md → Phase 2.5, Sprint 2.5.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
ProtocolLib als Dependency in das Projekt einbinden und `plugin.yml` für Soft-Dependency konfigurieren.

## Aktuelles Ergebnis
- `build.gradle.kts` hat nur `paper-api` als Dependency, kein ProtocolLib
- `plugin.yml` listet keine Soft-Dependencies

## Ziel
- ProtocolLib 5.3.0 ist als `compileOnly`-Dependency verfügbar
- `plugin.yml` deklariert `softdepend: [ProtocolLib]`
- `compileJava` läuft sauber durch

## Betroffene Dateien
| Datei | Rolle |
|---|---|
| `build.gradle.kts` | ProtocolLib-Repository + Dependency hinzufügen |
| `src/main/resources/plugin.yml` | `softdepend`-Eintrag hinzufügen |

## ToDo-Liste
1. [x] `build.gradle.kts`: ProtocolLib-Maven-Repository hinzufügen:
   ```kotlin
   maven("https://repo.dmulloy2.net/repository/public/")
   ```
2. [x] `build.gradle.kts`: ProtocolLib-Dependency hinzufügen:
   ```kotlin
   compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
   ```
3. [x] `plugin.yml`: `softdepend: [ProtocolLib]` unter `api-version` einfügen
4. [x] Build testen: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons ; .\gradlew.bat compileJava`
5. [x] Prüfen dass keine Compile-Fehler auftreten