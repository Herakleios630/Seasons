---
title: "Arbeitsauftrag: FrostEffectManager – Partikel"
quelle: "roadmap.md → Phase 2b, Sprint 2b.4"
related-roadmap: "Plannung/roadmap.md#phase-2b-frost-system"
created: "2025-07-09"
status: done
---

# Arbeitsauftrag: FrostEffectManager – Partikel

**Quelle:** roadmap.md → Phase 2b, Sprint 2b.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`FrostEffectManager.java` als neue Klasse im Package `de.ajsch.seasons.effects` schreiben. Verantwortlich für Frost-Faktor-Berechnung und `SNOWFLAKE`-Partikel-Spawns um Spieler bei aktiven Frost-Bedingungen.

## Aktuelles Ergebnis
- `FrostConfig` existiert (aus 2b.1), liefert alle benötigten Werte
- `TemperatureCalculator` existiert (Phase 1), liefert Temperatur pro Location
- Package `effects/` existiert, ist aber leer
- Kein Partikel-System für Frost vorhanden

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/effects/FrostEffectManager.java` | 🆕 Frost-Faktor, Partikel-Spawns, Biome-Filter |
| `src/main/java/de/ajsch/seasons/config/FrostConfig.java` | 📖 Gelesen: Thresholds, Partikel-Config, excluded-biomes |
| `src/main/java/de/ajsch/seasons/temperature/TemperatureCalculator.java` | 📖 Gelesen: `getTemperature(Location)` |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | 🔄 `FrostEffectManager` instanziieren + starten in `onEnable()`, stoppen in `onDisable()` |
| `src/main/java/de/ajsch/seasons/listener/PlayerJoinListener.java` | 🔄 Optional: Frost-Effekte bei PlayerJoin triggern |

## Erbetene Hilfe
1. `FrostEffectManager.java` mit folgender Struktur:
   - Konstruktor: `FrostEffectManager(SeasonsPlugin plugin, FrostConfig config)`
   - `start()`: Periodischen Bukkit-Task starten (4 Sekunden = 80 Ticks)
   - `stop()`: Task canceln
   - `tick()`: Für jeden Online-Player:
     a. Temperatur via `TemperatureCalculator.getTemperature(player.getLocation())` holen
     b. Biome-Check: `frostConfig.isFrostAllowedInBiome(player.getLocation().getBlock().getBiome())`
     c. `frostFactor = getFrostFactor(temperature)` berechnen (siehe Formel aus Konzept Abschnitt 4)
     d. Wenn `frostFactor > 0` UND Biome erlaubt → Partikel spawnen
   - `getFrostFactor(double temperature)`: `Math.clamp((freezeThreshold - temp) / (freezeThreshold - fullFrostThreshold), 0, 1)`
   - Partikel-Spawn: `player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, count, spreadX, spreadY, spreadZ, 0)`
     - `count = (int)(particlesPerSecond * frostFactor)`
     - `spread = spreadRadius` aus Config
   - Cleanup: Kein expliziter Cleanup nötig (Task prüft `player.isOnline()`)

2. `SeasonsPlugin.onEnable()` erweitern:
   - `frostEffectManager = new FrostEffectManager(this, configManager.getFrostConfig());`
   - `if (configManager.getFrostConfig().isEnabled()) frostEffectManager.start();`

3. `SeasonsPlugin.onDisable()` erweitern:
   - `if (frostEffectManager != null) frostEffectManager.stop();`

4. Build: `.\gradlew.bat compileJava`
5. Bei Compile-Fehlern korrigieren, dann `.\gradlew.bat shadowJar -x test`

## Done‑Definition
- [x] `FrostEffectManager` kompiliert fehlerfrei
- [x] `SNOWFLAKE`-Partikel spawnen bei `frostFactor > 0`
- [x] Keine Partikel in `excluded-biomes` (Wüste, Nether, End)
- [x] Partikel-Dichte skaliert mit `frostFactor`
- [x] Task läuft im 4-Sekunden-Intervall
- [x] `SeasonsPlugin` startet/stoppt den Manager sauber
- [x] Build erfolgreich