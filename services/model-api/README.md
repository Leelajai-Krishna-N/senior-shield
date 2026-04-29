# Local Scam Model API

This service loads the trained checkpoint from `results/` and serves HTTP prediction endpoints.

## Run

```powershell
cd "C:\sjbit hackathon"
python services\model-api\server.py
```

Optional environment variables:

- `MODEL_API_HOST` (default: `0.0.0.0`)
- `MODEL_API_PORT` (default: `8081`)
- `MODEL_DIR` (default: `results`)
- `MODEL_MAX_LENGTH` (default: `256`)

## Endpoints

- `GET /health`
- `POST /predict`

### Request

```json
{
  "text": "Your KYC is blocked. Click http://secure-update-info.com now"
}
```

### Response

```json
{
  "ok": true,
  "input_text": "Your KYC is blocked. Click http://secure-update-info.com now",
  "result": {
    "label": "phishing",
    "predicted_index": 1,
    "confidence": 0.98,
    "probabilities": {
      "benign": 0.02,
      "phishing": 0.98
    }
  }
}
```
