# Local Scam Model API

This service loads the trained checkpoint from `results/` and serves HTTP prediction endpoints.

## Fastest way

Run this one command:

```powershell
powershell -ExecutionPolicy Bypass -File "C:\sjbit hackathon\services\model-api\start-public-api.ps1"
```

That will:

- start the local model server on `http://127.0.0.1:8081`
- open a free Cloudflare Tunnel
- print a public URL
- save the public base URL to `C:\sjbit hackathon\public-model-api-url.txt`

Stop everything with:

```powershell
powershell -ExecutionPolicy Bypass -File "C:\sjbit hackathon\services\model-api\stop-public-api.ps1"
```

## Manual run

```powershell
cd "C:\sjbit hackathon"
python services\model-api\server.py
```

Optional environment variables:

- `MODEL_API_HOST` default `0.0.0.0`
- `MODEL_API_PORT` default `8081`
- `MODEL_DIR` default `results`
- `MODEL_MAX_LENGTH` default `256`

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
    "confidence": 0.995,
    "probabilities": {
      "benign": 0.004,
      "phishing": 0.995
    }
  }
}
```

## n8n friend workflow

Import this file into n8n:

`C:\sjbit hackathon\n8n\senior-shield-local-model-workflow.json`

Then set the model API URL in the HTTP node to:

`https://YOUR_PUBLIC_TUNNEL_URL/predict`

or set an n8n environment variable named `SCAM_MODEL_API_URL`.
