"""Usage: python score.py   -> writes score.md from fsec_ai_chatbot_checklist.csv

적합률 = (충족 + 0.5 x 부분) / 적용 항목 수. 해당 없음(N/A)은 분모에서 제외.
"""
import csv
from pathlib import Path

HERE = Path(__file__).resolve().parent
POINTS = {"충족": 1.0, "부분": 0.5, "미충족": 0.0}
rows = [r for r in csv.DictReader((HERE / "fsec_ai_chatbot_checklist.csv").open(encoding="utf-8")) if r["적용"] == "Y"]


def rate(items, phase):
    counts = {k: sum(r[phase] == k for r in items) for k in POINTS}
    score = sum(POINTS[r[phase]] for r in items)
    return f"{100 * score / len(items):.1f}% ({counts['충족']}/{counts['부분']}/{counts['미충족']})"


groups = [
    ("전체 적용 항목", rows),
    ("이번 AI 보안 작업 대상 항목", [r for r in rows if r["이번 작업 대상"] == "Y"]),
    ("그 밖의 항목 (계정·암호화·백업·패치 등 운영 통제)", [r for r in rows if r["이번 작업 대상"] == "N"]),
]
out = [
    "# 금융보안원 AI 챗봇서비스 보안성 체크리스트 적합률",
    "",
    "기준: 금융보안원 「금융분야 AI 보안 가이드라인」(2023.4) 별첨 AI챗봇서비스 보안성 체크리스트 33개 항목.",
    f"적용 {len(rows)}개 항목 (해당 없음 {33 - len(rows)}개 제외). 판정 근거는 fsec_ai_chatbot_checklist.csv. 자체 판정이며 외부 심사 결과가 아님.",
    "",
    "| 구분 | 항목 수 | 작업 전 | 현재 |",
    "|---|---|---|---|",
    *[f"| {name} | {len(items)} | {rate(items, '작업 전')} | {rate(items, '현재')} |" for name, items in groups],
    "",
    "괄호: 충족/부분/미충족 개수. 적합률 = (충족 + 0.5 x 부분) / 적용 항목 수.",
    "",
    "## 미충족·부분 항목",
    "",
    "| no | 소분류 | 현재 | 근거·사유 |",
    "|---|---|---|---|",
    *[f"| {r['no']} | {r['소분류']} | {r['현재']} | {r['근거·사유']} |" for r in rows if r["현재"] != "충족"],
    "",
]
(HERE / "score.md").write_text("\n".join(out), encoding="utf-8")
print("\n".join(out))
