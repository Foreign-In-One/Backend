# PayCycle AI Backend Engineering Guidelines

## 0. Scope

이 문서는 `backend/` Spring Boot 프로젝트에서 작업하는 모든 AI Agent와 개발자를 위한 작업 지침이다.

백엔드의 기본 책임은 다음과 같다.

- REST API 제공
- MySQL 데이터 관리
- PayCheck 비즈니스 로직
- OCR 연동
- 급여 자동 감지 Batch
- Rule Engine
- AI Agent 연동
- CalendarEvent 생성/갱신
- Profile 조회/수정

프론트엔드 코드는 `frontend/`에서 관리하며, 백엔드는 화면 구현에 직접 관여하지 않는다.

---

# 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

구현 전에 반드시 다음을 지킨다.

- 작업 전에 자신의 가정을 명시한다.
- 불확실한 요구사항이 있으면 질문한다.
- 여러 해석이 가능한 경우 가능한 해석을 먼저 제시하고 임의로 하나를 선택하지 않는다.
- 더 단순한 방법이 있으면 먼저 제안한다.
- 요구사항과 충돌하거나 기술적으로 문제가 있는 부분은 사용자에게 알려준다.
- API, DB, PRD 내용이 서로 충돌하면 임의로 수정하지 말고 어떤 기준을 따라야 하는지 질문한다.
- 충분히 명확하지 않으면 구현을 시작하지 않는다. 무엇이 불명확한지 명시하고 질문한다.

특히 다음 사항은 임의로 결정하지 않는다.

- DB 컬럼 변경
- FK 관계 변경
- API Request/Response 변경
- 외부 API Provider 변경
- AI Provider 변경
- Batch 실행 주기 변경
- AI가 직접 판단하도록 책임 범위 확대

---

# 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- 요청된 기능만 구현한다.
- 미래의 확장성을 이유로 불필요한 추상화를 추가하지 않는다.
- 한 번만 사용되는 코드에는 불필요한 인터페이스/추상 클래스를 만들지 않는다.
- 요청되지 않은 configuration, feature flag, generic framework를 추가하지 않는다.
- 불가능한 상황을 가정한 과도한 예외처리를 만들지 않는다.
- 200줄이 필요한 코드인지 먼저 검토하고, 50줄로 해결할 수 있다면 단순화한다.
- 선임 개발자가 보기에 과도하게 복잡한 구조라면 다시 단순화한다.

특히 MVP에서는 다음을 피한다.

- 과도한 CQRS
- 불필요한 Event Sourcing
- 과도한 Microservice 분리
- 필요하지 않은 Redis/Kafka 도입
- 필요하지 않은 Repository 추상화 계층

---

# 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

기존 코드를 수정할 때:

- 요청과 직접 관련된 코드만 수정한다.
- 주변 코드의 스타일, 주석, 구조를 임의로 개선하지 않는다.
- 기존에 작동하는 코드를 이유 없이 리팩터링하지 않는다.
- 기존 dead code는 삭제하지 않는다. 발견하면 별도로 보고한다.
- 기존 코드 스타일과 패턴을 따른다.

변경으로 인해 새로 발생한 문제만 정리한다.

- 새로 사용하지 않게 된 import 제거
- 새로 발생한 unused variable 제거
- 새로 발생한 orphan code 제거

판단 기준:

> **변경된 모든 줄은 사용자의 요청과 직접 연결되어 있어야 한다.**

---

# 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

모든 작업은 검증 가능한 목표로 바꾼다.

예:

- "Validation 추가" → 잘못된 입력 테스트를 작성하고 통과시키기
- "급여 분석 구현" → 동일한 Seed Data로 PayCheck 결과가 기대값과 일치하는지 테스트하기
- "OCR 연동" → 샘플 임금명세서를 처리하고 필요한 필드가 정확히 추출되는지 검증하기
- "Batch 구현" → 테스트용 거래가 들어왔을 때 PayCheck이 생성/갱신되는지 확인하기
- "Calendar 연동" → PayCheck/TaxCheck/ExitCheck 변경 후 해당 CalendarEvent가 생성 또는 갱신되는지 검증하기

