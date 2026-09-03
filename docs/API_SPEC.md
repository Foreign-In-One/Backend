# PayCycle AI API SPEC

## 1. 기본 정보

### Backend

- Spring Boot
- REST API
- JSON
- MySQL

### Base URL

```text
/api
```

### MVP 인증

회원가입/로그인은 구현하지 않는다.

모든 API는 Seed User를 사용한다.

```text
X-Demo-User-Id: 1
```

또는 개발 중에는 `userId=1`을 기본값으로 사용한다.

---

# 2. 공통 응답 형식

### 성공

```json
{
  "success": true,
  "data": {},
  "message": ""
}
```

### 실패

```json
{
  "success": false,
  "data": null,
  "message": "요청을 처리할 수 없습니다.",
  "code": "INVALID_REQUEST"
}
```

---

# 3. Profile API

## GET `/api/profile`

현재 데모 사용자의 프로필을 조회한다.

### Response

```json
{
  "userId": 1,
  "name": "민수",
  "phone": "01012345678",
  "nationality": "베트남",
  "visaType": "E-9",
  "entryDate": "2025-03-01",
  "employmentStatus": "WORKING",
  "companyName": "한국정밀",
  "workStartDate": "2025-03-10",
  "payday": 25,
  "expectedExitDate": "2027-03-01",
  "language": "ko"
}
```

## PATCH `/api/profile`

프로필을 수정한다.

### Request

```json
{
  "employmentStatus": "WORKING",
  "companyName": "한국정밀",
  "payday": 25,
  "expectedExitDate": "2027-04-01",
  "language": "ko"
}
```

### Side Effect

예상 출국일 변경 시 관련 `ExitCheck` 및 `CalendarEvent`의 재분석/갱신 상태를 표시한다.

---

# 4. Document API

## POST `/api/documents`

문서를 업로드한다.

### multipart

- `file`
- `documentType`

### documentType

```text
EMPLOYMENT_CONTRACT
PAYSLIP
BANK_RECEIPT
TAX_DOCUMENT
INSURANCE_DOCUMENT
PENSION_DOCUMENT
OTHER
```

### Response

```json
{
  "documentId": 3,
  "documentType": "PAYSLIP",
  "ocrStatus": "PENDING"
}
```

---

## POST `/api/documents/{documentId}/ocr`

OCR을 실행한다.

MVP에서는 실제 OCR API 또는 Mock OCR을 Service Layer에서 선택한다.

### Response

```json
{
  "documentId": 3,
  "ocrStatus": "SUCCESS",
  "extractedData": {
    "payPeriod": "2026-08",
    "baseSalary": 2200000,
    "overtimeAllowance": 180000,
    "deduction": 0,
    "netPay": 2380000
  }
}
```

---

## PATCH `/api/documents/{documentId}/extracted-data`

OCR 결과를 사용자가 확인/수정한다.

---

# 5. PayCheck API

## GET `/api/paychecks`

사용자의 급여 검증 기록을 조회한다.

### Query

```text
?from=2026-01-01&to=2026-12-31
```

---

## GET `/api/paychecks/{paycheckId}`

특정 급여 검증 결과를 조회한다.

### Response

```json
{
  "paycheckId": 2,
  "payPeriod": "2026-08",
  "contractAmount": 2300000,
  "payslipAmount": 2380000,
  "actualAmount": 2260000,
  "differenceAmount": -120000,
  "expectedPaymentDate": "2026-08-25",
  "paymentDate": "2026-08-25T09:14:00",
  "status": "EXPLANATION_REQUIRED",
  "analysisSummary": "임금명세서 실지급액과 실제 입금액에서 120,000원의 차이가 확인되었습니다.",
  "nextAction": "이번 달 임금명세서의 공제 및 별도 지급 여부를 확인하세요."
}
```

---

## POST `/api/paychecks/analyze`

문서 + 금융거래를 기반으로 PayCheck을 생성/재분석한다.

### Request

```json
{
  "payPeriod": "2026-08",
  "transactionId": 2,
  "contractDocumentId": 1,
  "payslipDocumentId": 3,
  "bankReceiptDocumentId": 4
}
```

