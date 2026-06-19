# Testplan Seasons

## Kurzcheckliste zum Abhaken

### Vorbereitung
- [ ] Server in Crafty starten.
- [ ] Pruefen, dass Seasons ohne Config-/Command-Fehler startet.
- [ ] Sicherstellen, dass `config.yml`, `precipitation_categories.yml` und `seasons_data.yml` im Plugin-Datenordner liegen.
- [ ] Testspieler mit OP-Rechten oder Zugriff auf `/season`.

### Schritt-fuer-Schritt-Durchlauf
- [ ] 1. `/season` zeigt Tag + Season + verbleibende Tage korrekt an.
- [ ] 2. `/season debug` zeigt erweiterte Info (Year-Offset, FullTime, Temperatur, Biom-Kategorie).
- [ ] 3. Wetter wechselt in Winter + CAN_FREEZE-Biom (z.B. Plains) zu Schnee.
- [ ] 4. In Wueste/Badlands/Savanna/Jungle bleibt Regen.
- [ ] 5. Schnee-Layer akkumulieren in geladenen Chunks bei Schneefall.
- [ ] 6. Schnee-Layer schmelzen bei positiver Temperatur (Random-Tick).
- [ ] 7. `/season skip 10` springt 10 Tage weiter und Season/Temperatur aktualisieren sich.
- [ ] 8. `/season set WINTER` setzt sofort Winter.
- [ ] 9. `/season speed 10` beschleunigt das Jahr.
- [ ] 10. Season-Wechsel feuert `SeasonChangeEvent` (im Log pruefbar).
- [ ] 11. Persistenz: Server-Restart, Jahr und Season sind wo sie waren.
- [ ] 12. `debug-mode: true` in Config → 20-Tage-Jahr.
- [ ] 13. `weather.enabled: false` deaktiviert Wetter-Manipulation.
- [ ] 14. Config-Reload uebernimmt neue Werte ohne Server-Neustart.
- [ ] 15. Keine Fehler im Server-Log.

### Fertig, wenn alles hiervon gilt
- [ ] Server startet sauber mit Seasons.
- [ ] Alle 4 Jahreszeiten durchlaufen und visuell pruefbar.
- [ ] Schnee und Wetter verhalten sich konfiguriert.
- [ ] Admin-Commands funktionieren.
- [ ] Persistenz ueber Neustarts stabil.
- [ ] Keine relevanten Fehler im Server-Log.

## Ziel
Vor jedem Release den aktuellen Feature-Stand strukturiert gegen die laufende Server-Version pruefen.

## Testumfang
Geprueft werden:
- Config-Loading und Resource-Copy
- Season-Clock und deterministische Berechnung
- Temperatur-Kurve und Biome-Offsets
- Weather-Interception (Regen → Schnee)
- Schnee-Akkumulation und -Schmelze
- Commands und Permissions
- Persistenz
- Performance (keine Tick-Blocker)

## Voraussetzungen
- Server startet in Crafty sauber mit aktueller Plugin-JAR.
- Im Plugin-Datenordner liegen mindestens:
  - `config.yml`
  - `precipitation_categories.yml`
  - `seasons_data.yml` (auto-generiert)
- Testspieler hat OP-Rechte oder Zugriff auf `/season`.
- Mehrere Biome verfuegbar (Plains, Desert, Taiga, Forest).

## Testdurchlauf

### 1. Smoke-Test Serverstart
Erwartung: Plugin startet ohne Fehler und meldet erfolgreiches Enable.
- Server in Crafty starten.
- Letzte Plugin-Logzeilen pruefen.
- Sicherstellen, dass keine Fehlermeldung zu Config-Parsing, fehlenden Ressourcen oder Command-Registrierung erscheint.

### 2. Config und Reload
Erwartung: Externe YAML-Dateien werden korrekt geladen und Reload uebernimmt Aenderungen.
- `config.yml` anpassen (z.B. `year-length-days` aendern).
- `/season reload` ausfuehren.
- Pruefen, dass die neue Einstellung sofort greift.
- Pruefen, dass `precipitation_categories.yml` aenderbar ist.

### 3. Season-Clock
Erwartung: Tage und Seasons werden korrekt berechnet.
- `/season` zeigt den aktuellen Tag und die Season.
- `/season skip 90` springt in die naechste Season.
- `/season set SUMMER` setzt sofort Sommer.
- `/season debug` zeigt konsistente Werte.

### 4. Wetter-Interception
Erwartung: Im Winter schneit es in CAN_FREEZE-Biomen.
- In ein Plains-Biom gehen, Winter per `/season set WINTER` aktivieren.
- Warten bis es regnet (oder per Vanilla `/weather rain`).
- Pruefen, dass Schnee-Partikel erscheinen statt Regen.
- In eine Wueste gehen: Regen soll normal sein.
- `weather.enabled: false` setzen und reloaden: Vanilla-Wetter soll wieder kommen.

### 5. Schnee-Akkumulation
Erwartung: Schnee-Layer entstehen in geladenen Chunks mit Schneefall.
- In Winter-Plains stehen und warten.
- Pruefen, dass Schnee-Layer entstehen und hoeher werden.
- Pruefen, dass die Hoehe temperaturabhaengig ist (kaelter = hoeher).

### 6. Schnee-Schmelze
Erwartung: Im Fruehling schmilzt Schnee.
- `/season set SPRING` ausfuehren.
- In einem Plains-Biom mit Schnee-Layern warten.
- Pruefen, dass Schnee-Layer per Random-Tick verschwinden.

### 7. Commands
Erwartung: Alle Commands funktionieren mit korrekten Permissions.
- `/season` als normaler Spieler funktioniert.
- `/season debug` erfordert OP oder `seasons.debug`.
- `/season skip/set/speed` erfordern OP oder `seasons.admin`.

### 8. Persistenz
Erwartung: Jahreszustand ueberlebt Server-Restarts.
- Season merken (z.B. Tag 200, Herbst).
- Server restart.
- `/season` zeigt wieder Tag 200, Herbst.

### 9. Performance
Erwartung: Keine Tick-Blocker durch Seasons.
- `/tps` vor und waehrend Schneefall pruefen.
- Mit `debug-mode: true` und beschleunigtem Jahr testen.

## Abnahmekriterien
Der Slice gilt als bereit fuer die naechste Phase, wenn:
- Serverstart und Reload stabil laufen.
- Alle 4 Seasons korrekt durchlaufen.
- Schnee/Wetter sichtbar und korrekt.
- Commands funktionieren mit Permissions.
- Persistenz stabil.
- Keine relevanten Fehler im Server-Log.

## Empfohlene Dokumentation waehrend des Tests
Pro Testfall kurz notieren:
- Datum/Uhrzeit
- Server-Build oder Plugin-JAR
- getestetes Biom / Bedingung
- Ergebnis: OK / Fehler
- falls Fehler: exakte Chat-Ausgabe oder Logstelle