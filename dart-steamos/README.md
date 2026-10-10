# Online-Dart für SteamOS (Steam Deck / Steam Machine)

Electron-Hülle um den [onion-server](../dart-server/onion-server/), die das Dartspiel
als **Non-Steam-Game** im Wohnzimmer spielbar macht: ein einziges AppImage, das den
Server (inkl. eingebettetem MQTT-Broker und Bluetooth-Board-Anbindung) startet und
die Controller-optimierte TV-Oberfläche (`/tv/`) im Vollbild anzeigt.

Alles ist gebündelt (Electron + minimales JRE + Server-Jar) — auf dem Steam Deck
muss nichts installiert werden, auch kein Java.

## Bauen

Voraussetzungen (nur auf dem Build-Rechner, Linux x64):

- JDK 21+ (inkl. `jdeps`/`jlink`; das gebündelte JRE wird daraus erzeugt)
- Node.js 18+ und npm

```bash
cd dart-steamos
npm install
npm run dist        # lädt das gepinnte Server-Release, baut JRE (scripts/build-runtime.sh) und das AppImage
npm run dist:local  # wie dist, aber mit Fat-Jar aus ../dart-server/onion-server (lokaler Stand)
```

Woher das Server-Jar kommt, steuert `scripts/build-runtime.sh`:

| Aufruf | Server-Jar |
|---|---|
| `SERVER_JAR=/pfad/zum/jar` | genau dieses Jar |
| `--from-source` (`dist:local`) | aus den Quellen gebaut |
| `SERVER_VERSION=2.2` | Release `onion-server-v2.2` von GitHub |
| ohne Angabe (`dist`) | gepinntes Release aus `package.json` → `onionServerVersion` |

Ergebnis: `dist/OnlineDart-<version>.AppImage` (~180 MB).
Das AppImage **nicht ins Repo committen** — es ist dafür zu groß; Releases baut
und veröffentlicht die CI (siehe unten). `resources/` und `dist/` sind gitignored.

Entwicklung ohne AppImage: `npm start` nutzt das System-Java und das lokal gebaute
Fat-Jar aus `../dart-server/onion-server/build/libs/`.

## Installation auf dem Steam Deck

1. AppImage z.B. nach `~/Applications/` kopieren (das schreibgeschützte Root-Dateisystem
   von SteamOS ist egal — AppImages laufen aus dem Home-Verzeichnis).
2. Ausführbar machen: `chmod +x OnlineDart-*.AppImage`
3. Desktop-Modus → Steam → **Spiel hinzufügen → Steam-fremdes Spiel hinzufügen…** →
   Durchsuchen → das AppImage auswählen.
4. Zurück in den Gaming-Modus — „OnlineDart" erscheint in der Bibliothek.
5. Optional: Artwork über [SteamGridDB](https://www.steamgriddb.com/) hinterlegen.

## Steam Input / Controller

Die TV-Oberfläche liest den Controller direkt über die Browser-Gamepad-API.
Empfohlenes Layout ist die Standard-Vorlage **„Gamepad"** — der Controller muss als
Gamepad erscheinen, nicht als Maus/Tastatur-Emulation. Beim Start aus dem
Gaming-Modus ist das automatisch der Fall.

| Taste | Menüs / Lobby | Im Spiel |
|---|---|---|
| Steuerkreuz / linker Stick | Navigieren, Werte ändern (◀▶) | — |
| Ⓐ | Auswählen / Umschalten | Weiter (NEXT, am Zugende) |
| Ⓑ | Zurück / Abbrechen | — (absichtlich ohne Funktion) |
| Ⓧ | Löschen (Bildschirmtastatur) | Daneben / kein Treffer |
| Ⓨ | Spieler entfernen / Fertig (Tastatur) | Wandtreffer |
| ☰ Start | — | Spielmenü (Reset, Turnier abbrechen) |

Tastatur/TV-Fernbedienung funktioniert parallel: Pfeiltasten, Enter, Esc,
X/Y, F (Spielmenü) — HTPC-Fernbedienungen senden genau diese Tasten.

## Einem anderen Server beitreten (online spielen)

Hauptmenü → **„Server beitreten (online)"**: Adresse/Port/Zugang des entfernten
MQTT-Brokers eintragen (Standard-Zugang ist vorausgefüllt), verbinden, dann mit
Name + eigener Board-ID der Lobby beitreten. Startet der Host das Turnier,
wechselt die Anzeige automatisch. Solange die eigene Lobby nie geöffnet wurde,
verhält sich die App am fremden Broker rein passiv (stört weder `lobby/state`
noch join-Antworten des dortigen Servers). Netzwerk (Tailscale o.ä.) muss die
Erreichbarkeit des Brokers herstellen — Ports 1883 (MQTT) und 8083 (WebSocket).

## Dartboard hängt? Neu verbinden

Zeigt das Board „verbunden", liefert aber keine Würfe (grüne LED, nichts
passiert): ☰ **Spielmenü → „Dartboard neu verbinden"** reißt die
BLE-Verbindung ab und baut sie samt Notify-Abo neu auf — mitten im Spiel,
ohne das Turnier zu unterbrechen. Zusätzlich erneuert die App die Verbindung
automatisch, wenn 5 Minuten keine Daten kommen.

