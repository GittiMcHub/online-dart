# dartAndroidMqttConnector

Android-App (minSdk 29, targetSdk 34), die ein Bluetooth-Dartboard mit dem MQTT-Broker
verbindet — Alternative zum [Python-Connector](../dartBlueMqttConnector/).
Bedienung: MQTT-Broker und Dartboard eintragen, verbinden.

## Installation

Das signierte APK liegt in den
[GitHub-Releases](https://github.com/GittiMcHub/online-dart/releases) unter
`android-connector-v<version>` (`dartAndroidMqttConnector-<version>.apk`).
Auf dem Handy „Installation aus unbekannten Quellen" erlauben und das APK öffnen.
Neuere Versionen lassen sich direkt über eine installierte Version installieren.

## Bauen

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # unsigniert, außer die Keystore-Variablen (s.u.) sind gesetzt
```

Voraussetzungen: JDK 17+, Android SDK (z.B. über Android Studio).

## Version & Releases

Die Version steht in `app/build.gradle.kts` (`val appVersion = "1.0.0"`);
`versionCode` wird daraus berechnet (MAJOR·10000 + MINOR·100 + PATCH).
Regeln (CI: `.github/workflows/android-connector.yml`):

- **Jeder PR mit Änderungen an der App erhöht `appVersion`.** Existiert
  `android-connector-v<version>` schon, schlägt die Prüfung im PR fehl.
  Reine `*.md`-Änderungen zählen nicht. PRs werden nicht gebaut.
- Merge nach `master`: Release-APK bauen, signieren, als GitHub-Release
  `android-connector-v<version>` veröffentlichen.

## Signatur-Keystore (einmalig einrichten)

Android installiert ein Update nur, wenn es mit **demselben Schlüssel** signiert ist
wie die installierte Version. Deshalb signiert die CI mit einem festen Keystore aus
Secrets des GitHub-Environments `release` (Settings → Environments, nur für `master`
freigegeben). Die `.p12`-Datei samt Passwort sicher aufbewahren (z.B. als
Anhang im Passwort-Manager) — geht sie verloren, müssen alle Nutzer die App einmal
deinstallieren.

```bash
keytool -genkeypair -storetype PKCS12 \
        -keystore onlinedart-android-release.p12 -alias dartconnector \
        -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=onlineDart"
base64 -w0 onlinedart-android-release.p12 > keystore.b64

gh secret set ANDROID_KEYSTORE_BASE64 --env release < keystore.b64
gh secret set ANDROID_KEYSTORE_PASSWORD --env release   # Keystore-Passwort
gh secret set ANDROID_KEY_ALIAS --env release --body dartconnector
gh secret set ANDROID_KEY_PASSWORD --env release        # bei PKCS12 = Keystore-Passwort
rm keystore.b64
```

Lokal signiert bauen: dieselben Werte als Umgebungsvariablen setzen
(`ANDROID_KEYSTORE_FILE=<pfad zur .p12>`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) und `./gradlew assembleRelease` ausführen.
