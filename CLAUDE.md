# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Hobby project for playing electronic darts (301 variant with house rules) online against each other. Smart dartboards (Smartness/Sunflex, Bluetooth LE) send hits via a "connector" to an MQTT broker; a game server computes the game state and publishes it; clients display it. Documentation, code identifiers, comments, and CLI output are largely **German** (Spieler, Spiel, Turnier, Strafpunkte) — keep that style when extending existing components.

## Architecture

All components are decoupled via **MQTT** (Mosquitto broker, config in `dart-broker/`). Data flow:

```
Dartboard (BLE) → dart-board-connector → MQTT topic dartboard/[id]
                                              ↓
                        dart-server (subscribes dartboard/#)
                                              ↓
                 publishes status/gameUpdate (QoS 1) + status/playSound (QoS 2)
                                              ↓
                              dart-client (subscribes both)
```

- **Throw protocol**: connectors translate board hex values into fixed 3-digit strings — first digit multiplier, last two digits field value (e.g. `320` = triple 20). Special values: `996` bounce-out/reset, `997` wall hit, `998` miss, `999` next player. Translation table in `doc/protokoll.md`.
- **Game-update JSON**: the schema for `status/gameUpdate` messages lives at `dart-server/schema.json` — the contract between server implementations and all clients.
- **Default MQTT credentials**: user `dartboard`, password `smartness`, port 1883 (websockets 8083 for the webapp).

### Components

- `dart-server/onion-server` — **the standard server** (Gradle, Java 21 — the vendored SimpleJavaBLE jar is class version 65, Lombok, JUnit 5), onion architecture under `it.tobaben.dart` (domain `game/x01`, `game/cricket`, `game/board`; application `application/` with `GameEngine`/`TournamentEngine`/`ServerSession`/lobby/board services + ports; infrastructure `infrastructure/` with `MqttAdapter`, `EmbeddedBroker` (Moquette), `WebServer`, BLE via vendored SimpleJavaBLE in `libs/`). Two run paths: with `--player` the legacy one-shot tournament (`LegacyRun`), without it a long-lived app with web management on port 8420 (`/display/` score display, `/tv/` controller-optimized TV UI, REST+SSE, MQTT lobby topics `lobby/*`), modes `--mode server|client|combined`. The only part of the repo with unit tests. See `dart-server/onion-server/README.md`.
- `dart-server/anthrax-server` — the previous server, kept working (Java 17, Maven). Flat classes in default package under `src/main/java`. `Main` reads game parameters interactively via stdin (or CLI flags like `--startpunkte`, `--spieler`); `MqttHandler` runs as its own thread with `SharedData` as shared memory; `Turnier`/`Spiel`/`Spieler` hold game logic; `GameDataPublisher` + `SpielMapper`/`SpielDto` publish state. MQTT config in `mqttbroker.conf` (colon-separated key:value).
- `dart-board-connector/dartBlueMqttConnector` — Python 3 script (bleak + paho-mqtt 1.6.1) bridging BLE dartboard → MQTT. Configured via `config/dartboard.conf` + `config/mqttbroker.conf` or CLI flags.
- `dart-board-connector/dartAndroidMqttConnector` — Android app (TargetAPI 31) alternative to the Python connector.
- `dart-client/webapp-client` — plain HTML/JS/CSS, no build step; open `index.html` in a browser. MQTT connection data is hardcoded near the top of the JS in `index.html` and must be edited there.
- `dart-client/java-ui-client` — Swing app (Java 17, Maven, main class `MainFrame`) bundling score display, hit picture, statistics, and launchers for bundled anthrax/connector jars/exes (versioned binaries checked into the directory).
- `dart-client/currentPlayerPoints-client`, `currentPlayerHitpic-client` — small standalone Python display scripts.
- `dart-steamos/` — Electron wrapper packaging the onion-server + its controller-optimized `/tv/` web UI as a self-contained Linux AppImage (bundled jlink JRE, no host Java) for use as a non-Steam game on SteamOS/Steam Deck. `npm run dist` builds `dist/OnlineDart-*.AppImage` with the pinned server release, `npm run dist:local` with a jar built from source; `scripts/build-runtime.sh` produces the bundled runtime (`SERVER_JAR`/`SERVER_VERSION` env override the jar source). Electron/electron-builder are pinned exact (newer Electron majors need Node ≥22 tooling). See `dart-steamos/README.md`.
- `tools/` — bluetooth reverse-engineering scripts (`bluetoothExplorer`: `findDevices.py`, `findCharacteristics.py`, `dartbleed.py`), DIY smartboard build (`buildOwnSmartboard`), legacy offline client.