## Bluetooth-Dartboards

Das AppImage ist nicht gesandboxt und spricht den BlueZ-Dienst von SteamOS direkt
über D-Bus an (SimpleJavaBLE, linux-x64). Voraussetzung: Bluetooth ist in den
SteamOS-Einstellungen aktiviert. Schlägt die Suche fehl, zeigt die Board-Ansicht
den Fehler an — alternativ kann wie bisher der
[Python-Connector](../dart-board-connector/dartBlueMqttConnector/) auf einem anderen
Rechner die Würfe liefern.

## Netzwerk / Ports

- Web-Interface: **8420**, MQTT-WebSocket: **8083** — sind sie belegt, weicht die App
  automatisch auf die nächsten freien Ports aus.
- MQTT-TCP: **1883** (eingebetteter Broker; bei Konflikt erscheint die Fehlermeldung
  auf dem Start-Bildschirm).
- Remote-Spiel (Tailscale/Netbird o.ä.) ist Sache des Netzwerks, nicht dieser App:
  Mitspieler verbinden ihre Clients einfach gegen Broker/Weboberfläche des Decks.

## Versionen & Releases

`package.json` enthält zwei Versionen:

- `version` — die Version der App (des AppImages), Tag `steamos-v<version>`.
- `onionServerVersion` — das gepinnte onion-server-Release, das ins AppImage
  kommt. Wird **von Hand** erhöht, wenn eine neue Server-Version auf das Deck soll.

Wann `version` erhöht wird:

| Änderung | App-Version |
|---|---|
| Pin auf neuen Server-Patch (2.1 → 2.1.1) | Patch |
| Pin auf neue Server-Minor/-Major (2.1 → 2.2) — auch neue TV-UI-Features, `/tv/` liegt im Server | Minor |
| Nur Electron-Hülle (`main.js`, `preload.js`, Build-Konfiguration) | Patch oder Minor, je nach Änderung |
| Electron-Major-Update, inkompatibles Start-/Konfigurationsverhalten | Major |
| Neues Server-Release, das (noch) nicht aufs Deck soll | keine — Pin bleibt |

CI (`.github/workflows/steamos.yml`):

- **Pull Request** → AppImages als Workflow-Artefakte (7 Tage):
  - ändert der PR den onion-server: eines mit dem Server-Jar aus dem PR
    (`OnlineDart-<app>-pr<N>.<run>+server.<server>-pr<N>.<run>.AppImage`) —
    so lässt sich ein Server-Fix direkt auf dem Deck testen;
  - sonst eines mit dem gepinnten Server (`…+server.<pin>.AppImage`) und, falls
    es ein neueres onion-server-Release gibt, zusätzlich eines damit
    (`…+server.<neuste>-latest.AppImage`).
  - Der PR schlägt fehl, wenn `dart-steamos/` geändert wurde und
    `steamos-v<version>` schon existiert, oder wenn der Pin weder auf ein
    Release noch auf die Server-Version im selben PR zeigt.
- **Merge nach `master`** (bei Änderungen in `dart-steamos/`) → GitHub-Release
  `steamos-v<version>` mit dem AppImage, gebaut mit dem gepinnten Server-Release.
  Werden Server und Pin im selben PR erhöht, wartet der Job auf das Server-Release.
- Nach jedem **onion-server-Release** baut `onion-server.yml` zusätzlich ein
  `-latest`-AppImage mit dem neuen Server, solange der Pin noch älter ist —
  zum Ausprobieren vor dem Pin-Update.

### Electron

`electron` und `electron-builder` sind exakt gepinnt (Electron 36.x läuft mit
Node 18-Tooling; neuere Electron-Majors verlangen Node ≥ 22 zum Bauen). Beim
Aktualisieren zuerst auf einem Deck testen — entscheidend ist, dass die
Chromium-Laufzeit zur glibc der SteamOS-Version passt.
