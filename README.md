# Senior Shield

Senior Shield is a native Android hackathon app for detecting phishing SMS and suspicious shared or pasted email text for senior citizens.

## What is implemented
- Real-time SMS ingestion via `SMS_RECEIVED` broadcast receiver
- Manual and shared-text message review flow
- Large-button Compose UI with Hindi voice output support
- Family alert handoff through a prefilled SMS intent
- Nested phishing detection:
  - scans message text for links
  - analyzes the visible link host
  - detects hidden nested redirect parameters like `url=`, `redirect=`, `target=`
  - flags shorteners, punycode, raw IP links, and scam wording in URLs

## Capacitor check
`Capacitor` is not the best primary choice for this app.

Why:
- SMS broadcast receivers and Android permission flows are much easier in native Android
- accessibility and senior-first UI tuning are easier to control in Compose
- background and notification behavior for phishing alerts is more natural in native code
- link and message analysis can still be shared later as plain Kotlin business logic or exposed to a web layer if needed

When Capacitor could help:
- if you later want a lightweight companion dashboard
- if you want a shared marketing/demo shell across Android and web

Recommendation:
- keep the app native for the hackathon MVP
- if needed later, move only non-Android-specific logic into a shared module or API

## Local setup
1. Open the folder in Android Studio.
2. The project is already configured to use the local SDK in `C:\Users\adith\AppData\Local\Android\Sdk`.
3. Sync Gradle and run the `app` configuration.
4. Or build from terminal with `.\gradlew.bat assembleDebug`.

## Build output
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk`
- Unit tests: `.\gradlew.bat testDebugUnitTest`

## Installed on this machine
- Android Studio installed via `winget`
- Android SDK command-line tools installed
- SDK packages installed:
  - `platform-tools`
  - `platforms;android-34`
  - `build-tools;34.0.0`
- Project `local.properties` created and pointed at the SDK

## Known hackathon limitations
- automatic email inbox scanning is not implemented because Android cannot safely read all email clients in real time
- “alert family on suspicious link click” is modeled as a strong warning plus one-tap family alert, not silent device-wide link interception
- notification and SMS behavior may need small permission tweaks depending on device brand and Android version
