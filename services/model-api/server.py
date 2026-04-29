import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


HOST = os.getenv("MODEL_API_HOST", "0.0.0.0")
PORT = int(os.getenv("MODEL_API_PORT", "8081"))
MODEL_DIR = Path(os.getenv("MODEL_DIR", "results"))
MAX_LENGTH = int(os.getenv("MODEL_MAX_LENGTH", "256"))


def resolve_model_path() -> Path:
    if (MODEL_DIR / "config.json").exists():
        return MODEL_DIR
    checkpoints = sorted(MODEL_DIR.glob("checkpoint-*"))
    if checkpoints:
        return checkpoints[-1]
    raise FileNotFoundError(f"No model found in {MODEL_DIR}")


MODEL_PATH = resolve_model_path()
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
TOKENIZER = AutoTokenizer.from_pretrained(str(MODEL_PATH))
MODEL = AutoModelForSequenceClassification.from_pretrained(str(MODEL_PATH)).to(DEVICE)
MODEL.eval()


def predict_text(text: str) -> dict[str, Any]:
    encoded = TOKENIZER(
        text,
        truncation=True,
        max_length=MAX_LENGTH,
        return_tensors="pt",
    )
    encoded = {key: value.to(DEVICE) for key, value in encoded.items()}

    with torch.no_grad():
        logits = MODEL(**encoded).logits
        probs = torch.softmax(logits, dim=-1)[0].detach().cpu().tolist()

    pred_idx = int(torch.argmax(logits, dim=-1)[0].detach().cpu().item())
    id2label = MODEL.config.id2label if MODEL.config.id2label else {0: "benign", 1: "phishing"}
    raw_label = id2label.get(pred_idx, str(pred_idx))
    label = "phishing" if raw_label.lower() in {"phishing", "label_1"} else "benign"

    return {
        "label": label,
        "predicted_index": pred_idx,
        "confidence": float(probs[pred_idx]),
        "probabilities": {
            "benign": float(probs[0]) if len(probs) > 0 else 0.0,
            "phishing": float(probs[1]) if len(probs) > 1 else 0.0,
        },
    }


class ScamModelHandler(BaseHTTPRequestHandler):
    def _json_response(self, payload: dict[str, Any], status: int = 200) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._json_response(
                {
                    "ok": True,
                    "model_path": str(MODEL_PATH),
                    "device": DEVICE,
                }
            )
            return
        self._json_response({"error": "Not found"}, status=404)

    def do_POST(self) -> None:
        if self.path != "/predict":
            self._json_response({"error": "Not found"}, status=404)
            return

        content_length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(content_length) if content_length > 0 else b""
        if not raw_body:
            self._json_response({"error": "Body required"}, status=400)
            return

        try:
            payload = json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError:
            self._json_response({"error": "Invalid JSON"}, status=400)
            return

        text = payload.get("text", "")
        if not isinstance(text, str) or not text.strip():
            self._json_response({"error": "Field 'text' must be a non-empty string"}, status=400)
            return

        result = predict_text(text.strip())
        self._json_response(
            {
                "ok": True,
                "input_text": text,
                "result": result,
            }
        )

    def log_message(self, format: str, *args: Any) -> None:
        return


def main() -> None:
    server = HTTPServer((HOST, PORT), ScamModelHandler)
    print(f"Model API listening on http://{HOST}:{PORT}")
    print(f"Loaded model from: {MODEL_PATH}")
    print(f"Device: {DEVICE}")
    server.serve_forever()


if __name__ == "__main__":
    main()
