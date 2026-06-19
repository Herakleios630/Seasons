---
globs: "**/*.java"
description: Stellt sicher, dass alle Java-Dateien unter 400 Zeilen bleiben und
  bei Überschreitung modularisiert werden. Gilt für das gesamte Seasons-Projekt.
alwaysApply: true
---

Keine Java-Datei darf über 400 Zeilen wachsen. Bei Annäherung an die Grenze (ab ~350 Zeilen) muss die Klasse aufgeteilt werden: Logik in separate Services, Hilfsklassen oder kleinere Einheiten auslagern. Jede Klasse hat genau eine Verantwortlichkeit (Single Responsibility Principle). Listener, Services, Commands, Config-Wrapper und Datenmodelle strikt getrennt halten. Bei Überschreitung sofort Refactoring einleiten, nicht aufschieben.