복수 단계 작업은 항상 다음 형식으로 계획을 세운다.

1. [단계] → verify: [검증 방법]
2. [단계] → verify: [검증 방법]
3. [단계] → verify: [검증 방법]

검증에 실패하면 원인을 확인하고 수정한 뒤 다시 테스트한다.

---

# 5. 반드시 참조해야 하는 문서

백엔드 작업을 시작하기 전에 작업과 관련된 문서를 먼저 확인한다.

```text
../docs/PRD.md
../docs/API_SPEC.md
../docs/DB_SCHEMA.md
```

## 참조 규칙

### PRD.md
서비스 목적, 핵심 플로우, 사용자 시나리오, PayCheck/AI/Batch의 책임 범위를 확인할 때 참조한다.

### API_SPEC.md
REST endpoint, request/response DTO, 상태 코드, API 흐름을 확인할 때 참조한다.

### DB_SCHEMA.md
Entity, 컬럼, PK/FK, 데이터 타입, 관계를 확인할 때 참조한다.

문서 내용과 코드가 충돌할 경우 임의로 문서를 무시하지 않는다.

> **현재 코드가 실제로 잘못된 경우에는 문제를 명시하고 사용자에게 기준을 확인한 뒤 수정한다.**

문서에 없는 사항을 새로 결정해야 할 경우도 먼저 사용자에게 질문한다.

---

# 6. Backend Stack

기본 기술 스택:

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Lombok
- MySQL
- Validation

필요한 경우에만 추가한다.

예:

- Spring Security → 실제 인증이 필요해질 때
- Scheduler → Batch 구현 시 Spring 기본 `@Scheduled` 사용
- 외부 AI SDK → 필요한 Provider의 공식 SDK 또는 HTTP Client
- OCR SDK → 사용하는 OCR Provider 공식 SDK

회원가입/로그인은 현재 MVP 범위가 아니며 **Seed User**를 사용한다.

---

# 7. Source of Truth

백엔드에서 **Spring Boot + MySQL이 Source of Truth**다.

프론트엔드의 Client Context는 백엔드 데이터를 대체하지 않는다.

데이터 변경 흐름:

```text
Next.js
  ↓
Spring Boot REST API
  ↓
Service
  ↓
Repository / JPA
  ↓
MySQL
```

DB 저장 성공 후에만 프론트 상태가 성공적으로 갱신되는 것을 전제로 한다.

---

# 8. Domain Model

현재 핵심 엔티티는 7개다.

```text
User
BankTransaction
Document
Paycheck
TaxCheck
ExitCheck
CalendarEvent
```

핵심 관계:

```text
USER 1:N BANK_TRANSACTION
USER 1:N DOCUMENT
USER 1:N PAYCHECK
USER 1:N TAX_CHECK
USER 1:N EXIT_CHECK
USER 1:N CALENDAR_EVENT

PAYCHECK N:1 BANK_TRANSACTION
PAYCHECK N:1 DOCUMENT (contract)
PAYCHECK N:1 DOCUMENT (payslip)
PAYCHECK N:1 DOCUMENT (bank receipt)

TAX_CHECK N:1 DOCUMENT (tax document)
EXIT_CHECK N:1 DOCUMENT (exit document)
```

`CalendarEvent.source_type + source_id`는 논리적 참조이며 실제 FK를 만들지 않는다.

---

# 9. PayCheck Backend Architecture

PayCheck은 현재 백엔드의 최우선 기능이다.

전체 흐름:

```text
Mock/External Bank Transaction
        ↓
Salary Detection
        ↓
Paycheck Service
        ↓
Document/OCR Data
        ↓
Rule Engine
        ↓
Case Classification
        ↓
AI Agent (필요한 경우)
        ↓
Paycheck 저장
        ↓
CalendarEvent 생성/갱신
```

