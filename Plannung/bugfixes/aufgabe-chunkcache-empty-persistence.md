---
title: "Arbeitsauftrag: Chunk-Cache-Persistenz – chunk_cache.json ist leer"
quelle: "Server-Live-Test 2025-06-19, chunk_cache.json mit leerer chunks-Map"
created: "2025-06-19"
status: offen
---

# Arbeitsauftrag: Chunk-Cache-Persistenz

**Quelle:** Server-Live-Test vom 2025-06-19 – `chunk_cache.json` enthält `{"cacheVersion": 1, "chunks": {}}`

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5

## Auftrag
`chunk_cache.json` speichert keine Chunk-Daten. Entweder läuft der asynchrone Save-Task nicht, oder er speichert ins Leere/falsche Verzeichnis. Analyse und Behebung.

## Aktuelles Ergebnis
- `plugins/Seasons/chunk_cache.json`:
  ```json
  {"cacheVersion": 1, "chunks": {}}
  ```
- Keine einzige Chunk-Cache-Entry wurde jemals persistiert
- RAM-Cache funktioniert (Summary zeigt Hits/Misses)
- Server läuft seit mehreren Minuten mit aktiver Snow-Akkumulation

## Ursachenverdacht
| Hypothese | Wahrscheinlichkeit |
|---|---|
| `ChunkCacheStore.save()` schreibt, aber auf eine andere Map-Referenz als die, die `SnowAccumulator` befüllt | Hoch |
| `startAsyncSaveTask()` wird nie gestartet oder der Scheduler läuft nicht | Mittel |
| Dateipfad ist falsch (z.B. relativ statt absolut, oder falscher DataFolder) | Mittel |
| `ChunkCacheStore.load()` überschreibt die Map mit einer leeren Map BEVOR der erste Save läuft | Niedrig |
| Save-Task läuft, aber `ConcurrentHashMap` ist zum Save-Zeitpunkt leer (Timing-Problem) | Niedrig |

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `persistence/ChunkCacheStore.java` | load/save/startAsyncSaveTask |
| `SeasonsPlugin.java` | Reihenfolge: load() vor start()? save() in onDisable? |
| `weather/SnowAccumulator.java` | `getCache()` – gibt die Map nach außen |

## Erbetene Hilfe (ToDo)

### Schritt 1: Analyse
- [ ] `ChunkCacheStore.java` vollständig lesen und prüfen:
  - Welche Map-Referenz wird in `save()` serialisiert?
  - Wurde `load()` eventuell nach einem ersten `save()` aufgerufen und hat die gefüllte Map mit einer leeren überschrieben?
  - Wohin schreibt `save()`? `getDataFolder()` + filename?
- [ ] `SeasonsPlugin.onEnable()` prüfen:
  - Reihenfolge: `chunkCacheStore = new ...`, `chunkCacheStore.load()`, `snowAccumulator.start()`, `chunkCacheStore.startAsyncSaveTask()`
  - Wird `load()` VOR `snowAccumulator.start()` aufgerufen? Wenn ja, lädt es eine leere Map und startAsyncSaveTask speichert dann die korrekte Map? Oder überschreibt load() die Map?
- [ ] `SeasonsPlugin.onDisable()` prüfen:
  - Wird `chunkCacheStore.save()` aufgerufen?
  - Hat der Server genug Zeit zum Speichern vor Shutdown?

### Schritt 2: Fix
- [ ] Korrekte Reihenfolge sicherstellen:
  1. `snowAccumulator` erzeugen (Cache-Map existiert jetzt)
  2. `chunkCacheStore` mit Referenz auf DIESELBE Map erzeugen
  3. `chunkCacheStore.load()` – lädt persistierte Daten IN die existierende Map
  4. `snowAccumulator.start()` – beginnt Platzierung/Wachstum
  5. `chunkCacheStore.startAsyncSaveTask()` – startet periodisches Speichern
- [ ] Prüfen ob `load()` die Map per `putAll()` befüllt oder per `=` überschreibt
- [ ] `chunk_cache.json` nach Fix deployed auf Server prüfen

### Schritt 3: Verifikation
- [ ] Build & Deploy
- [ ] Server starten, 2 Minuten warten, Server stoppen
- [ ] `chunk_cache.json` prüfen – sie muss jetzt Einträge enthalten
- [ ] Server erneut starten – Cache-Hits müssen ab Minute 1 hoch sein (Cache wurde geladen)

## Technische Randbedingungen
- **Terminal:** Alle Befehle in PowerShell-Syntax
- **Build:** `.\\gradlew.bat shadowJar`
- **Deploy:**
  1. `scp build\\libs\\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

## Sync nach Abschluss
- `Plannung/bugfixes/aufgabe-1a11-fixes.md` → auf diese Karte verweisen
- `docs/handover.md` → Status aktualisieren