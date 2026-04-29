# Senior Shield Sandbox Worker

This service runs the server-side URL deep scan for suspicious links.

## Endpoints
- `GET /health`
- `POST /scan-url`

## Request
```json
{
  "url": "https://example.com/login"
}
```

## Response
The service returns:
- DNS, MX and NS lookups
- SSL certificate metadata
- WHOIS age when `WHOISFREAKS_API_KEY` is set
- redirect chain
- network log
- DOM flags
- screenshot thumbnail
- aggregated risk score

## Run locally
```bash
npm install
npm start
```

## Docker
```bash
docker build -t senior-shield-sandbox .
docker run -p 8080:8080 -e WHOISFREAKS_API_KEY=your_key senior-shield-sandbox
```
