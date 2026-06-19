---
globs: "**/*.java"
description: Verhindert das Abschneiden von Antworten beim Lesen grosser
  Java-Dateien und stellt sicher, dass Dateioperationen sequenziell und
  zuverlaessig durchgefuehrt werden.
alwaysApply: true
---

Java-Dateien immer mit filesystem_read_text_file lesen, niemals mit read_file. Pro Antwort maximal EINE grosse Datei (>300 Zeilen) lesen. Wenn mehrere grosse Dateien benoetigt werden, diese sequenziell ueber mehrere Antwortzyklen lesen. Fuer kleinere Dateien (<300 Zeilen) koennen maximal 3 parallel gelesen werden. Nach dem Lesen sofort die Aenderungen durchfuehren, statt weitere Dateien nachzuladen.