# Vendored Libraries

## simplejavable-v0.14.1-linux-x64.jar

Java-Bindings von [SimpleBLE](https://github.com/simpleble/simpleble) (SimpleJavaBLE) für die
Bluetooth-Anbindung der Dartboards. Nicht auf Maven Central verfügbar (Stand Juli 2026:
Early Preview), daher aus den Quellen gebaut und hier eingecheckt.

Das Jar enthält die Java-Klassen (`org.simplejavable.*`) **und** die native Bibliothek nur für
die Build-Plattform (`native/x64/libsimplejavable.so`, Linux x64). Auf anderen Plattformen
schlägt das Laden der Native-Library zur Laufzeit fehl — die Anwendung meldet dann
„BLE ist auf dieser Plattform nicht verfügbar" und der Python-Connector
(`dart-board-connector/dartBlueMqttConnector`) bleibt der Fallback. Für Windows/macOS ein
weiteres Jar auf der jeweiligen Plattform bauen und daneben legen
(z.B. `simplejavable-v0.14.1-windows-x64.jar`).

### Build-Rezept (Linux)

Voraussetzungen: JDK 17+, CMake ≥ 3.21, C++-Compiler, `libdbus-1-dev`.

```bash
git clone https://github.com/simpleble/simpleble.git
cd simpleble

# Stand v0.14.1 nötig: fehlende Includes in den vendored simplejni-Headern
sed -i '1a #include <stdexcept>' dependencies/internal/include/simplejni/VM.hpp \
                                 dependencies/internal/include/simplejni/Registry.hpp

cd simplejavable/java
# Toolchain-Pin ggf. an installiertes JDK anpassen (Original: 17)
sed -i 's/JavaLanguageVersion.of(17)/JavaLanguageVersion.of(21)/' build.gradle.kts

gradle jar -PbuildFromCMake     # baut zuerst die native Library via CMake
# Ergebnis: build/libs/simplejavable-v0.14.1.jar
```

### Lizenz

SimpleBLE steht unter der **Business Source License 1.1** (frei für nicht-kommerzielle
Nutzung; jede Version wird 4 Jahre nach Release GPLv3). Für dieses Hobby-Projekt unkritisch.
Details: https://github.com/simpleble/simpleble?tab=License-1-ov-file

### Hinweis JVM-Shutdown

SimpleJavaBLE startet non-daemon Native-Threads — die Anwendung beendet sich deshalb mit
`System.exit(...)` (siehe `Main`).