Built artifacts (jars, exes) are committed to the repo alongside sources; Windows exes are made with launch4j (`launch4jconfig` files).

## Commands

### anthrax-server (Java 17 + Maven)
```bash
cd dart-server/anthrax-server
mvn clean package
java -jar target/anthrax-1.0-SNAPSHOT-jar-with-dependencies.jar   # interactive stdin setup
# Docker alternative (needs -i for the interactive game setup):
docker build -t anthrax-server-container .
docker run -i -p 1884:1883 --name anthrax-server anthrax-server-container
```

### onion-server (Gradle)
```bash
cd dart-server/onion-server
./gradlew build
./gradlew test
./gradlew test --tests "it.tobaben.dart.game.x01.X01GameTest"   # single test class
./gradlew fatJar    # build/libs/onion-server-2.0-all.jar
java -jar build/libs/onion-server-2.0-all.jar                   # web mode, http://localhost:8420/
java -jar build/libs/onion-server-2.0-all.jar --embedded-broker --player Alice:1 --player Bob:2   # legacy one-shot
```

### java-ui-client (Java 17 + Maven)
```bash
cd dart-client/java-ui-client
mvn clean package          # shade plugin, main class MainFrame
java -jar target/java-ui-client-1.0-SNAPSHOT.jar
```

### dartBlueMqttConnector (Python)
```bash
cd dart-board-connector/dartBlueMqttConnector
pip install -r requirements.txt    # paho-mqtt 1.6.1, bleak 0.21.1, async-timeout 4.0.3
python dartBlueMqttConnector.py    # reads config/ files, or pass --mqttbrokerip … --dartboard_mac … flags
```

### MQTT broker
```bash
docker run -p 8083:8083 -p 1883:1883 \
  -v $(pwd)/dart-broker/mosquitto.conf:/mosquitto/config/mosquitto.conf \
  -v $(pwd)/dart-broker/passwd:/mosquitto/config/passwd \
  --name dartMQTTbroker eclipse-mosquitto:latest
```

## Conventions & constraints

- paho-mqtt is pinned to **1.6.1** in the Python components (2.x has a breaking callback API).
- QoS on the server cannot exceed what connectors publish with — keep QoS settings consistent across `mqttbroker.conf` files.
- Game modes: anthrax only has X01 countdown (301/501/free start score) without double-in/out; onion-server additionally has double-in/out and Cricket (extra `gameMode`/`modeData` fields in gameUpdate JSON). House rules (Schnapszahlen = repdigit penalties, wall hits, placement penalties) are part of the scoring in both.
- SimpleJavaBLE is vendored under `dart-server/onion-server/libs/` (not on Maven Central, BUSL-1.1, native lib per platform — currently linux-x64; build recipe in `libs/README.md`). BLE code stays behind the ports in `application/board/port/` so the Python connector remains a drop-in alternative.
- Branching: short-lived branches → PR → `master` (no `dev` branch anymore).
- CI/releases (`.github/workflows/onion-server.yml`, `steamos.yml`, shared `.github/actions/build-appimage`): component tags `onion-server-v<ver>` / `steamos-v<ver>` are created by CI on merge to `master`. Any PR changing onion-server must bump `version` in `build.gradle` (PR fails if the tag exists); same for `dart-steamos/package.json` `version`. PR builds are workflow artifacts (7 days) named `<ver>-pr<N>.<run>`. `dart-steamos/package.json` `onionServerVersion` pins the server release bundled into the AppImage (bumped by hand); PRs additionally get a `-latest` AppImage when a newer server release exists. Android connector (`android-connector.yml`): same bump rule for `appVersion` in `app/build.gradle.kts` (versionCode derived), PRs only check the version (no build), master builds a release APK signed with the keystore from the `release` environment secrets (master-only) → release `android-connector-v<ver>`. `v*` tags still belong to java-ui-client (`main.yml`). Details: `doc/builds-releases.md`. Docs: top-level `README.md` is an index; topic docs live in `doc/` (`komponenten`, `installation`, `protokoll`, `bot`, `reverse-engineering`, `builds-releases`).
