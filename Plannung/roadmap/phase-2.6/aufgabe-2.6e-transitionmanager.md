---
title: "Arbeitsauftrag: Phase 2.6e – TransitionManager (Nacht-Check, Stufen-Tracking)"
quelle: "roadmap.md → Phase 2.6, Sprint 2.6e"
related-roadmap: "Plannung/roadmap.md#phase-26-custom-biome-datapack"
created: "2025-07-03"
status: in-progress
---

# Arbeitsauftrag: Phase 2.6e – TransitionManager (Nacht-Check, Stufen-Tracking)

**Quelle:** roadmap.md → Phase 2.6, Sprint 2.6e

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "Seasons"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.5
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\Seasons`

## Auftrag
**`TransitionManager.java`** – Neue Klasse im `visual`-Package. Zustandsmaschine, die den schrittweisen Biome-Wechsel über mehrere Nächte steuert (Sub-Varianten-Tracking).

Trigger: `SeasonChangeEvent` → initialisiert eine Transition. Alle 40 Ticks prüft der Manager, ob die Nacht erreicht ist. Wenn ja: `currentStep++`, alle gespooften Chunks auf die nächste Sub-Variante aktualisieren.

## Aktuelles Ergebnis
- Kein Transition-System existiert
- `BiomeSpoofListener.onSeasonChange()` setzt lediglich `seasonTransitionUntil` (toter Code)
- Season-Wechsel ist serverseitig instant (alle Chunks sofort auf Ziel-Biom)
- Keine sanften Übergänge zwischen Seasons

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/seasons/visual/TransitionManager.java` | 🆕 Neu: Zustandsmaschine |
| `src/main/java/de/ajsch/seasons/visual/BiomeSpoofCoordinator.java` | 🔄 Erweitern: TransitionManager einbinden |
| `src/main/java/de/ajsch/seasons/listener/SeasonChangeListener.java` | 🔄 An TransitionManager delegieren |
| `src/main/java/de/ajsch/seasons/visual/SeasonColorConfig.java` | Nutzt `getTransitionSteps()` |
| `src/main/resources/biome_spoof.yml` | 🔄 Erweitern: `transition.nights_per_step` |

## Erbetene Hilfe
1. **`TransitionManager.java` NEU anlegen:**
   - Felder:
     - `fromSeason`, `toSeason` (Season)
     - `totalSteps` (int) – aus `SeasonColorConfig.getTransitionSteps(from, to)`
     - `currentStep` (int, 0 = noch nicht gestartet)
     - `nextTransitionTick` (long) – Welt-Tick des nächsten Schritts
     - `variantSequence` (String[]) – **Sub-Varianten-Namen OHNE Biome-Key**, z.B. `["late_summer", "early_fall", "mid_fall", "fall"]`
     - `active` (boolean)
   - Konstruktor: `TransitionManager(SeasonColorConfig)`
   - `startTransition(Season from, Season to, World world)`:
     - `totalSteps` aus Config lesen
     - `currentStep = 0`
     - `variantSequence` bauen: Für jeden Schritt den Variant-Namen (ohne Biome!), z.B. `["late_summer", "early_fall", …]`
     - Nächste Nacht berechnen: `world.getTime()` bis zur nächsten Nacht (13000), ggf. `+ 24000`
     - `active = true`
   - `tick(long currentTick, World world)`:
     - Nur wenn `active == true`
     - Prüfen ob `currentTick >= nextTransitionTick`
     - Wenn ja: `currentStep++` → `notifyStepChange()` → `nextTransitionTick += 24000 * nights_per_step`
     - Wenn `currentStep >= totalSteps`: Transition beendet (`active = false`)
   - `getCurrentVariant()`: Liefert aktuellen Variant-Namen OHNE Biome (z.B. `"early_fall"`).
     **Kein** festes `getCurrentStepBiome(Biome)` mehr – die Kombination `<variant>_<biomeKey>` wird vom Resolver gebaut!
   - `isActive()`: Ob eine Transition läuft
   - **Max ~150 Zeilen**
2. **`BiomeSpoofCoordinator.java` erweitern:**
   - TransitionManager als Feld + im Konstruktor akzeptieren
   - `run()`: Wenn TransitionManager aktiv → `tick()` aufrufen → `tm.getCurrentVariant()` (z.B. `"early_fall"`) an `resolver.resolveTargetBiome(chunk, season, variant)` übergeben – der Resolver kombiniert zum vollen Namen `seasons:early_fall_swamp`
   - `onSeasonChange(Season from, Season to)`: Alte Transition verwerfen + neue starten
3. **`SeasonChangeListener.java` anpassen:**
   - Statt `adapter.setSeasonTransitionUntil(...)` → `coordinator.getTransitionManager().startTransition(...)`
4. **`biome_spoof.yml` erweitern:**
   - Neuen Abschnitt `transition:` mit `nights_per_step: 1` hinzufügen
   - `ConfigManager` erweitern: `getTransitionNightsPerStep()`
5. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
6. **Deployment:** JAR + `biome_spoof.yml` kopieren, Server restart
7. **Test:**
   - Mit `/season set summer` Sommer sicherstellen
   - `/season set fall` → prüfen ob Transition startet (Log)
   - Warten bis Nacht → prüfen ob Chunk-Biome auf Sub-Variante wechseln
8. Sync: `README.md`, `docs/developer-guide.md`, `docs/handover.md`, `Plannung/roadmap.md`

## Technische Randbedingungen
- **Keine NMS/Reflection:** Nur Paper-API
- **Java-Dateien ≤ 400 Zeilen:** TransitionManager ≤ 150 Z.
- **Terminal:** PowerShell-Syntax
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/Seasons-0.1.0-SNAPSHOT.jar`
- **Deploy:**
  1. `scp build\libs\Seasons-0.1.0-SNAPSHOT.jar mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"`
  2. `scp src\main\resources\biome_spoof.yml mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons/biome_spoof.yml"`
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` – **KEIN `/reload`**