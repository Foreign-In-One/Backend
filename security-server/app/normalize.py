import re
import unicodedata

# 결합 부호 블록: U+0300-036F, U+1AB0-1AFF, U+1DC0-1DFF, U+20D0-20FF, U+FE20-FE2F
_RANGES = [(0x0300, 0x036F), (0x1AB0, 0x1AFF), (0x1DC0, 0x1DFF), (0x20D0, 0x20FF), (0xFE20, 0xFE2F)]
_COMBINING = "[" + "".join(f"{chr(a)}-{chr(b)}" for a, b in _RANGES) + "]"
_COMBINING_RUN = re.compile(f"({_COMBINING}{{2}}){_COMBINING}+")


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKC", text)
    # Cf(태그 문자·제로폭·BOM 등 비가시 서식 문자)와 줄바꿈/탭 외 제어문자 제거
    text = "".join(
        ch for ch in text
        if unicodedata.category(ch) != "Cf" and (unicodedata.category(ch) != "Cc" or ch in "\n\t")
    )
    # Zalgo: 한 글자에 붙은 결합 부호는 최대 2개까지만 유지
    return _COMBINING_RUN.sub(r"\1", text).strip()
