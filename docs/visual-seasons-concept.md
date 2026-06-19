# Visual Seasons – Foliage Tints (Sprint 2 / Phase 2)

**Status:** Sprint-reif nach Feedback  
**Scope:** Nur Foliage/Laub-Farbwechsel via NMS (wie in roadmap.md definiert)  
**Frost-Overlay:** Ausgelagert in spätere Phase (2b)

---

## 1. Ziel

Sanfte, konfigurierbare Farbveränderung von Laub (Foliage) je nach Saison.
- Herbst: Gelb/Orange/Rot Töne
- Frühling: Frisches Grün + Kirschblüten
- Winter: Grau-braune Töne
- Übergänge sehr langsam (passend zu 365-Tage-Jahr)

Kein Frost, keine sendBlockChange, keine Partikel in dieser Phase.

---

## 2. Package-Struktur (final)

```
seasons/
└── visual/
    ├── VisualSeasonManager.java
    ├── FoliageTintManager.java
    ├── VisualConfig.java
    ├── ColorCalculator.java
    └── nms/
        └── NmsAdapter.java          # Versionierte Implementierungen
```

→ Danach `architecture-concept.md` aktualisieren.

---

## 3. Config (`foliage_tints.yml`)

```yaml
foliage:
  transition-days: 4.0

  seasons:
    SUMMER:
      default-tint: "0x7C9E4F"
      overrides:
        BIRCH: "0xA0C05F"
    FALL:
      default-tint: "0xC68A3F"
      overrides:
        OAK: "0xD45A2E"
        BIRCH: "0xF0C040"
    WINTER:
      default-tint: "0xA8B0B8"
    SPRING:
      default-tint: "0x8FD15F"
      overrides:
        CHERRY: "0xFF99CC"
```

---

## 4. Architektur

- **FoliageTintManager**: Verwaltet aktuelle Farben pro Spieler + Saison.
- **NmsAdapter**: Packet-Manipulation (MapChunk / LevelChunk Packet abfangen und Biome-Farben überschreiben).
- **VisualSeasonManager**: Koordiniert PlayerJoin, SeasonChange, periodische Updates.
- **ColorCalculator**: Lineare Interpolation zwischen Saison-Farben.

---

## 5. Ablauf

- Bei **PlayerJoin**: Aktuelle Saison-Farbe senden.
- Bei **SeasonChange**: Alle Spieler updaten.
- Periodischer Task (alle 10–30 Sekunden): Leichte Updates bei Übergang zwischen Seasons.

---

## 6. Performance & Risiken

- Nur Packet-basiert (keine sendBlockChange in Phase 2).
- NMS nur für Chunk-Packets → akzeptables Risiko.
- Fallback auf Vanilla-Farben bei NMS-Fehlern.

---

## 7. ToDo für Sprint 2

### 2.1 Foundation
- [ ] `NmsAdapter` + versionierte Implementierungen
- [ ] `VisualConfig` + `foliage_tints.yml`
- [ ] `ColorCalculator`

### 2.2 Core
- [ ] `FoliageTintManager`
- [ ] `VisualSeasonManager`

### 2.3 Integration
- [ ] PlayerJoinEvent + SeasonChangeEvent
- [ ] Scheduler für periodische Updates

### 2.4 Testing
- [ ] Test-Matrix für alle Seasons
- [ ] Kompatibilitätstest mit anderen Plugins