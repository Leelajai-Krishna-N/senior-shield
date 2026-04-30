# Senior Shield n8n Integration

The Android app calls an n8n webhook through `BuildConfig.N8N_SCAN_WEBHOOK_URL`.

## Best setup for your team

1. Start the local model and public tunnel:

```powershell
powershell -ExecutionPolicy Bypass -File "C:\sjbit hackathon\services\model-api\start-public-api.ps1"
```

2. Copy the public URL from:

`C:\sjbit hackathon\public-model-api-url.txt`

3. Import this n8n workflow:

`C:\sjbit hackathon\n8n\senior-shield-local-model-workflow.json`

4. In n8n, set the HTTP node URL to:

`https://YOUR_PUBLIC_TUNNEL_URL/predict`

5. Activate the workflow and use the production webhook URL from n8n:

`https://YOUR-N8N-DOMAIN/webhook/senior-shield-scan`

Do not use `/webhook-test/...` in the Android app for real use.

## Expected input shape

```json
{
  "sender": "VK-HDFCBK",
  "message": "Your KYC update is pending...",
  "smsBody": "Your KYC update is pending...",
  "source": "sms",
  "trigger": "auto_sms_phishing",
  "preferredUiLanguage": "en",
  "preferredVoiceLanguage": "hi",
  "messageLanguage": "en",
  "phishingScore": 92,
  "modelLabel": "custom:phishing",
  "links": ["https://example.com"]
}
```

## Expected output shape

```json
{
  "verdict": "high_risk",
  "finalScore": 92,
  "confidence": 99,
  "familyAlertRecommended": true,
  "localized": {
    "en": {
      "explanation": "This message looks like a scam because it combines risky language, a clickable link, or urgency pressure.",
      "action": "Do not click the link or reply. Call the official number from the real website or app."
    },
    "hi": {
      "explanation": "यह संदेश धोखाधड़ी जैसा लग रहा है क्योंकि इसमें खतरनाक भाषा, लिंक या जल्दी कार्रवाई का दबाव है।",
      "action": "लिंक मत खोलिए और जवाब मत दीजिए। आधिकारिक वेबसाइट या ऐप से सही नंबर लेकर खुद कॉल कीजिए।"
    },
    "kn": {
      "explanation": "ಈ ಸಂದೇಶವು ಮೋಸದಂತಿದೆ, ಏಕೆಂದರೆ ಇದರಲ್ಲಿ ಅಪಾಯಕಾರಿ ಭಾಷೆ, ಲಿಂಕ್ ಅಥವಾ ತುರ್ತು ಒತ್ತಡ ಇದೆ.",
      "action": "ಲಿಂಕ್ ತೆರೆಯಬೇಡಿ ಮತ್ತು ಉತ್ತರಿಸಬೇಡಿ. ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್ ಅಥವಾ ಆಪ್‌ನ ಸರಿಯಾದ ಸಂಖ್ಯೆಗೆ ನೀವು ಸ್ವತಃ ಕರೆ ಮಾಡಿ."
    }
  },
  "evidence": {
    "sender": "VK-HDFCBK",
    "messageLanguage": "en",
    "originalPhishingScore": 92,
    "modelLabel": "custom:phishing",
    "links": ["https://example.com"],
    "indicators": [
      "Local fine-tuned model classified this message as phishing",
      "Message contains one or more links"
    ]
  }
}
```
