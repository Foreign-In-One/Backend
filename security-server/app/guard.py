from pathlib import Path

import torch
from nemoguardrails import LLMRails, RailsConfig
from nemoguardrails.actions import action
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from app.encoded import has_encoded_payload

# 다국어(mmBERT) 기반. 선택 근거: redteam/compare_classifiers.py 결과 (영어 전용 모델은 한/베트남어 정상 질문 오탐 42%)
MODEL_ID = "patronus-studio/wolf-defender-prompt-injection-small"
INJECTION_THRESHOLD = 0.5
BLOCKED = "__PAYCYCLE_BLOCKED__"
BLOCK_REASONS = {BLOCKED: "PROMPT_INJECTION_DETECTED", "__PAYCYCLE_BLOCKED_ENCODED__": "ENCODED_PAYLOAD_DETECTED"}
SAFE_TEXT = "보안 정책에 따라 안전한 표준 안내를 제공합니다. 급여 및 근로권리와 관련된 구체적인 질문을 입력해 주세요."

_tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
# bf16: 메모리 절반. int8 동적 양자화는 분류 정확도를 무너뜨려 사용하지 않음
_model = AutoModelForSequenceClassification.from_pretrained(MODEL_ID, dtype=torch.bfloat16).eval()
_injection_label = next(i for i, name in _model.config.id2label.items() if name.upper() == "INJECTION")


def injection_score(text: str) -> float:
    inputs = _tokenizer(text, return_tensors="pt", truncation=True, max_length=512)
    with torch.no_grad():
        probs = _model(**inputs).logits.float().softmax(dim=-1)[0]
    return probs[_injection_label].item()


@action(name="detect_injection")
async def detect_injection(text: str) -> bool:
    return injection_score(text) >= INJECTION_THRESHOLD


@action(name="detect_encoded_payload")
async def detect_encoded_payload(text: str) -> bool:
    return has_encoded_payload(text)


@action(name="detect_canary_leak")
async def detect_canary_leak(text: str, canary: str) -> bool:
    return canary in text or "CANARY_SEC_" in text


_rails = LLMRails(RailsConfig.from_path(str(Path(__file__).parent / "guardrails")))
_rails.register_action(detect_encoded_payload)
_rails.register_action(detect_injection)
_rails.register_action(detect_canary_leak)


async def check_input(text: str) -> str | None:
    result = await _rails.generate_async(
        messages=[{"role": "user", "content": text}], options={"rails": ["input"]}
    )
    return BLOCK_REASONS.get(result.response[-1]["content"])


async def check_output(text: str, canary: str) -> bool:
    result = await _rails.generate_async(
        messages=[
            {"role": "context", "content": {"canary": canary}},
            {"role": "user", "content": ""},
            {"role": "assistant", "content": text},
        ],
        options={"rails": ["output"]},
    )
    return result.response[-1]["content"] == BLOCKED
