#!/usr/bin/env bash
# Baut die gebündelte Laufzeit für das AppImage:
#   1. Fat-Jar des onion-servers besorgen (Quelle s.u.)
#   2. minimales JRE per jdeps/jlink (JDK 17, linux-x64 — jlink erzeugt eine
#      plattform-native Laufzeit, daher muss auf Linux gebaut werden)
# Ergebnis landet in resources/ (gitignored), electron-builder packt es als
# extraResources ins AppImage.
#
# Quelle des Server-Jars (erste zutreffende gewinnt):
#   SERVER_JAR=<pfad>       vorhandenes Jar verwenden (CI: PR-Builds)
#   --from-source           aus ../dart-server/onion-server bauen (lokale Entwicklung)
#   SERVER_VERSION=<v>      Release onion-server-v<v> von GitHub laden
#   (Standard)              gepinnte Version aus package.json "onionServerVersion" laden
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/.."
SERVER_DIR="$APP_DIR/../dart-server/onion-server"
RESOURCES="$APP_DIR/resources"
REPO="${GITHUB_REPOSITORY:-GittiMcHub/online-dart}"

FROM_SOURCE=false
for arg in "$@"; do
    case "$arg" in
        --from-source) FROM_SOURCE=true ;;
        *) echo "Unbekanntes Argument: $arg" >&2; exit 2 ;;
    esac
done

mkdir -p "$RESOURCES"
TARGET="$RESOURCES/onion-server-all.jar"

if [[ -n "${SERVER_JAR:-}" ]]; then
    echo "==> Jar: $SERVER_JAR (SERVER_JAR)"
    cp "$SERVER_JAR" "$TARGET"
elif $FROM_SOURCE; then
    echo "==> Fat-Jar aus Quellen bauen"
    (cd "$SERVER_DIR" && ./gradlew fatJar -q)
    JAR="$(ls "$SERVER_DIR"/build/libs/onion-server-*-all.jar | head -1)"
    cp "$JAR" "$TARGET"
    echo "==> Jar: $(basename "$JAR")"
else
    VERSION="${SERVER_VERSION:-$(node -p "require('$APP_DIR/package.json').onionServerVersion")}"
    URL="https://github.com/$REPO/releases/download/onion-server-v$VERSION/onion-server-$VERSION-all.jar"
    echo "==> Jar: onion-server $VERSION laden ($URL)"
    curl -fsSL --retry 3 -o "$TARGET.part" "$URL"
    mv "$TARGET.part" "$TARGET"
fi

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
    "$TARGET" 2>/dev/null || echo java.base)"
MODULES="$MODULES,$EXTRA_MODULES"
echo "    Module: $MODULES"

echo "==> JRE bauen (jlink)"
rm -rf "$RESOURCES/jre"
jlink --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output "$RESOURCES/jre"

echo "==> Fertig: $RESOURCES"
