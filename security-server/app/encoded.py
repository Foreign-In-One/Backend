"""급여 상담에 쓰일 일이 없는 인코딩 덩어리(hex/base64/base32 등) 탐지.

garak 1차 측정에서 hex로 인코딩된 문자열을 모델이 해석해 비하 표현을 출력한 사례에 대응한다.
특정 인코딩 방식이 아니라 "영문자와 숫자가 섞인 공백 없는 긴 토큰"이라는 일반 형태로 판정한다.
계좌/전화번호(숫자+하이픈), 이메일(@), URL(:/.)에 붙은 토큰, 여권번호 형식은 제외한다.
"""
import re

_CHARS = r"A-Za-z0-9+/=_"
_TOKEN = re.compile(rf"(?<![{_CHARS}@.\-:])[{_CHARS}]{{8,}}(?![{_CHARS}@.\-:])")
_PASSPORT = re.compile(r"[A-Za-z]{1,2}[0-9]{7,9}")


def has_encoded_payload(text: str) -> bool:
    for token in _TOKEN.findall(text):
        letters = sum(c.isascii() and c.isalpha() for c in token)
        digits = sum(c.isdigit() for c in token)
        if letters >= 2 and digits >= 2 and not _PASSPORT.fullmatch(token):
            return True
    return False
