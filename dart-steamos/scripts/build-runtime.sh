#!/usr/bin/env bash
# Baut die gebündelte Laufzeit für das AppImage:
#   1. Fat-Jar des onion-servers (Gradle)
#   2. minimales JRE per jdeps/jlink (JDK 17, linux-x64 — jlink erzeugt eine
#      plattform-native Laufzeit, daher muss auf Linux gebaut werden)
# Ergebnis landet in resources/ (gitignored), electron-builder packt es als
# extraResources ins AppImage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/.."
SERVER_DIR="$APP_DIR/../dart-server/onion-server"
RESOURCES="$APP_DIR/resources"

echo "==> Fat-Jar bauen"
(cd "$SERVER_DIR" && ./gradlew fatJar -q)

JAR="$(ls "$SERVER_DIR"/build/libs/onion-server-*-all.jar | head -1)"
mkdir -p "$RESOURCES"
cp "$JAR" "$RESOURCES/onion-server-all.jar"
echo "==> Jar: $(basename "$JAR")"

echo "==> Benötigte Java-Module ermitteln (jdeps + Festliste)"
# jdeps unterschätzt am Fat-Jar (meldet nur java.base), daher Vereinigung mit
# einer Festliste dessen, was die Abhängigkeiten nachweislich brauchen:
#   jdk.httpserver  — WebServer (com.sun.net.httpserver)
#   jdk.unsupported — Netty/Moquette (sun.misc.Unsafe)
#   jdk.crypto.ec   — TLS-fähige MQTT-Verbindungen
#   java.logging/java.naming/java.management — SLF4J/Netty/Moquette
#   java.desktop/java.sql — Gson-Typadapter, SimpleJavaBLE
EXTRA_MODULES="jdk.httpserver,jdk.unsupported,jdk.crypto.ec,java.logging,java.naming,java.management,java.desktop,java.sql"
MODULES="$(jdeps --multi-release 17 --print-module-deps --ignore-missing-deps \
    "$RESOURCES/onion-server-all.jar" 2>/dev/null || echo java.base)"
MODULES="$MODULES,$EXTRA_MODULES"
echo "    Module: $MODULES"

echo "==> JRE bauen (jlink)"
rm -rf "$RESOURCES/jre"
jlink --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output "$RESOURCES/jre"

echo "==> Fertig: $RESOURCES"
