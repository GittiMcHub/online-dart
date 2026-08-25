# dart-broker

Vorkonfigurierter MQTT-Broker (eclipse-mosquitto) für onlineDart.
Dieser Ordner enthält:

| Datei            | Zweck                                                        |
|------------------|--------------------------------------------------------------|
| `mosquitto.conf` | Broker-Konfiguration: MQTT auf Port 1883, WebSockets auf 8083 (für den Webapp-Client), Authentifizierung über Passwortdatei, Persistenz |
| `passwd`         | Passwortdatei, vorbelegt mit Benutzer `dartboard` / Passwort `smartness` |

Alternative ohne Docker: der onion-server bringt mit `--embedded-broker`
einen eingebetteten MQTT-Broker (Moquette, TCP 1883 + WebSockets 8083) mit —
praktisch für den Einzelrechner-Betrieb; für den Dauerbetrieb bleibt der
eigenständige Mosquitto hier die Empfehlung.

## Broker starten (Docker)

Im Verzeichnis `dart-broker` ausführen:

```bash
docker run -d \
  --name dartMQTTbroker \
  -p 1883:1883 \
  -p 8083:8083 \
  -v "$(pwd)/mosquitto.conf:/mosquitto/config/mosquitto.conf" \
  -v "$(pwd)/passwd:/mosquitto/config/passwd" \
  -v dartmqtt-data:/mosquitto/Data \
  eclipse-mosquitto:latest
```

Windows (PowerShell):

```powershell
docker run -d `
  --name dartMQTTbroker `
  -p 1883:1883 `
  -p 8083:8083 `
  -v "${PWD}\mosquitto.conf:/mosquitto/config/mosquitto.conf" `
  -v "${PWD}\passwd:/mosquitto/config/passwd" `
  -v dartmqtt-data:/mosquitto/Data `
  eclipse-mosquitto:latest
```

- `-d` startet den Broker im Hintergrund. Logs ansehen: `docker logs -f dartMQTTbroker`
- Das benannte Volume `dartmqtt-data` hält die Persistenz-Daten
  (`persistence_location /mosquitto/Data/` aus der mosquitto.conf) über
  Container-Neustarts hinweg.
- Später stoppen/starten: `docker stop dartMQTTbroker` / `docker start dartMQTTbroker`

## Ports

| Port | Protokoll  | Genutzt von                                          |
|------|------------|------------------------------------------------------|
| 1883 | MQTT       | Dart-Server (anthrax/onion), Dartboard-Connector, Python-Clients, java-ui-client |
| 8083 | WebSockets | webapp-client (MQTT über den Browser)                |

## Benutzername/Passwort ändern

Anonyme Verbindungen sind deaktiviert (`allow_anonymous false`). Standardzugang:
Benutzer `dartboard`, Passwort `smartness`.

Zum Ändern im laufenden Container:

```bash
docker exec -it dartMQTTbroker mosquitto_passwd -c /mosquitto/config/passwd <benutzername>
docker restart dartMQTTbroker
```

Anschließend die Zugangsdaten in allen Komponenten anpassen
(z.B. `mqttbroker.conf` von Server und Connector, Webapp-Konfiguration).

## Verbindung testen

```bash
mosquitto_sub -h localhost -u dartboard -P smartness -t 'dartboard/#' -v
```

Alternativ mit dem [MQTT Explorer](http://mqtt-explorer.com/). Wenn ein
Dartboard-Connector läuft, erscheinen die Würfe als 3-stellige Codes auf
`dartboard/<id>`.

## Hinweis für Online-Spiele

Wenn Spieler über das Internet verbunden sind: Router-Portfreigabe und
Firewall für 1883 (und 8083 für den Webclient) konfigurieren — siehe
Troubleshooting im Haupt-README.
