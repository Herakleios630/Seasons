---
globs: "**/*.java"
description: Gilt bei jeder Java-Codeänderung. Verhindert das Entstehen von
  unwartbaren Monsterklassen.
alwaysApply: true
---

Keine Java-Datei über 800 Zeilen. Bei Annäherung an die Grenze in separate Klassen auslagern. Pro Klasse maximal eine Verantwortlichkeit (Single Responsibility). Listener, Services, Commands, Config-Wrapper und Datenmodelle strikt getrennt halten.