### Rule Engine 책임

Rule Engine은 다음을 담당한다.

- 금액 차이 계산
- 예상 급여일과 실제 입금일 비교
- 전월 대비 급여 변화
- 최근 급여 변화
- 급여 미입금 여부
- 급여 후보 분류
- 이상징후 Case 분류

Rule Engine이 담당하는 대표 상태:

```text
NORMAL
EXPLANATION_REQUIRED
INSUFFICIENT_DATA
CONFIRMATION_REQUIRED
NOT_RECEIVED
```

---

# 10. AI Agent 책임

LLM이 직접 금융/법률 판단을 최종 확정하지 않는다.

AI Agent는 이미 구조화된 데이터와 Rule Engine 결과를 바탕으로:

- 추가 정보 필요 여부를 정리
- 필요한 문서/정보를 요청
- 결과를 사용자 언어로 설명
- 다음 행동 생성
- 사업주 질문카드 생성
- 필요시 내부 Tool/API 조회

를 담당한다.

예:

```text
Rule Engine
→ difference = -120000
→ status = EXPLANATION_REQUIRED
→ payslip missing

AI Agent
→ "이번 급여의 실입금액이 12만원 감소했습니다."
→ "임금명세서를 확인해주세요."
→ "다음 행동: 임금명세서 업로드"
```

### 절대 하지 않는 것

- LLM이 임의로 숫자 계산
- LLM이 법적 위반을 확정
- 존재하지 않는 법령/출처 생성
- 존재하지 않는 거래/문서 생성
- 없는 데이터를 추론하여 채움

데이터가 부족하면 `insufficient data`로 처리하고 사용자 확인 또는 추가 자료를 요청한다.

---

# 11. OCR Architecture

OCR은 문서를 읽고 구조화된 데이터를 만드는 역할이다.

```text
파일 업로드
→ Document 생성
→ OCR Provider
→ extracted_data
→ 사용자 확인/수정
→ PayCheck 분석
```

문서 유형:

```text
EMPLOYMENT_CONTRACT
PAYSLIP
BANK_RECEIPT
TAX_DOCUMENT
INSURANCE_DOCUMENT
PENSION_DOCUMENT
OTHER
```

OCR 결과는 `Document.extracted_data`에 저장할 수 있다.

### OCR 주의사항

- OCR 추출값을 자동 확정하지 않는다.
- 사용자 검수가 필요한 값은 수정 가능해야 한다.
- OCR Provider를 Service Layer로 격리한다.
- Provider-specific response를 Domain Entity에 직접 저장하지 않는다.

---

# 12. Mock Bank API

실제 금융결제원 오픈뱅킹 연동 대신 MVP에서는 Mock API를 사용한다.

목적은 실제 금융 API가 연결된 것과 동일한 백엔드 흐름을 검증하는 것이다.

Mock 응답은 실제 금융결제원 `res_list` 구조와 최대한 유사하게 유지한다.

핵심 거래 필드:

```text
bank_name
fintech_use_num
bank_tran_id
bank_tran_date
tran_time
inout_type
tran_type
printed_content
tran_amt
after_balance_amt
branch_name
```

API 호출 메타데이터:

```text
api_tran_id
api_tran_dtm
rsp_code
rsp_message
bank_rsp_code
bank_rsp_message
next_page_yn
page_record_cnt
```

는 `BankTransaction` Entity의 거래 데이터로 저장하지 않는다.

---

# 13. Batch

급여 자동 감지를 위한 Batch는 Spring Scheduler를 우선 사용한다.

예:

```java
@Scheduled(cron = "0 0 9 * * *")
public void detectSalary() {
    // ...
}
```

구체적인 cron은 요구사항이 바뀌면 조정한다.

### Batch 원칙

