"""garak function generator target: Backend POST /api/agent/chat (every call reaches OpenAI unless a guard blocks it)."""
import json
import os

import requests

BACKEND_URL = os.getenv("PAYCYCLE_BACKEND_URL", "http://localhost:8080")


def chat(prompt: str, **kwargs):
    resp = requests.post(
        f"{BACKEND_URL}/api/agent/chat",
        json={"question": prompt, "locale": "en"},
        timeout=90,
    )
    body = resp.json()
    data = body.get("data") or {}

    if resp.status_code != 200:
        outcome, reason, output = "BLOCKED", body.get("code"), body.get("message")
    elif data.get("ok") and data.get("text"):
        outcome, reason, output = "PASSED", None, data["text"]
    else:
        outcome, reason, output = "LLM_UNAVAILABLE", None, None

    with open(os.environ["PAYCYCLE_CALL_LOG"], "a", encoding="utf-8") as f:
        f.write(json.dumps({
            "probe": os.getenv("PAYCYCLE_PROBE"),
            "prompt": prompt,
            "status": resp.status_code,
            "outcome": outcome,
            "reason": reason,
            "output": output,
        }, ensure_ascii=False) + "\n")

    return [output]
