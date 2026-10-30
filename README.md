# Line

Vier oder fünf in einer Reihe – für zwei Personen an einem Gerät oder allein gegen den Computer. Läuft ohne Internet; Google AdMob zeigt Werbung (Einwilligungsabfrage inklusive).

Paketname: `de.autoknigge.line`

## Bauen
Unter **Actions → Build** läuft der Build bei jedem Push.
- Mit hinterlegten Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`): Artefakt **Line** mit `Line.apk` und `Line.aab` (identischer Stand, signiert).
- Ohne Secrets: Artefakt **Line-Test** mit einer Test-APK.

## Werbung
Die AdMob-IDs stehen in `app/build.gradle.kts` (öffentliche Kennungen) und lassen sich per Secrets `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_OPEN_ID` überschreiben.

Datenschutzerklärung: https://autoknigge.de/line/datenschutz.html
