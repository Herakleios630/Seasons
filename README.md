"# Seasons Plugin

> Vanilla‑Plus Jahreszeiten für Paper‑Minecraft

Verleiht der Overworld einen echten Jahreszeiten‑Kreislauf: 365 Minecraft‑Tage = 1 Jahr mit Frühling, Sommer, Herbst und Winter. Im Winter schneit es in passenden Biomen, im Herbst färbt sich das Laub, extreme Temperaturen beeinflussen Spieler.

Alle Werte sind über Configs steuerbar – keine Magic Numbers, kein Hardcoding pro Biom.

---

## Aktueller Stand

- **Phase 1 MVP abgeschlossen** – Seasons, Temperatur, Schnee-Akkumulation, Commands
- **Phase 2 (Visual Seasons) abgeschlossen** – Saisonale Laubfarben per NMS-Packet-Override, Chunk-Biome-Caching, sanfte Übergänge
- **Phase 2b (Frost) offen** – Temperaturabhängiger Frost als Tint-Lerp + Partikel
- Siehe [Roadmap](Plannung/roadmap.md) und [Architektur-Konzept](docs/architecture-concept.md)

---

## Zielplattform

- Paper-Server 1.21.5 (Crafty-Verwaltung)
- Java 21
- Lokal: Windows mit VS Code
- Remote: Ubuntu-Server (24 GB RAM, 3 GB VRAM), gleicher Server wie VillagerAI

---

## Build

```powershell
Set-Location C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\Seasons
.\gradlew.bat compileJava
.\gradlew.bat shadowJar
# Artefakt: build/libs/Seasons-0.1.0-SNAPSHOT.jar
```

## Deploy

1. JAR in `plugins/` kopieren
2. Server neustarten (`sudo systemctl restart crafty`) – nicht `/reload`!
3. Configs prüfen unter `plugins/Seasons/`

```powershell
scp ".\build\libs\Seasons-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/Seasons-0.1.0-SNAPSHOT.jar"
```

---

## Features (geplant)

### Phase 1 – MVP ❄️ (in Planung)
- 365‑Tage‑Jahreszeiten‑Kreislauf (Start im Frühling)
- Winter: Echter Schneefall statt Regen in passenden Biomen
- Schnee‑Platten akkumulieren temperaturabhängig
- Temperatur‑Übergänge mit Sinuskurve – sanft, keine harten Cuts
- Volle Config‑Steuerung (Jahreslänge, Biome‑Kategorien, Temperaturamplituden)
- Persistenz über Server‑Restarts
- Test‑Mode mit 20‑Tage‑Jahr
- Keine NMS/Reflection

### Phase 2 – Laub & Farben 🍂
- Herbst‑Laubfärbung (orange/rot/gelb)
- Frühling‑Kirsch‑Rosa
- Biome‑Tints per Packet‑Overrides
- Sanfte Farbübergänge über ~3 Ingame-Tage

### Phase 3 – Temperatur‑Effekte 🌡️
- Spieler‑Modifier bei Kälte/Hitze (Hunger, Speed)
- Nebel in Herbst‑Morgenstunden
- Eis‑Effekt auf stehendem Gewässer
- Extreme Temperaturen beeinflussen Wasserfälle

### Phase 4 – Fortgeschrittene Wettereffekte 🌩️
- Hagel, Monsun‑Regen, Gewitter‑Modulation
- Pro‑Chunk‑Wetter (realistischere Übergänge)

### Phase 5 – Admin‑Tooling 🧰
- Admin‑Commands (skip, set, speed)
- PlaceholderAPI‑Integration
- Performance‑Profiling

---

## Befehle & Permissions

| Befehl | Beschreibung | Permission |
|---|---|---|
| `/season` | Zeigt aktuellen Tag, Jahreszeit & Temperatur | `seasons.info` |
| `/season debug` | Erweiterte Debug‑Info | `seasons.debug` |
| `/season skip <days>` | Überspringt Tage (Test) | `seasons.admin` |
| `/season set <season>` | Setzt Jahreszeit direkt | `seasons.admin` |
| `/season speed <mult>` | Multiplikator für Zeitablauf (Test) | `seasons.admin` |

---

## Konfiguration

Hauptkonfiguration: `plugins/Seasons/config.yml`
Biome‑Kategorien: `plugins/Seasons/precipitation_categories.yml`
Persistenz: `plugins/Seasons/seasons_data.yml`

---

## Dokumentation

- [Roadmap](Plannung/roadmap.md) – Phasen, Sprints, Done‑Definitionen
- [Architektur‑Konzept](docs/architecture-concept.md) – Entscheidungen, Datenfluss, Schichten
- [Entwickler‑Guide](docs/developer-guide.md) – Struktur, Datenmodelle, Schichten-Impact
- [Handover](docs/handover.md) – Status, offene Baustellen, Prioritäten
- [Testplan](Plannung/testplan.md) – Checklisten und Testdurchläufe

---

## Projekt‑Setup (lokal)

1. Java 21 installieren
2. Repo klonen
3. `.\gradlew.bat shadowJar`
4. JAR auf Server kopieren
"