### 처리

1. Document OCR 데이터 조회
2. BankTransaction 조회
3. 금액/날짜 비교
4. Rule Engine 실행
5. Case 분류
6. 필요한 경우 AI Agent 호출
7. Paycheck 저장
8. CalendarEvent 생성/갱신

---

## POST `/api/paychecks/{paycheckId}/explain`

기존 분석 결과를 AI Agent가 설명한다.

### Response

```json
{
  "summary": "8월 실입금액이 지난달보다 120,000원 감소했습니다.",
  "reasons": [
    "기본급 변화 여부 확인 필요",
    "임금명세서의 공제 항목 확인 필요"
  ],
  "nextActions": [
    "8월 임금명세서 업로드",
    "명세서의 공제내역 확인"
  ]
}
```

---

# 6. 자동 급여 Batch API / 내부 Job

외부에서 호출하는 API가 아니라 Spring Scheduler Job으로 실행한다.

## Job

```text
SalaryMonitoringJob
```

### 실행

```text
매일 09:00
```

### 흐름

```text
Mock Bank API
→ 최근 거래 조회
→ 신규 거래 중복 확인
→ 급여 후보 탐지
→ 기존 급여와 비교
→ Case 분류
→ 필요 시 AI Agent
→ PayCheck 저장
→ CalendarEvent 생성/갱신
```

정상 사례에서는 AI 호출을 하지 않는다.

---

# 7. Mock Bank API

## GET `/api/mock/bank/transactions`

실제 금융결제원 오픈뱅킹 API 연동을 대신하는 MVP Mock API.

### Query

```text
?userId=1&from=2026-08-20&to=2026-08-31
```

### Response

```json
{
  "apiTranId": "mock-001",
  "rspCode": "A0000",
  "resList": [
    {
      "bankTranId": "F123456789U4BC34239Z002",
      "bankTranDate": "20260825",
      "tranTime": "091400",
      "inoutType": "입금",
      "tranType": "급여",
      "printedContent": "한국정밀 8월 급여",
      "tranAmt": "2260000",
      "afterBalanceAmt": "6760000",
      "branchName": "분당점",
      "bankName": "하나은행",
      "fintechUseNum": "123456789012345678901234"
    }
  ]
}
```

Mock 응답 구조는 실제 금융결제원 거래내역조회 응답과 유사하게 유지한다.

---

# 8. AI Agent API

## POST `/api/agent/paycheck`

PayCheck Case를 해결하기 위한 Agent 실행 API.

### Request

```json
{
  "paycheckId": 2,
  "caseType": "SALARY_DECREASE"
}
```

### Agent Tool 예시

내부 서비스 함수로 구현한다.

```text
getUserProfile(userId)
getRecentPaychecks(userId)
getBankTransactions(userId)
getRelatedDocuments(paycheckId)
getApplicableRule(caseType)
```

### Response

```json
{
  "caseType": "SALARY_DECREASE",
  "summary": "이번 달 실입금액이 지난달보다 120,000원 감소했습니다.",
  "requiredEvidence": [
    "2026년 8월 임금명세서"
  ],
  "nextActions": [
    "임금명세서 업로드",
    "공제 항목 확인"
  ],
  "messageForEmployer": "이번 급여의 실지급액과 실제 입금액 사이에 차이가 있어 확인 부탁드립니다."
}
```

---

# 9. TaxCheck API

사용자 입력·확인 기반 MVP. 실제 세무 확정 판정이나 환급액 계산을 제공하지 않는다.
구현 결정과 검증 상태는 `docs/TAXCHECK_IMPLEMENTATION.md`를 참고한다.

공통 `ApiResponse`를 사용한다. 데모 사용자 선택 우선순위는 기존 Paycheck와 동일하게
`userId` query → `X-User-Id` → `X-Demo-User-Id` → `1`이다. 이는 인증이 아니므로 공개 서비스에서 실제 개인정보에 사용하면 안 된다.

## GET `/api/tax-checks`

현재 선택한 데모 사용자의 분석 이력. 선택 query: `taxYear=2026`.
`analyzedAt DESC, taxCheckId DESC` 순서의 배열을 `data`로 반환한다. 다른 사용자의 이력은 포함하지 않는다.
MVP에서는 페이지네이션 없이 반환한다.

