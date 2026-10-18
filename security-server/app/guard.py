import hashlib
from pathlib import Path

import torch
from huggingface_hub import hf_hub_download
from nemoguardrails import LLMRails, RailsConfig
from nemoguardrails.actions import action
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from app.encoded import has_encoded_payload

# 다국어(mmBERT) 기반. 선택 근거: redteam/compare_classifiers.py 결과 (영어 전용 모델은 한/베트남어 정상 질문 오탐 42%)
MODEL_ID = "patronus-studio/wolf-defender-prompt-injection-small"
# 공급망 대비: 커밋 고정 + 기동 시 파일 SHA-256 검증 (model.safetensors 값은 Hugging Face LFS 해시와 같음)
MODEL_REVISION = "cdcdf7d0231d68f39cc3bb1b70f6a2bdfca8ad55"
MODEL_SHA256 = {
    "config.json": "b5bfba7b100b4b1aa361e8160e5593695164d81ca09b47f3b26561332b520218",
    "model.safetensors": "88574ea37b2aaff38468194c544a5918b5e160fb9ee630bb11bba0aa3f533cf9",
    "special_tokens_map.json": "97f6f6ef323755568c9dddf57b7b2bac4c2019e8c10c2ef1262001a9f6487124",
    "tokenizer.json": "7e426c3929b44e6ab4c931770b5f22b913280633f5a1c67c81e9ad64decef55c",
    "tokenizer_config.json": "14b147f2a4f939d9b12ab36e9633917040dd948fa78ce283b03402e4cf2c9cba",
}
INJECTION_THRESHOLD = 0.5
BLOCKED = "__PAYCYCLE_BLOCKED__"
BLOCK_REASONS = {BLOCKED: "PROMPT_INJECTION_DETECTED", "__PAYCYCLE_BLOCKED_ENCODED__": "ENCODED_PAYLOAD_DETECTED"}
SAFE_TEXT = "보안 정책에 따라 안전한 표준 안내를 제공합니다. 급여 및 근로권리와 관련된 구체적인 질문을 입력해 주세요."

def verify_model_files(model_dir: Path, expected: dict[str, str] = MODEL_SHA256) -> None:
    for name, digest in expected.items():
        sha256 = hashlib.sha256()
        with open(Path(model_dir) / name, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                sha256.update(chunk)
        if sha256.hexdigest() != digest:
            raise RuntimeError(f"classifier model file failed integrity check: {name}")


_model_dir = [Path(hf_hub_download(MODEL_ID, name, revision=MODEL_REVISION)).parent for name in MODEL_SHA256][0]
verify_model_files(_model_dir)
_tokenizer = AutoTokenizer.from_pretrained(_model_dir)
# bf16: 메모리 절반. int8 동적 양자화는 분류 정확도를 무너뜨려 사용하지 않음
_model = AutoModelForSequenceClassification.from_pretrained(_model_dir, dtype=torch.bfloat16).eval()
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
