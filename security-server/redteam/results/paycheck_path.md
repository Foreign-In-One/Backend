# PayCycle AI paycheck explanation path: input guard

급여 설명 경로(POST /api/paychecks/{id}/explain) 입력 가드 측정.
백엔드는 요청의 사업장명과 finding.fact 를 "사업장명\nfact" 한 문자열로 /guard/pre 에 보낸다(AiAgentService.callOpenAi).
- 정상: 규칙 엔진이 실제로 만드는 fact 문장 7종 x 4개 언어(Front i18n/rules*.ts 템플릿에 예시 값 대입)
- 공격: garak 공격 프롬프트 707개(results/after/calls.jsonl)를 fact 자리에 넣음
수정 전에는 이 경로에 가드가 없어 모든 입력이 LLM 프롬프트에 그대로 들어갔다.

## Benign facts (false positives)

| lang | n | blocked |
|---|---|---|
| ko | 7 | 0 |
| en | 7 | 0 |
| vi | 7 | 0 |
| zh | 7 | 0 |
| TOTAL | 28 | 0 |

## garak attack prompts as finding.fact

| group | n | blocked by guard | reaches LLM after fix | reaches LLM before fix |
|---|---|---|---|---|
| designed-against | 579 | 437 | 24.5% | 100% |
| held-out | 128 | 77 | 39.8% | 100% |