## GET `/api/tax-checks/{taxCheckId}`

저장 당시 입력·실입금 집계·분석 결과를 반환한다. 최신 Paycheck로 과거 결과를 다시 계산하지 않는다.
없는 기록 또는 선택 사용자 소유가 아닌 기록은 `404 TAXCHECK_NOT_FOUND`.

## POST `/api/tax-checks/analyze`

분석 이력을 새로 저장한다. 같은 연도 재분석도 새 이력이다(기존 결과 덮어쓰기 없음).

### Request

```json
{
  "taxYear": 2026,
  "taxDocumentId": null,
  "income": {
    "annualIncome": 30000000,
    "nonTaxableIncome": 2000000,
    "confirmed": true
  },
  "conditions": {
    "housingSaving": true,
    "isHomeless": true,
    "housingSavingProof": false,
    "usesDeductions": null
  }
}
```

| 필드 | 의미/검증 |
|---|---|
| `taxYear` | 필수. 2000년부터 현재 한국 날짜의 연도까지. 참고 계산 규칙은 현재 2025·2026년만 제공 |
| `taxDocumentId` | 선택. 사용자 소유 `TAX_DOCUMENT` 또는 `PAYSLIP`만 연결. OCR 수치 자동 사용 안 함 |
| `income.annualIncome` | 문서로 확인할 해당 귀속연도의 총급여(비과세소득 제외). 실입금·월급·연환산 금액이 아님 |
| `income.nonTaxableIncome` | 총급여와 중복되지 않는, 단일세율 계산에 포함할 해당 연도 비과세 근로소득. 모르면 `null`, 확인된 없음만 `0` |
| `income.confirmed` | 해당 귀속연도 전체 소득과 항목 구분을 사용자가 확인했는지. 서버의 진위·세무 검증 완료 표시가 아님 |
| `conditions.*` | `true`/`false`/`null` 설문. `null`은 미응답. 이 설문만으로 공제·세율 적용 자격을 확정하지 않음 |

금액은 0 이상, 정수 13자리·소수 2자리 이내이며 두 소득 항목 합계에도 같은 범위를 적용한다.
부분 입력과 확인 미완료는 저장 가능하되 계산하지 않는다. 음수·잘못된 금액 형식은 `400 INVALID_REQUEST`.
선택 문서가 다른 사용자 소유이거나 없으면 `404 DOCUMENT_NOT_FOUND`, 종류가 다르면 `400 INVALID_REQUEST`.
입국일·현재 근로 상태는 서버 User 프로필에서 읽는다.

### Response (`data`)

```json
{
  "taxCheckId": 1,
  "sourceTaxCheckId": null,
  "simulation": false,
  "taxYear": 2026,
  "taxDocumentId": null,
  "income": { "annualIncome": 30000000, "nonTaxableIncome": 2000000, "confirmed": true },
  "conditions": { "housingSaving": true, "isHomeless": true, "housingSavingProof": false, "usesDeductions": null },
  "paySummary": {
    "totalReceivedPay": 4640000.00,
    "recordedMonths": 2,
    "amountKnownMonths": 2,
    "recordedPeriods": ["2026-07", "2026-08"],
    "missingAmountPeriods": []
  },
  "result": {
    "annualIncome": 30000000,
    "flatTaxEstimate": 6080000.00,
    "generalTaxEstimate": null,
    "taxDifference": null,
    "residentStatus": "REVIEW_REQUIRED",
    "elapsedDaysReference": 244,
    "status": "REVIEW_REQUIRED",
    "cards": [],
    "requiredDocuments": ["해당 귀속연도 근로소득 원천징수영수증 또는 전체 급여명세서", "비과세 항목 내역", "소득·세액공제 증빙"],
    "analysisSummary": "19% 적용을 가정한 참고액만 계산했습니다. 실제 적용 여부와 유불리는 추가 확인이 필요합니다.",
    "nextAction": "일반세율 비교에 필요한 공제 자료와 단일세율 적용 자격을 확인하세요.",
    "calculation": {
      "mode": "FLAT_19_ASSUMPTION",
      "ruleVersion": "taxcheck-manual-reference-v1",
      "rate": 0.19,
      "incomeBase": 32000000,
      "eligibilityConfirmed": false,
      "missingFields": [],
      "warnings": []
    }
  },
  "analyzedAt": "2026-09-01T15:00:00"
}
```

