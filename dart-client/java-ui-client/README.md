# java-ui-client
Der Java UI Client dient zur Vereinfachung der Handhabung für nicht so Computer versierte Nutzer. Folgende Komponenten nutzt der Java-UI-Client:  
Spieleserver "anthrax" in der Version v0.1.1  
Dartboard Connector "dartBlueMqttConnector" in der Version v0.1.2  
Bluetooth Geräte Finder "findDevices" in der Version v0.1.2  
  
dafür wird benötigt:  
```
java Version 17 (mit Version 17 und 21 getestet. Evtl. gehen auch andere)
```
Für nicht-Windows Nutzer oder Windows-Nutzer, die die Python-Skripte statt die .exe-Dateien ausführen wollen die Python Bibliotheken:  

```
python 3.12.8 (mit dieser Version getestest. Evtl. gehen auch andere)
paho-mqtt==1.6.1  
bleak==0.21.1  
async-timeout==4.0.3  
```
## client starten 
je nach Version z.B.:
```
java -jar java-ui-client-v0.1.3.jar
```
