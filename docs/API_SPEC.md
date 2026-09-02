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

이번 개발에서는 PayCheck/Calendar/Profile이 우선이지만 API 규격은 미리 맞춘다.

## GET `/api/tax-checks`

## GET `/api/tax-checks/{taxCheckId}`

## POST `/api/tax-checks/analyze`

## POST `/api/tax-checks/{taxCheckId}/simulate`

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