위 응답은 가상 입력·급여 기록을 사용한 구조 예시이며 `cards`와 `warnings` 내용은 길이 때문에 생략했다.
실제 `cards`는 `resident`, `housing`, `flat` 3개이며 각 카드의 필드는
`id, title, status, tone, summary, confirmed[], missing[], nextActions[], evidence[{title,url}]`이다.
카드 status는 이 버전에서 `UNKNOWN` 또는 `REVIEW_REQUIRED`; 기존 프론트의 한국어 상태와 직접 동일하지 않다.
화면 연동 시 명시적으로 변환해야 한다.

### 금액/누락 규칙

- `paySummary.totalReceivedPay`: 해당 사용자의 `pay_period`가 귀속연도에 속하는 Paycheck의 `actual_amount`만 합산.
- 모든 입금액이 미확인이거나 기록이 없으면 `totalReceivedPay=null`. 확인된 0원은 `0`으로 보존.
- `recordedMonths`는 해당 연도 등록 기록 수, `amountKnownMonths`는 입금액이 알려진 기록 수.
- `missingAmountPeriods`는 **등록된 기록 중 금액이 없는 월**이며 미등록 월을 의미하지 않는다.
- 등록 개월 수가 12 미만이라는 사실만으로 사용자가 12개월 모두 근무했다고 가정하지 않는다.
- `income` 두 금액이 있고 `confirmed=true`이며 지원 연도이면 `(annualIncome + nonTaxableIncome) × 0.19`만 계산.
- 입금액을 세전 소득으로 대체하거나 월평균을 12개월로 환산하지 않는다.
- `flatTaxEstimate`는 19% 적용 **가정**의 소득세 참고값이다. 원 단위 신고·징수 절사 규칙이 아니라 소수 2자리 HALF_UP 표시용 계산이다.
- 일반세율 예상액·차액은 항상 `null`. 지방소득세·기납부세액·환급액 계산은 없다.
- 금액 계산 성공이어도 `eligibilityConfirmed=false`, 전체 상태는 `REVIEW_REQUIRED`.
- 입국일 참고 일수가 183일 이상이어도 거주자로 확정하지 않는다. 입국일 다음날부터 해당 연도/분석일까지 경과일만 센다. 실제 출입국/거소일수가 아니다.

`calculation.missingFields` 코드:

| 코드 | 의미 |
|---|---|
| `ANNUAL_INCOME_REQUIRED` | 총급여 입력 필요 |
| `NON_TAXABLE_INCOME_REQUIRED` | 단일세율 계산에 포함할 비과세 항목 확인 필요 |
| `INCOME_CONFIRMATION_REQUIRED` | 사용자 소득 확인 필요 |
| `TAX_YEAR_RULE_NOT_VERIFIED` | 해당 연도 계산 규칙을 이 버전에서 지원하지 않음 |

코드가 있으면 `flatTaxEstimate`와 `calculation.incomeBase`는 `null`이다.
일반세율이 `null`인 이유와 적용 자격 미확인 사항은 `calculation.warnings`와 카드에 별도로 제공한다.

## POST `/api/tax-checks/{taxCheckId}/simulate`

원본 분석 스냅샷에 조건을 대입한다. TaxCheck·User·Paycheck·Document·CalendarEvent 모두 저장/수정하지 않는다.

```json
{
  "income": { "annualIncome": 40000000, "nonTaxableIncome": 2000000, "confirmed": true },
  "conditions": { "housingSaving": true, "isHomeless": true, "housingSavingProof": true, "usesDeductions": false }
}
```

