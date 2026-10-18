# Protokoll & Schnittstellen

Alle Komponenten sind über **MQTT** entkoppelt. Hier stehen Topics, Nachrichtenformate und die
Übersetzung der Dartboard-Werte.

## MQTT-Zugangsdaten
Standard Benutzername: dartboard  
Standard Passwort: smartness  
Im MQTT Docker Container ändern im CLI mit:  
```
mosquitto_passwd -c /mosquitto/config/passwd <username>
```  

Benutzername und Passwort in der `src/configfiles/mqttbroker.conf` anpassen

## MQTT Topics
| Komponente   | Aktion           | Topic              |QoS|
|--------------|------------------|--------------------|--------------------|
| Client    | Subscribe        | status/gameUpdate        |1|
| Client    | Subscribe          | status/playSound    |2|
| Client    | Publish          | dartboard/[id]    |2|
| Server       | Subscribe        | dartboard/#   |2|
| Server       | Publish          | status/gameUpdate        |1|
| Server       | Publish          | status/playSound        |2|
| dartBlueMQTTConnector       | Publish          | dartboard/[id]        |2|

Der onion-server nutzt zusätzlich die Lobby-Topics `lobby/*`, siehe
[onion-server README](../dart-server/onion-server/README.md#lobby-über-mqtt-für-eigene-clients).

## Sound MQTT Nachrichtaufbau
| Sound für   | JSON-Nachricht           | Datei|
|--------------|------------------|------------------|
| treffer    | {"sound": "TREFFER"}       |treffer.wav|
| double    | {"sound": "DOUBLE"}       | double.wav|
| triple    | {"sound": "TRIPLE"}         |triple.wav| 
| bullseye       | {"sound": "BULLSEYE"}        |bullseye.wav|
| winner       | {"sound": "WINNER"}        |winner.wav |
| strafe       | {"sound": "STRAFE"}       |strafe.wav |
| ueberworfen       | {"sound": "UEBERWORFEN"}         |ueberworfen.wav|
| reset       | {"sound": "RESET"}       |reset.wav|
| spielstart       | {"sound": "SPIELSTART"}        |spielstart.wav|
| 180       | {"sound": "MAXPOINTS"}        |180.wav|

## JSON Schema für den Client
Das JSON Schema zum Validieren für weitere Serverimplementierungen befindet sich unter:
[`dart-server/schema.json`](../dart-server/schema.json)

## Dartboard Value Übersetzung (Interface)
Damit der Server universell in Zukunft funktioniert, interpretiert der Server die Felder nach folgendem Schema:  
Feste Länge: 3
| 1. Stelle   | 2. Stelle & 3. Stelle |
|--------------|------------------|
| Multiplikator   | Wert |  
  
Werte für weitere Würfe / Funktionen 
| Typ    | Wert |
|--------------|------------------|
|Bounce Out / Punkte Reset|996|
|Wandtreffer|997|
|Rand- / kein Treffer|998|
|Next|999|

Dartboard-IDs 900–999 sind im onion-server für [Bots](bot.md) reserviert.

## Smart Dartboard Übersetzungstabelle 
Wir gehen davon aus, dass alle smarten Dartboards den selben Chip verwenden. Folgend die Auflistung mit denen wir es getestet haben:  
- [x] Smartness Turbo Charger 4.0 Smart connect Dartboard[^1]  
- [x] Sunflex Smart Tec[^2]  
- [ ] Smartness Arcadia 4.0 Smart connect Dartboard[^3]  
  
Name des Bluetoothchips: `Smartness1`  
UUID: `0000ffe1-0000-1000-8000-00805f9b34fb`  

Wie die Werte ermittelt wurden, steht in [Reverse Engineering](reverse-engineering.md).

Folgend die Übersetzungstabelle für unser Dartprogramm:  
(Jedes Feld wird einzeln abgefragt. Deswegen sind für die "Feld-mal-1-Felder" 2 Hex Werte vorhanden)  
| Feld   | Dartboard Value (Hex)          | Für Server |-|Feld  | Dartboard Value (Hex)          | Für Server |-|Feld  | Dartboard Value (Hex)          | Für Server |
|--------------|------------------|--------------------|--------------------|------------------|--------------------|--------------------|--------------------|------------------|--------------------|--------------------|
|1|01 & 15 |  101||2x 1 |29   |201||3x 1 |3d    |301|
|2|02 & 16 |  102||2x 2 |2a   |202||3x 2 |3e    |302|
|3|03 & 17 |  103||2x 3 |2b   |203||3x 3 |3f    |303|
|4|04 & 18 |  104||2x 4 |2c   |204||3x 4 |40    |304|
|5|05 & 19 |  105||2x 5 |2d   |205||3x 5 |41    |305|
|6|06 & 1a |  106||2x 6 |2e   |206||3x 6 |42    |306|
|7|07 & 1b |  107||2x 7 |2f   |207||3x 7 |43    |307|
|8|08 & 1c |  108||2x 8 |30   |208||3x 8 |44    |308|
|9|09 & 1d |  109||2x 9 |31   |209||3x 9 |45    |309|
|10|0a & 1e|  110||2x10|32   |210||3x10|46    |310|
|11|0b & 1f|  111||2x11|33   |211||3x11|47    |311|
|12|0c & 20|  112||2x12|34   |212||3x12|48    |312|
|13|0d & 21|  113||2x13|35   |213||3x13|49    |313|
|14|0e & 22|  114||2x14|36   |214||3x14|4a    |314|
|15|0f & 23|  115||2x15|37   |215||3x15|4b    |315|
|16|10 & 24|  116||2x16|38   |216||3x16|4c    |316|
|17|11 & 25|  117||2x17|39   |217||3x17|4d    |317|
|18|12 & 26|  118||2x18|3a   |218||3x18|4e    |318|
|19|13 & 27|  119||2x19|3b   |219||3x19|4f    |319|
|20|14 & 28|  120||2x20|3c   |220||3x20|50    |320|
|25|51     |  125||2x25|52   |225||
|NEXT |65   |999|

[^1]: Smartness Turbo Charger 4.0  
https://shop.carromco.com/shop/dartboards/smart-connect-dartboard-turbo-charger-4-0/
[^2]: Sunflex Smart Tec Dartscheibe:  
https://www.amazon.de/Sunflex-Dartscheibe-Smart-elektronische-Ersatzspitzen/dp/B07X1TMMX4
[^3]: Smartness Arcadia 4.0  
https://shop.carromco.com/shop/dartboards/smart-connect-dartboard-arcadia-4-0/  
