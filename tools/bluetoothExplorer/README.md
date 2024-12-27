# findDevices

## Beispiele Python
Wird das Python Skript verwendet benötigt ihr folgende Bibliotheken:
```
paho-mqtt==1.6.1  
bleak==0.21.1  
async-timeout==4.0.3  
```
### Skript starten
```
python findDevices.py
```

## Beispiele Windows ohne Python
In der Windowsversion sind alle Bibliotheken in der EXE enthalten. Die EXE wurde mit folgendem Befehl erstellt: 
``` 
pyinstaller --onefile --hidden-import=bleak --hidden-import=async-timeout --hidden-import=winrt --hidden-import=winrt.windows.foundation.collections findDevices.py
```
### EXE starten
```
findDevices-v0.1.2.exe
```