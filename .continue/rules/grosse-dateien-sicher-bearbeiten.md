---
globs: "**/*.java"
description: Verhindert Session-Abbrueche und Destabilisierung durch ueberladene
  Kontextfenster bei grossen Dateien. Sollte immer dann angewendet werden, wenn
  Dateien mit mehr als 300 Zeilen bearbeitet werden muessen.
alwaysApply: true
---

1. Niemals zwei Dateien mit mehr als 300 Zeilen in einer User-Nachricht kombinieren. 2. Vor Edits an grossen Dateien immer nur den relevanten Ausschnitt mit head/tail lesen, nicht die ganze Datei. 3. Edits an grossen Dateien einzeln durchfuehren, nicht als Batch. 4. single_find_and_replace ist zuverlaessiger als filesystem_edit_file und sollte bevorzugt werden. 5. Nach einem fehlgeschlagenen Tool-Aufruf zuerst die exakte Syntax pruefen (oldText/newText vs old_string/new_string), bevor weitere Schritte unternommen werden.