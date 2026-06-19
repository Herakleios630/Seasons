---
globs: "**/*.java,src/main/resources/precipitation_categories.yml,src/main/resources/foliage_tints.yml"
description: Gilt bei allen Änderungen, die Biome oder Biom-abhängige Logik
  berühren.
alwaysApply: true
---

Biome nie per Enum oder if/else im Code hardcoden. Immer über eine Config-Kategorie wie `precipitation_categories.yml` oder ein Config-gesteuertes Mapping lösen. Neue Biome müssen ohne Code-Änderung hinzufügbar sein.