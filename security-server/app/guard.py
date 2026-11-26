import functools
import hashlib
import logging
import re
from pathlib import Path

import torch
from huggingface_hub import hf_hub_download
from nemoguardrails import LLMRails, RailsConfig
from nemoguardrails.actions import action
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from app.encoded import has_encoded_payload
from app.normalize import normalize

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
# 검사 비용 상한. 채팅 질문은 Backend에서 300자로 막고, 급여 설명은 사업장명+fact라 짧다
MAX_QUESTION_CHARS = 2000
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


def fail_closed(check):
    # NeMo가 action 예외를 삼키고 결과를 "미탐지"로 처리하므로, 검사 오류는 여기서 탐지로 바꾼다
    @functools.wraps(check)
    async def wrapper(*args, **kwargs):
        try:
            return await check(*args, **kwargs)
        except Exception:
            logging.exception("guard check %s failed; treating as detected", check.__name__)
            return True

    return wrapper


def injection_score(text: str) -> float:
    # 분류기 입력은 512토큰까지라, 64토큰씩 겹치는 구간으로 나눠 모두 보고 가장 높은 점수를 쓴다.
    # return_overflowing_tokens는 이 토크나이저에서 뒤쪽 토큰을 잃어서 직접 자른다
    ids = _tokenizer(text, add_special_tokens=False)["input_ids"]
    edge = _tokenizer("")["input_ids"]
    windows = [edge[:1] + ids[i:i + 510] + edge[1:] for i in range(0, max(len(ids) - 64, 1), 446)]
    inputs = _tokenizer.pad({"input_ids": windows}, return_tensors="pt")
    with torch.no_grad():
        probs = _model(**inputs).logits.float().softmax(dim=-1)
    return probs[:, _injection_label].max().item()


@action(name="detect_injection")
@fail_closed
async def detect_injection(text: str) -> bool:
    return injection_score(text) >= INJECTION_THRESHOLD


@action(name="detect_encoded_payload")
@fail_closed
async def detect_encoded_payload(text: str) -> bool:
    return has_encoded_payload(text)


@action(name="detect_canary_leak")
@fail_closed
async def detect_canary_leak(text: str, canary: str) -> bool:
    # 대소문자·전각·제로폭·글자 사이 공백이나 기호로 바꿔 내보내는 우회를 막으려고 영숫자만 남겨 비교
    def squash(s: str) -> str:
        return re.sub(r"[\W_]+", "", normalize(s).casefold())

    flat = squash(text)
    return squash(canary) in flat or "canarysec" in flat


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
