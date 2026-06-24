---
title: "Arbeitsauftrag: SeasonalEffect-Interface + EffectScheduler + FrostEffectManager umbauen"
quelle: "roadmap.md → Phase 3, Sprint 3.2"
related-roadmap: "https://"
created: "2025-07-08"
status: in-progress
---

# Arbeitsauftrag: SeasonalEffect-Interface + EffectScheduler + FrostEffectManager umbauen

**Quelle:** roadmap.md → Phase 3, Sprint 3.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
Die Laufzeiteffekte (`FrostEffectManager`, später `TemperatureEffect`) sollen in ein gemeinsames Framework überführt werden, um Timer-Overhead zu reduzieren und Wiederverwendung zu fördern:

1. **`SeasonalEffect.java`** – neues Interface im Package `effects/`
2. **`EffectScheduler.java`** – neuer Singleton-Timer, der alle 20 Ticks die Online-Spieler iteriert, Temperatur+Season einmal berechnet und an alle registrierten `SeasonalEffect`-Instanzen delegiert
3. **`FrostEffectManager.java`** – Umbau: Implementiert `SeasonalEffect`, keine eigene Timer-Logik mehr
4. **`SeasonsPlugin.java`** – `EffectScheduler` initialisieren, `FrostEffectManager` registrieren, `PlayerQuitEvent`-Handler für `remove()`

## Aktuelles Ergebnis
- `FrostEffectManager` hat einen eigenen `BukkitRunnable`-Timer (20 Ticks), berechnet Temperatur+Season selbst und spawnt Partikel
- Kein `SeasonalEffect`-Interface, kein `EffectScheduler`
- Jeder neue Effekt würde einen weiteren Timer bedeuten (unnötiger Overhead)

## Ursachenverdacht
- Phase 2b wurde als isoliertes Feature implementiert; das Interface/der Scheduler waren für Phase 3 vorgesehen
- Bei 3+ Effekten würden 3+ Timer laufen – ineffizient

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/effects/SeasonalEffect.java` | NEU: Interface mit `isApplicable()`, `apply()`, `remove()` |
| `src/main/java/de/ajsch/seasons/effects/EffectScheduler.java` | NEU: 20-Tick-Timer, Spieler-Iteration, eine Temperatur/Season-Berechnung pro Spieler pro Tick |
| `src/main/java/de/ajsch/seasons/effects/FrostEffectManager.java` | Umbau: `implements SeasonalEffect`, Timer-Logik entfernen |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | EffectScheduler starten/registrieren, PlayerQuitEvent-Listener für remove() |

## Erbetene Hilfe
1. **`SeasonalEffect.java`** als Interface erstellen:
   ```java
   boolean isApplicable(Player player, double temperature, Season season, long worldTime);
   void apply(Player player, double temperature, Season season);
   void remove(Player player);
   ```
2. **`EffectScheduler.java`** erstellen:
   - 20-Tick `BukkitRunnable`
   - Hält `List<SeasonalEffect> effects`
   - `register(SeasonalEffect)` / `unregister(SeasonalEffect)`
   - `run()`: Für jeden Online-Spieler `TemperatureCalculator.getTemperature(loc)` und `SeasonClock.getCurrentSeason(world)` einmal berechnen, dann für jeden registrierten Effekt: `isApplicable()` → `apply()`
   - `cleanupPlayer(Player)`: Ruft `remove()` für alle registrierten Effekte
   - `shutdown()`: Timer stoppen, alle Effekte `remove()`
3. **`FrostEffectManager.java`** umbauen:
   - `implements SeasonalEffect`
   - Eigenen Timer (`frostTask`) entfernen
   - `isApplicable()`: Prüft `frostFactor > 0`, excluded-biomes, Cooldown (internes `lastParticleTick` pro Spieler in `Map<UUID, Long>`)
   - `apply()`: Spawnt `SNOWFLAKE`-Partikel wie bisher (bestehende Logik extrahieren)
   - `remove()`: Cleanup der internen Maps für den Spieler
   - Konstruktor erwartet kein `plugin` mehr für Timer, nur noch Config
4. **`SeasonsPlugin.java`** anpassen:
   - `EffectScheduler` als Feld, in `onEnable()` initialisieren, `FrostEffectManager` registrieren
   - `PlayerQuitEvent`-Handler: `effectScheduler.cleanupPlayer(player)`
   - `onDisable()`: `effectScheduler.shutdown()`
5. Build: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons; .\gradlew.bat compileJava` dann `; .\gradlew.bat shadowJar -x test`
6. Deployment: JAR kopieren, Server-Neustart, Frost-Partikel prüfen

## Technische Randbedingungen (gelten für jeden Auftrag)
- **Keine Magic Numbers:** Jeder numerische Wert muss über eine Config-Datei steuerbar sein
- **Biome nie hardcoden:** Immer über `precipitation_categories.yml` kategorisieren
- **Season deterministisch:** Ausschliesslich aus `world.getFullTime()` + `yearStartOffset` berechnen – kein mutable Field
- **Keine NMS/Reflection in Phase 1:** Nur Paper-API, Packet-Overrides erst ab Phase 2
- **Java-Dateien ≤ 400 Zeilen:** Ab ~350 Zeilen in separate Klassen auslagern (Single Responsibility)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` oder `single_find_and_replace`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Terminal:** Alle Befehle in PowerShell-Syntax (`Set-Location`, `;` als Trenner)
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. Wenn YAML-Configs geändert: zusätzlich die geänderten Config-Dateien kopieren (Ziel `plugins/Seasons/`)
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**
- **Sync nach jedem Slice:** `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`