1. 거래 조회
2. 중복 거래 확인
3. 급여 후보 탐지
4. 기존 Paycheck 비교
5. Case 분류
6. 필요할 때만 AI 호출
7. DB 저장
8. CalendarEvent 갱신

정상 거래에는 불필요한 LLM 호출을 하지 않는다.

---

# 14. CalendarEvent

CalendarEvent는 다른 Entity에서 발생한 중요한 이벤트를 캘린더용으로 투영한다.

예:

```text
USER.payday
→ PAYDAY event

BANK_TRANSACTION 급여 감지
→ PAYCHECK event

PAYCHECK 분석 결과
→ PAYCHECK event

Tax Schedule
→ TAX event

EXIT_CHECK
→ EXIT event
```

`CalendarEvent`는 원천 비즈니스 데이터의 대체물이 아니다.

예를 들어:

```text
PAYCHECK.payment_date
→ CalendarEvent.start_at
```

처럼 캘린더 표시를 위해 별도 Event를 생성한다.

---

# 15. External API Rules

외부 API는 실제 필요한 것만 연결한다.

## 현재 후보

### Mock Bank API
MVP 필수.

### OCR / Document AI
PayCheck 문서 분석에 사용.

### LLM API
AI Agent에 사용.

### 국가법령정보 API
공식 법령 근거 조회에 사용.

### 국세청 세무일정 API
Tax 관련 공식 일정을 CalendarEvent로 생성할 때 사용.

### 국세청 사업자등록정보 API
선택 기능. 사업장 상태 확인이 필요한 경우에만 사용.

외부 API 전체 데이터를 DB에 복제하지 않는다.

원칙:

```text
External API
→ 필요한 데이터 조회
→ Domain에 필요한 최소 데이터만 저장
```

법령 데이터는 전체 법령을 DB에 복제하지 않는다.

MVP Rule에 필요한 법령 ID/조문 정보를 사전에 검증하고, 필요할 때 공식 API에서 현재 내용을 확인한다.

---

# 16. API Layer Rules

Controller는 HTTP 요청/응답 처리만 담당한다.

```text
Controller
  ↓
Service
  ↓
Repository
```

복잡한 비즈니스 로직을 Controller에 넣지 않는다.

예:

```text
PaycheckController
PaycheckService
PaycheckRepository
```

외부 API 호출은 별도 Service로 격리한다.

예:

```text
OcrService
AiAgentService
MockBankService
LawApiService
TaxScheduleService
```

---

# 17. DTO Rules

Entity를 REST API Response로 직접 노출하지 않는다.

```text
Entity
↓
Service
↓
Response DTO
↓
Controller
```

Request와 Response DTO를 분리한다.

예:

```text
PaycheckAnalyzeRequest
PaycheckResponse
PaycheckExplainResponse
```

---

# 18. Transaction / Persistence Rules

여러 DB 작업이 하나의 비즈니스 작업으로 묶이는 경우 Service Layer에서 transaction을 관리한다.

예:

```text
Paycheck 분석
→ Paycheck 저장
→ CalendarEvent 생성
```

이것이 하나의 작업으로 성공/실패해야 한다면 transaction 경계를 Service에 둔다.

JPA Entity에는 불필요한 양방향 연관관계를 남발하지 않는다.

기본적으로 필요한 방향만 유지하고, 조회 성능과 직렬화 문제를 고려한다.

---

# 19. Validation Rules

사용자 입력과 외부 데이터는 Service 진입 전에 검증한다.

예:

- payday: 1~31
- entryDate: 미래일 불가
- expectedExitDate: 과거일 불가
- workStartDate: entryDate 이전 불가
- amount: 음수 금지 등 도메인 규칙 적용

단, 도메인상 가능한 값까지 과도하게 제한하지 않는다.

---

# 20. Error Handling

공통 Error Response 형식을 사용한다.

