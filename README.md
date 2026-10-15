# Backend
Foreign-In-One 백엔드

## AI 가드레일 및 레드티밍 (`feature/ai-guardrail-baseline`)

### 개요
- AI 상담 챗봇(`/api/agent/chat`) 대상 방어 계층 구축: 프롬프트 인젝션, 개인정보 노출, 시스템 프롬프트 유출
- Python 보안 서버(`security-server/`) 분리: NeMo Guardrails 입출력 레일, Presidio 한국형 개인정보 비식별화
- garak 레드티밍 기반 적용 전후 정량 비교, 4개 언어(ko·en·vi·zh) 정상 질문 오탐률 측정

### 요청 처리 흐름
```
Front → Backend /api/agent/chat
  1. 호출 빈도 제한: 사용자별 60초당 10회
  2. 보안 서버 /guard/pre
     ├ 유니코드 정규화: NFKC, 비가시·태그 문자 제거, Zalgo 결합 부호 축약
     ├ 인코딩 페이로드 레일: hex·base64 등 영문·숫자 혼합 장문 토큰 차단
     ├ 프롬프트 인젝션 분류 레일: 다국어 분류기 판정
     └ 개인정보 비식별화: 주민·외국인등록번호, 전화번호, 계좌번호, 이메일, 본인·사업장명
  3. LLM 호출: 요청별 카나리아 토큰 삽입, 데이터·질문 영역 구분자 분리
  4. 보안 서버 /guard/post: 카나리아 토큰 유출 검사, 유출 시 안전 안내문 대체
  5. 보안 이벤트 감사 로그 기록 (위험 유형별 JSON)
```
- 보안 서버 장애 시 LLM 미호출 (fail-closed)

### 구성 요소
| 위치 | 역할 |
|---|---|
| `domain/agent/security/AiAgentGuardrailService` | 호출 빈도 제한, 입력 가드 호출, 카나리아 토큰 생성·출력 검증 |
| `domain/agent/security/SecurityServerClient` | 보안 서버 REST 클라이언트 (연결 3초, 응답 10초 제한) |
| `domain/agent/security/AiRiskType` | 위험 유형 분류 (NIST AI RMF, OWASP Top 10 기준) |
| `domain/agent/security/AiAgentAuditLogger` | 보안 이벤트 JSON 감사 로그 |
| `domain/agent/service/AiAgentService` | 입력 가드, 카나리아 삽입, 출력 검증 연결 |
| `application-redteam.yml` | 레드티밍 전용 프로필 (호출 빈도 제한 해제) |
| `security-server/app/` | FastAPI 보안 서버 (`/guard/pre`, `/guard/post`) |
| `security-server/app/guardrails/` | NeMo Guardrails 레일 정의 (Colang 1.0) |
| `security-server/tests/` | 보안 서버 단위 테스트 |
| `security-server/redteam/` | garak 레드티밍 실행·분석 스크립트, 정상 질문 세트, 결과 요약 |

### 설계 결정
- 인젝션 분류기 선정: 영어 전용 분류기의 정상 질문 오탐 확인(선정 세트 10/24, 한국어 4/8·베트남어 4/5) → 다국어 분류기 `patronus-studio/wolf-defender-prompt-injection-small` 채택 (`redteam/compare_classifiers.py`)
- 모델 선정용 세트와 오탐률 보고용 세트 분리: 선정 세트 24건, 보고용 세트 60건
- 한국어 개인정보 정규식: 단어 경계(`\b`) 대신 숫자 경계 사용, 조사 결합(`010-1234-5678로`) 시 미탐 방지
- 영어 개체명 인식(PERSON) 미사용: 한국어 문장 오인식 방지, 실명은 알려진 사용자명·사업장명 기준 치환
- 인코딩 레일: 특정 인코딩 대신 "영문·숫자 혼합, 공백 없는 장문 토큰" 일반 형태 판정, 계좌·전화번호·이메일·URL·여권번호 제외
- 레드티밍 시 호출 빈도 제한 해제: 공격 내용과 무관한 차단의 측정 오염 방지

### 레드티밍 결과
garak 0.16.0, 적용 전 → 적용 후, 동일 프롬프트 대응 비교

| 구분 | 프롬프트 수 | LLM 도달률 | 공격 성공률 |
|---|---|---|---|
| 설계 대상 프로브 합계 | 579 | 82.4% → 23.8% (p = 1.8e-102) | 1.2% → 0.3% (p = 0.12) |
| 미사용 인코딩 (held-out) | 128 | 100% → 43.8% (p = 4.2e-22) | 0% → 0% |
| 정상 질문 오탐 (언어별 15건) | 60 | - | 0% → 0% |

- 프로브: promptinject, dan.DanInTheWild, encoding 계열, sysprompt_extraction
- LLM 도달률: 입력 가드를 모두 통과해 실제 LLM에 전달된 비율
- 공격 성공률: garak 탐지기 적중 중 거절 응답 제외, 시스템 프롬프트 추출은 자체 유출 탐지기 기준
- p: 동일 프롬프트 전후 McNemar 정확 검정
- 공격 성공률 감소는 통계적으로 유의하지 않음 (적용 전 성공률 자체가 낮음)
- 상세 결과: `security-server/redteam/results/summary.md`

### 실행
보안 서버
```bash
cd security-server
python -m venv .venv-guard
.venv-guard/Scripts/pip install -r requirements.txt
.venv-guard/Scripts/python -m spacy download en_core_web_sm
.venv-guard/Scripts/python -m uvicorn app.main:app --port 8001
```
- Backend 연결 주소: `SECURITY_SERVER_BASE_URL` (기본값 `http://localhost:8001`)
- 첫 실행 시 인젝션 분류기 모델 자동 다운로드
- 단위 테스트: `.venv-guard/Scripts/python -m pytest tests`

레드티밍
```bash
cd security-server
python -m venv .venv
.venv/Scripts/pip install -r redteam/requirements.txt
# Backend를 SPRING_PROFILES_ACTIVE=local,redteam 으로 실행한 상태에서
.venv/Scripts/python redteam/run_redteam.py baseline   # 적용 후 측정은 after
.venv/Scripts/python redteam/analyze.py
```
- garak과 보안 서버의 의존성 충돌 방지를 위한 가상환경 분리
- 가드를 통과한 프롬프트의 실제 OpenAI API 호출 (비용 발생)

### 관련 오픈소스 기여
레드티밍과 가드레일 구현 과정에서 발견한 문제의 원 프로젝트 보고·수정
- Presidio: 한국어 컨텍스트 매칭 옵션 [PR #2341](https://github.com/data-privacy-stack/presidio/pull/2341), 한국 계좌번호 인식기 보완 커밋 4건 ([PR #2215](https://github.com/data-privacy-stack/presidio/pull/2215))
- garak: promptinject 프로브 중복 프롬프트 수정 [이슈 #2304](https://github.com/NVIDIA/garak/issues/2304), [PR #2305](https://github.com/NVIDIA/garak/pull/2305)
- NeMo Guardrails: Presidio 언어·모델 설정 [이슈 #2415](https://github.com/NVIDIA-NeMo/Guardrails/issues/2415), 비 UTF-8 환경 설정 파일 로딩 오류 [이슈 #2429](https://github.com/NVIDIA-NeMo/Guardrails/issues/2429)
- [presidio-ko](https://github.com/Bin0917/presidio-ko): 한국 전화번호·계좌번호 인식기, 한국어 컨텍스트 강화기 패키지
