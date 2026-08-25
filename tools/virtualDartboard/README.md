# virtualDartboard

Virtuelles Dartboard + Test-Client in einer einzigen HTML-Datei. Zum Testen
der Dart-Server (anthrax/onion) ohne echte Dartscheibe: Würfe werden per
Button als 3-stellige Protokoll-Codes auf `dartboard/<id>` published, gleichzeitig
zeigt die Seite `status/gameUpdate` und `status/playSound` an.

## Benutzen

1. MQTT-Broker starten (siehe `dart-broker/README.md`) — der WebSockets-Port
   8083 wird benötigt (im mitgelieferten `mosquitto.conf` bereits aktiv).
2. Dart-Server starten.
3. `virtualDartboard.html` im Browser öffnen (Doppelklick, kein Build nötig).
4. Broker-Host/Zugangsdaten eintragen, Dartboard-ID wählen (muss zur
   Spieler-Konfiguration im Server passen), „Verbinden".
5. Würfe klicken — zwei Ansichten (Tabs):
   - **Dartboard**: klickbares SVG-Board mit echten Proportionen; Segmente,
     Triple-/Double-Ring, Bull/Bullseye direkt anklicken, der schwarze Rand
     außerhalb des Double-Rings sendet 998 (kein Treffer). Tooltip zeigt
     Feld + Code.
   - **Buttons**: Tabelle S/D/T 1–20 plus Bull/Bullseye/998 als Buttons.

   Darunter (in beiden Ansichten) die Spezialcodes 997 (Wandtreffer),
   996 (Bounce Out) und 999 (NEXT).

Die Dartboard-ID lässt sich zur Laufzeit umschalten — damit kann eine Seite
mehrere Boards simulieren (z.B. um den Dartboard-ID-Filter des Servers zu
testen). Für parallele Boards einfach die Seite mehrfach öffnen.

Bei Cricket (onion-server, `--game-mode cricket`) wird zusätzlich die
Marks-Tabelle aus dem `modeData`-Feld angezeigt.

Hinweis: Die MQTT-Bibliothek (mqtt.js) wird wie beim webapp-client per CDN
geladen — die Seite braucht daher einmalig Internetzugang bzw. einen
erreichbaren unpkg-Mirror.