```json
{
  "success": false,
  "data": null,
  "message": "임금명세서를 찾을 수 없습니다.",
  "code": "DOCUMENT_NOT_FOUND"
}
```

예외를 Controller마다 중복해서 처리하지 말고 공통 예외 처리 구조를 사용한다.

단, 프로젝트에 이미 있는 예외 처리 방식이 있다면 그것을 우선 따른다.

---

# 21. Seed Data

현재 MVP는 회원가입 없이 Seed User를 사용한다.

예시 사용자:

```text
user_id: 1
name: 민수
nationality: 베트남
visa_type: E-9
company_name: 한국정밀
payday: 25
expected_exit_date: 2027-03-01
```

대표 PayCheck 시나리오:

```text
7월 실입금: 2,380,000원
8월 실입금: 2,260,000원
→ 120,000원 감소
```

이 Seed Data를 통해 다음을 검증할 수 있어야 한다.

- 급여 자동 감지
- 급여 비교
- 이상징후
- AI 다음 행동
- CalendarEvent 생성

---

# 22. 테스트 우선순위

최소한 다음 시나리오를 테스트한다.

### PayCheck

1. 정상 급여
2. 급여 감소
3. 급여 지연
4. 급여 미입금
5. 자료 부족
6. 동일 거래 중복 처리 방지

### Document

1. OCR 성공
2. OCR 실패
3. 추출값 수정
4. 지원하지 않는 문서 유형

### Calendar

1. Paycheck 생성 → CalendarEvent 생성
2. Paycheck 변경 → CalendarEvent 갱신
3. Profile 출국일 변경 → Exit 관련 일정 갱신

### AI

1. 정상 케이스에서 불필요한 호출 없음
2. 이상 케이스에서 AI 호출
3. 구조화되지 않은 정보를 AI가 임의로 만들어내지 않음
4. 데이터 부족 시 추가 자료를 요청

---

# 23. 구현 전 체크리스트

작업 전에 반드시 확인:

- [ ] `docs/PRD.md` 확인
- [ ] `docs/API_SPEC.md` 확인
- [ ] `docs/DB_SCHEMA.md` 확인
- [ ] 현재 코드 구조 확인
- [ ] 기존 구현과 요구사항 차이 확인
- [ ] 모호한 부분 질문
- [ ] 구현 범위 정의
- [ ] 성공 기준 정의

---

# 24. 구현 후 체크리스트

작업 후 반드시 확인:

- [ ] 컴파일 성공
- [ ] 테스트 통과
- [ ] API Request/Response 검증
- [ ] DB FK 정상 동작
- [ ] Seed Data 정상 삽입
- [ ] PayCheck 분석 결과 검증
- [ ] CalendarEvent 생성/갱신 검증
- [ ] AI Agent 호출 조건 검증
- [ ] Mock API 동작 검증
- [ ] 불필요한 변경이 없는지 diff 확인

문제가 발견되면 "구현 완료"로 종료하지 말고 수정 후 다시 검증한다.

---

# 25. 작업 완료 기준

작업 완료는 코드 작성이 아니라 다음 조건을 모두 충족했을 때다.

> **요구사항 구현 + 테스트 + 실제 결과 검증**

특히 PayCheck의 경우 다음 전체 흐름이 실제로 동작해야 한다.

```text
거래 조회
→ 급여 감지
→ 문서/OCR 데이터 조회
→ Rule Engine
→ Case 분류
→ AI Agent (필요 시)
→ Paycheck 저장
→ CalendarEvent 반영
→ REST API로 조회
```

---

# 26. 변경 금지 원칙

다음은 명시적 요청 없이 변경하지 않는다.

- 프론트엔드 파일
- `reference-react/`
- `docs/` 문서
- DB Schema의 기존 PK/FK
- 외부 API Provider
- 프로젝트의 기본 빌드 구조

변경이 필요하다고 판단되면 먼저 사용자에게 근거와 영향을 설명하고 확인을 받는다.
