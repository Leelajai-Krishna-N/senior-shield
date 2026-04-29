# Senior Shield n8n Integration

The Android app can call an n8n webhook through `BuildConfig.N8N_SCAN_WEBHOOK_URL`.

## Expected webhook route
- `POST /webhook/scan-message`

## Input shape
```json
{
  "sender": "VK-HDFCBK",
  "message": "Your KYC update is pending...",
  "source": "sms",
  "trigger": "im_not_sure",
  "preferredUiLanguage": "en",
  "preferredVoiceLanguage": "hi",
  "links": ["https://example.com"]
}
```

## Expected output shape
```json
{
  "verdict": "high_risk",
  "finalScore": 91,
  "confidence": 88,
  "familyAlertRecommended": true,
  "localized": {
    "en": {
      "explanation": "This message is trying to rush you into clicking a suspicious link.",
      "action": "Do not open the link. Call the official source directly."
    },
    "hi": {
      "explanation": "यह संदेश आपको जल्दी में लिंक खोलने पर मजबूर कर रहा है।",
      "action": "लिंक मत खोलिए। आधिकारिक नंबर पर खुद कॉल कीजिए।"
    },
    "kn": {
      "explanation": "ಈ ಸಂದೇಶವು ನಿಮ್ಮನ್ನು ತುರ್ತಾಗಿ ಅನುಮಾನಾಸ್ಪದ ಲಿಂಕ್ ತೆರೆಯಲು ಒತ್ತಾಯಿಸುತ್ತಿದೆ.",
      "action": "ಲಿಂಕ್ ತೆರೆಯಬೇಡಿ. ಅಧಿಕೃತ ಮೂಲಕ್ಕೆ ನೀವು ಸ್ವತಃ ಕರೆ ಮಾಡಿ."
    }
  },
  "evidence": {
    "indicators": [
      "Domain age is under 30 days",
      "Form submits to external domain"
    ]
  }
}
```
