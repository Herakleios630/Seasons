---
title: "Arbeitsauftrag: TemperatureEffect – Potion-Debuffs"
quelle: "roadmap.md → Phase 3, Sprint 3.3"
related-roadmap: "https://"
created: "2025-07-08"
status: in-progress
---

# Arbeitsauftrag: TemperatureEffect – Potion-Debuffs bei extremen Temperaturen

**Quelle:** roadmap.md → Phase 3, Sprint 3.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
`TemperatureEffect.java` implementieren – der Effekt verleiht Spielern bei extremen Temperaturen Potion-Effekte (Hunger, Slowness, Mining Fatigue):

1. **`TemperatureEffect.java`** – neue Klasse, implementiert `SeasonalEffect`
2. **`config.yml`** um `temperature-effects`-Sektion erweitern
3. **`EffectScheduler`** registrieren (via 3.2)

## Aktuelles Ergebnis
- Es gibt keine temperaturbasierten Spieler-Debuffs
- Spieler frieren/scchwitzen nicht visuell/gameplay-technisch
- `config.yml` hat keine `temperature-effects`-Sektion

## Ursachenverdacht
- Feature war von Anfang an für Phase 3 vorgesehen
- Benötigt `SeasonalEffect`-Interface + `EffectScheduler` aus Sprint 3.2 als Voraussetzung

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/effects/TemperatureEffect.java` | NEU: `implements SeasonalEffect`, Potion-Logik |
| `src/main/resources/config.yml` | Neue Sektion `temperature-effects` |
| `src/main/java/de/ajsch/seasons/effects/EffectScheduler.java` | Registrierung (keine Änderung nötig, nur im Plugin) |
| `src/main/java/de/ajsch/seasons/SeasonsPlugin.java` | `TemperatureEffect` instanziieren und registrieren |

## Erbetene Hilfe
1. **`config.yml`** um folgende Sektion erweitern (im Ressourcen-Ordner):
   ```yaml
   temperature-effects:
     enabled: true
     interval-ticks: 40
     cold:
       hunger-threshold: -0.2
       slowness-threshold: -0.5
       mining-fatigue-threshold: -0.8
     heat:
       exhaustion-threshold: 0.8
       slowness-threshold: 1.0
   ```
2. **`TemperatureEffect.java`** erstellen:
   - `implements SeasonalEffect`
   - `isApplicable()`: Prüft `enabled`, `temp < hungerThreshold \|\| temp < slownessThreshold \|\| temp > heatExhaustionThreshold`
   - `apply()`:
     - `temp < cold.hunger-threshold` → `player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 0, true, false))`
     - `temp < cold.slowness-threshold` → `SLOWNESS, duration, 0`
     - `temp < cold.mining-fatigue-threshold` → `MINING_FATIGUE, duration, 0`
     - `temp > heat.exhaustion-threshold` → `HUNGER, duration, 1` (stärkerer Hunger)
     - `temp > heat.slowness-threshold` → `SLOWNESS, duration, 0`
     - `duration` = `interval-ticks + 10` (Puffer, damit Effekt nicht zwischen Ticks abläuft)
   - `remove()`: Entfernt alle gesetzten Potion-Effekte für den Spieler via `player.removePotionEffect(PotionEffectType.HUNGER)` etc.
   - Config-Werte aus `config.yml` lesen (analog zu `FrostConfig`-Pattern mit `ConfigurationSection`)
3. **`SeasonsPlugin.java`**: `TemperatureEffect` instanziieren, in `EffectScheduler` registrieren
4. Build: `Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons; .\gradlew.bat compileJava` dann `; .\gradlew.bat shadowJar -x test`
5. Deployment: JAR + `config.yml` kopieren, Server-Neustart, bei Winter + extremer Kälte Hunger/Slowness prüfen

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