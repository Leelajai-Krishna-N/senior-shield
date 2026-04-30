# Senior Shield

Senior Shield is a senior-first Android app that helps detect phishing SMS, scam-style shared messages, suspicious links, and pressure-based fraud attempts.

## What the app does
- Reviews incoming SMS and manually pasted/shared messages
- Detects scam intent like KYC fraud, OTP abuse, threats, panic scams, and lure messages
- Analyzes links for typosquatting-style impersonation, shorteners, punycode, nested redirects, and risky host patterns
- Uses our trained multilingual phishing model as the local AI scoring layer
- Sends the score, message body, language, and links to an n8n webhook for final yes/no reasoning
- Shows the final result in English, Hindi, or Kannada
- Reads explanations aloud and supports local-language readout for senior citizens
- Lets the user alert a trusted family member for high-risk cases

## Current build
- App version: `v1.4.1-translation-localread-fix`
- Package: `com.sjbit.seniorshield`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Main features
- Large-button senior-friendly UI
- English, Hindi, and Kannada app language support
- Default read-aloud language selection
- Slower TTS pacing for elders
- `I'm not sure about it` deep-check flow
- Final verdict controlled by webhook yes/no response
- Family alert support
- Trained local phishing model integration

## Tech stack
- Native Android
- Kotlin + Jetpack Compose
- Local phishing model served through the app integration flow
- n8n webhook explanation layer
- Sarvam + Android TTS fallback for speech

## Model summary
The project uses a fine-tuned multilingual phishing classifier based on:
- Base model: `distilbert-base-multilingual-cased`
- Trained model repo: `ClutchKrishna/scam-detector-v2`

Training highlights:
- Accuracy: about `96.8%`
- F1 score: about `96.2%`

See [MODEL_CARD.md](MODEL_CARD.md) for more details.

## Run locally
1. Open the project in Android Studio.
2. Sync Gradle.
3. Run the `app` configuration on a phone or emulator.

Or from terminal:
```powershell
.\gradlew.bat assembleDebug
```

Tests:
```powershell
.\gradlew.bat testDebugUnitTest
```

## Hackathon pitch
Senior Shield is not just a classifier. It is a senior-citizen safety assistant that combines AI scoring, human-readable reasoning, voice accessibility, local-language support, and family escalation.
