# Builds, Versionen & Branches
Entwickelt wird auf kurzlebigen Branches (`fix/…`, `feat/…`), die per Pull Request direkt nach `master` gehen — einen `dev`-Branch gibt es nicht mehr. GitHub Actions baut `onion-server` und `dart-steamos` automatisch:

| Ereignis | onion-server (`.github/workflows/onion-server.yml`) | dart-steamos (`.github/workflows/steamos.yml`) |
|---|---|---|
| Pull Request | Tests + `onion-server-<version>-pr<N>.<run>-all.jar` als Workflow-Artefakt | AppImage(s) als Workflow-Artefakt, siehe unten |
| Merge nach `master` | GitHub-Release `onion-server-v<version>` mit dem Jar | GitHub-Release `steamos-v<version>` mit dem AppImage |

Workflow-Artefakte (PR-Builds) liegen 7 Tage in der Actions-Ansicht des jeweiligen Laufs („Artifacts").

Fehlt ein Release (z.B. weil ein Lauf abgebrochen wurde), lässt es sich manuell nachholen: Actions → Workflow wählen → „Run workflow" auf `master`, oder `gh workflow run android-connector.yml --ref master` (analog `onion-server.yml`, `steamos.yml`). Existiert das Release-Tag schon, passiert nichts.

## Versionen
Jede Komponente hat ihre eigene Version und ihr eigenes Tag-Präfix:
* onion-server: `version` in `dart-server/onion-server/build.gradle`. Ein PR, der den Server ändert, **muss die Version erhöhen** — der Build schlägt fehl, wenn `onion-server-v<version>` schon existiert.
* dart-steamos: `version` in `dart-steamos/package.json`, gleiche Regel für `steamos-v<version>`. Zusätzlich pinnt `onionServerVersion` dort, welches Server-Release ins AppImage kommt. Der Pin wird **von Hand** erhöht; wann dabei welche steamos-Version fällig ist, steht in [dart-steamos/README.md](../dart-steamos/README.md#versionen--releases).

## AppImages in Pull Requests
* PR ändert den onion-server → ein AppImage mit dem Server-Jar aus dem PR (`OnlineDart-<app>-pr<N>.<run>+server.<server>-pr<N>.<run>.AppImage`) — zum Testen eines Fixes direkt auf dem Steam Deck.
* sonst → ein AppImage mit dem gepinnten Server und, falls es schon ein neueres onion-server-Release gibt, zusätzlich eines mit diesem (`…+server.<neuste>-latest.AppImage`).
* Nach jedem onion-server-Release auf `master` entsteht außerdem ein `-latest`-AppImage mit dem neuen Server, solange der Pin noch auf einer älteren Version steht.

## Android-Connector
`.github/workflows/android-connector.yml` — gleiche Versionsregel, aber ohne PR-Builds: `appVersion` in `dart-board-connector/dartAndroidMqttConnector/app/build.gradle.kts` muss in jedem PR mit Code-Änderungen erhöht werden; nach dem Merge nach `master` entsteht das signierte APK als Release `android-connector-v<version>`. Einrichtung des Signatur-Keystores: [dartAndroidMqttConnector/README.md](../dart-board-connector/dartAndroidMqttConnector/README.md).

## java-ui-client
Die `v*`-Tags (z.B. `v0.1.3a`) gehören weiterhin zum java-ui-client (`.github/workflows/main.yml`).