- `income`/`conditions` 그룹이 생략 또는 `null`이면 원본 그룹을 유지한다. `{}`는 원본과 같은 결과의 시뮬레이션이다.
- 전달한 그룹은 **그룹 전체를 교체**한다. 내부 생략 필드는 원본을 상속하지 않고 미확인 `null`이다.
- `income: {"confirmed":false}` 또는 `income:{}`로 소득 확인을 해제할 수 있다.
- 귀속연도·문서·입국일·근로 상태·실입금 집계·기준일은 원본 스냅샷을 유지한다.
- `simulation=true`, `taxCheckId=null`, `sourceTaxCheckId=원본ID`를 반환한다.
- 응답 `analyzedAt`은 원본 분석 시각이다. 새 저장 기록이나 수정 시각을 만들지 않는다.
- 금액 변경을 가정할 때 `confirmed=true`는 시나리오 입력의 확인이지 실제 소득이 바뀌었다는 뜻이 아니다.
- 지원하지 않는 저장 스냅샷/계산 버전이면 `409 TAXCHECK_SNAPSHOT_INVALID`로 재분석을 요청한다.

---

# 10. ExitCheck API

## GET `/api/exit-checks`

## GET `/api/exit-checks/{exitCheckId}`

## POST `/api/exit-checks/analyze`

---

# 11. Calendar API

## GET `/api/calendar/events`

### Query

```text
?from=2026-08-01&to=2026-08-31
```

### Response

```json
[
  {
    "eventId": 1,
    "eventType": "PAYDAY",
    "title": "8월 급여일",
    "description": "계약상 급여일",
    "startAt": "2026-08-25T09:00:00",
    "endAt": "2026-08-25T23:59:59",
    "sourceType": "PAYCHECK",
    "sourceId": 2,
    "status": "COMPLETED"
  }
]
```

---

## POST `/api/calendar/events`

사용자 직접 일정 생성.

### Request

```json
{
  "eventType": "PERSONAL",
  "title": "은행 방문",
  "description": "통장 관련 업무",
  "startAt": "2026-09-01T14:00:00",
  "endAt": "2026-09-01T15:00:00"
}
```

---

## PATCH `/api/calendar/events/{eventId}`

사용자 일정 수정.

---

## DELETE `/api/calendar/events/{eventId}`

사용자 직접 생성 일정 삭제.

시스템 생성 일정은 삭제보다는 원천 결과 상태 변화에 따라 갱신한다.

---

# 12. 외부 API / 설정

## 현재 MVP

- Bank API: Mock
- OCR: Mock 또는 실제 OCR API 교체 가능
- LLM: 실제 API 연동 예정
- 법령 API: 필요 시 Rule 기반 조회
- 국세청 세무일정: 연 1회 Seed/캐시 가능

모든 외부 의존성은 Service Layer를 통해 분리한다.

```text
controller
  ↓
service
  ↓
external client / adapter
```

실제 API를 Mock으로 변경할 때 Controller/Business Logic을 수정하지 않는다.

---

# 13. Dashboard / Records 조회 API

- `GET /api/dashboard?year=2026`: 선택 연도의 실입금 집계와 전체 이력의 최신 도메인별 결과·최근 3건.
- `GET /api/records`: 저장된 PayCheck·TaxCheck·ExitCheck 통합 목록.
- `GET /api/records?type=TAX_CHECK`: 종류 필터. `PAYCHECK`, `TAX_CHECK`, `EXIT_CHECK`를 지원하며 생략하면 전체.

공통 `ApiResponse`를 사용하며 DB 저장·수정·삭제·분석·시뮬레이션을 수행하지 않는다.
전체 응답 계약, 누락/0원 규칙, 정렬, 개발·배포 전제는
[`DASHBOARD_RECORDS_IMPLEMENTATION.md`](DASHBOARD_RECORDS_IMPLEMENTATION.md)를 따른다.

개발용 사용자 선택은 기존 API와 동일하며 **인증이 아니다**. 공개 URL에서 실제 개인정보를 제공하면 안 된다.
실제 실행에는 `paychecks`, `tax_checks`, `exit_checks` 테이블이 모두 필요하다.
테스트 전용 SQL은 누락된 도메인의 읽기 컬럼을 격리된 H2 DB에 제공할 뿐, 운영 마이그레이션이 아니다.
