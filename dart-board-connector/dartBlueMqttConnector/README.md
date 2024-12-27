# dartBlueMqttConnector

## Beispiele Python
Wird das Python Skript verwendet benötigt ihr folgende Bibliotheken:
```
paho-mqtt==1.6.1  
bleak==0.21.1  
async-timeout==4.0.3  
```
### Skript starten ohne Parameter
Wenn ihr das Python Skript ohne Parameter startet, werden die benötigten Parameter aus /config/mqttbroker.conf und /config/dartboard.conf gelesen: 
```
python dartBlueMqttConnector.py
```
### Skript starten mit Parameter
```
python dartBlueMqttConnector.py --mqttbrokerip 127.0.0.1 --mqttbrokerport 1883 --mqttuser dartboard --mqttpassword smartness --mqttqos 0 --dartboard_mac 84:C6:92:C2:7B:A7 --dartboard_uuid 0000ffe1-0000-1000-8000-00805f9b34fb --dartboard_id 1
```

## Beispiele Windows ohne Python
In der Windowsversion sind alle Bibliotheken in der EXE enthalten. Die EXE wurde mit folgendem Befehl erstellt: 
``` 
pyinstaller --onefile --hidden-import=bleak --hidden-import=async-timeout --hidden-import=winrt --hidden-import=winrt.windows.foundation.collections --hidden-import=paho-mqtt dartBlueMqttConnector.py
```
### EXE starten ohne Parameter
Wenn ihr die EXE ohne Parameter startet, werden die benötigten Parameter aus /config/mqttbroker.conf und /config/dartboard.conf gelesen (Achtet darauf, dass im Verzeichnis in der die EXE liegt, auch der config ordner liegt): 
```
dartBlueMqttConnector-v0.1.2.exe
```
### EXE starten mit Parameter
```
dartBlueMqttConnector-v0.1.2.exe --mqttbrokerip 127.0.0.1 --mqttbrokerport 1883 --mqttuser dartboard --mqttpassword smartness --mqttqos 0 --dartboard_mac 84:C6:92:C2:7B:A7 --dartboard_uuid 0000ffe1-0000-1000-8000-00805f9b34fb --dartboard_id 1
