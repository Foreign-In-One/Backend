# PayCycle AI PRD

## 1. 문서 목적

PayCycle AI는 외국인 근로자가 한국에서 일하며 발생하는 **급여·세금·출국 관련 금융권리**를 지속적으로 확인할 수 있도록 돕는 서비스다.

MVP에서는 회원가입/로그인 없이 **시드 사용자 1명**을 사용한다. 핵심 기능은 다음 3개의 흐름으로 연결된다.

```text
금융거래/문서
    ↓
PayCheck
    ├─ OCR 문서 분석
    ├─ 급여 자동 감지 Batch
    ├─ Rule Engine
    └─ AI Agent 결과/다음 행동
          ↓
     CalendarEvent
          ↓
Profile / Dashboard
```

이번 개발 범위의 핵심 담당 기능은 **PayCheck + Calendar + Profile**이다.

---

## 2. 핵심 가치

### 핵심 한 문장

> 급여가 들어오면 자동으로 확인하고, 문제가 있을 때만 사용자에게 필요한 행동을 알려주는 외국인 근로자 금융권리 Agent

### 일반 LLM과의 차별점

일반 LLM처럼 문서 하나를 읽고 답하는 데 그치지 않는다.

```text
금융거래 이벤트
→ 이전 급여/사용자 프로필 조회
→ Rule Engine으로 사실 계산
→ 필요한 경우 AI Agent가 추가 데이터/도구 조회
→ 이상징후 설명
→ 다음 행동 생성
→ 결과 저장
→ 캘린더 반영
```

LLM은 금액 계산이나 법적 결론을 직접 만들지 않는다.

---

## 3. MVP 사용자

데모 사용자 Seed Data를 사용한다.

예시:

- 이름: 민수
- 국적: 베트남
- 체류자격: E-9
- 사업장: 한국정밀
- 근무 상태: 근무 중
- 계약상 급여일: 매월 25일
- 예상 출국일: 2027-03-01
- 언어: 한국어

실제 회원가입/로그인은 MVP 범위에서 제외한다.

---

## 4. 핵심 기능

### 4.1 PayCheck

근로계약서, 임금명세서, 실제 금융거래를 이용하여 월별 급여를 검증한다.

#### 입력 데이터

- User 프로필
- BankTransaction
- Employment Contract
- Payslip
- Bank Receipt 등의 Document

#### 주요 기능

1. 문서 업로드
2. OCR 처리
3. OCR 결과 사용자 확인/수정
4. 금융거래에서 급여 후보 탐지
5. 전월/최근 급여 비교
6. 계약금액/명세서/실제 입금액 비교
7. 급여일 지연 확인
8. 새로운 공제/급여 변화 탐지
9. 이상징후 Case 분류
10. AI Agent가 설명 및 다음 행동 생성
11. 결과 DB 저장
12. CalendarEvent 생성

#### 결과 상태

- `NORMAL`
- `EXPLANATION_REQUIRED`
- `INSUFFICIENT_DATA`
- `CONFIRMATION_REQUIRED`
- `NOT_RECEIVED`

#### 법적 표현 제한

다음 표현은 사용하지 않는다.

- "임금체불입니다."
- "불법 공제입니다."
- "사업주가 법을 위반했습니다."

대신:

- "설명이 필요한 차이가 확인되었습니다."
- "현재 자료만으로 원인을 확정할 수 없습니다."
- "추가 확인이 필요합니다."

---

## 5. PayCheck 자동화

### 목표

사용자가 매월 직접 모든 자료를 비교하지 않도록 한다.

### Batch 흐름

```text
Scheduler
  ↓
Mock Bank API 거래조회
  ↓
최근 거래 중 급여 후보 탐지
  ↓
중복 여부 확인
  ↓
기존 급여 기록과 비교
  ↓
Case 분류
  ├─ NORMAL
  ├─ SALARY_DECREASE
  ├─ PAYMENT_DELAY
  ├─ NOT_RECEIVED
  ├─ LARGE_DEVIATION
  └─ UNKNOWN
  ↓
필요한 경우 AI Agent 호출
  ↓
Paycheck 저장/갱신
  ↓
CalendarEvent 생성/갱신
```

### Batch 원칙

- 매일 실행 가능하도록 설계한다.
- 급여일 기준으로 판단하되 하루에 한 번 조회해 실제 지연도 탐지한다.
- 동일한 외부 거래를 중복 처리하지 않는다.
- 정상 사례에서는 불필요한 LLM 호출을 하지 않는다.
- 이상징후나 설명 생성이 필요한 경우에만 AI 호출한다.

### MVP Mock Bank API

실제 금융결제원 오픈뱅킹 연동 대신 실제 응답 구조와 유사한 Mock API를 사용한다.

```text
GET /api/mock/bank/transactions
```

Mock API의 거래 데이터는 `BANK_TRANSACTION`에 저장하거나 이미 저장된 시드 데이터를 조회하도록 구현한다.

---

## 6. PayCheck OCR

### 지원 문서

- `EMPLOYMENT_CONTRACT`
- `PAYSLIP`
- `BANK_RECEIPT`

### 처리 흐름

```text
파일 업로드
→ Document 생성
→ OCR API 호출 또는 Mock OCR
→ extracted_data 저장
→ 사용자 확인/수정
→ PayCheck 분석에서 사용
```

### 임금명세서 추출 예시

```json
{
  "payPeriod": "2026-08",
  "baseSalary": 2200000,
  "overtimeAllowance": 180000,
  "deduction": 0,
  "netPay": 2380000
}
```

OCR이 추출한 값은 바로 확정하지 않는다. 사용자 확인 후 분석에 사용한다.

---

## 7. AI Agent

### AI 역할

AI는 단순 요약기가 아니라 **다음 행동을 결정하기 위한 Agent**로 사용한다.

### Agent Input

구조화된 사실만 전달한다.

예:

```json
{
  "case": "SALARY_DECREASE",
  "currentAmount": 2260000,
  "previousAmount": 2380000,
  "difference": -120000,
  "payslipAvailable": false,
  "companyName": "한국정밀"
}
```

### Agent가 할 수 있는 것

- 최근 급여 기록 조회
- 최신 PayCheck 조회
- 관련 Document 존재 여부 조회
- 관련 Rule/법적 근거 조회
- 추가 확인이 필요한 정보 결정
- 사용자에게 필요한 다음 행동 생성
- 다국어 설명 생성
- 사업주에게 보낼 사실확인 질문 생성

### Agent가 하지 않는 것

- 급여 차액 계산
- 세법/노동법의 최종 법률판단
- 근거 없는 사실 추론
- 사용자 데이터에 없는 값을 생성

### 예시

```text
은행에서 8월 급여 2,260,000원 감지
→ 7월 2,380,000원 대비 120,000원 감소
→ 현재 임금명세서 없음
→ Agent가 다음 행동으로 "8월 임금명세서 업로드" 제안
```

---

## 8. Calendar

Calendar는 각 기능의 결과를 날짜 기반으로 통합해서 보여주는 공통 레이어다.

### 이벤트 원천

| 이벤트 | 원천 |
|---|---|
| 계약상 급여일 | `USER.payday` |
| 실제 급여 입금 | `BANK_TRANSACTION` |
| 급여 분석 결과 | `PAYCHECK` |
| 세무 일정 | 국세청 세무일정 API 또는 Seed |
| 세금 분석 일정 | `TAX_CHECK` |
| 출국 준비 | `EXIT_CHECK` |
| 예상 출국일 | `USER.expected_exit_date` |
| 사용자 일정 | 사용자 입력 |

### CalendarEvent 저장 방식

`source_type + source_id`로 생성 원천을 기록한다.

예:

```text
source_type = PAYCHECK
source_id = 12
```

→ 12번 PayCheck으로부터 생성된 캘린더 일정.

`source_type + source_id`는 논리적 참조이며 polymorphic FK를 만들지 않는다.

### 사용자 시나리오

```text
2026.08.25
→ 급여일

2026.08.25
→ 급여 입금 감지

2026.08.25
→ 급여 확인 필요

2026.12.01
→ 연말정산 준비

2027.03.01
→ 예상 출국일
```

---

## 9. Profile

프로필은 회원가입 화면이 아니라 **현재 사용자 금융권리 프로필 관리 화면**이다.

### 표시 정보

- 이름
- 국적
- 체류자격
- 입국일
- 근로상태
- 사업장명
- 근무 시작일
- 계약상 급여일
- 예상 출국일
- 서비스 언어

### 수정 동작

프로필 값을 변경하면 관련 데이터가 갱신된다.

예:

예상 출국일 변경
→ ExitCheck 재계산 필요 상태
→ 출국 관련 CalendarEvent 갱신

근로상태 변경
→ PayCheck 노출 여부/기능 흐름 변경

---

## 10. 비근로 사용자 지원

근로를 시작하지 않은 사용자도 기본 서비스 접근이 가능해야 한다.

### `NOT_WORKING`

- PayCheck: 급여 분석 대신 "아직 급여 데이터 없음" 표시
- TaxCheck: 근무 시작 후 필요한 정보 안내
- ExitCheck: 예상 출국일이 있을 경우 준비 정보 제공
- Calendar: 입국/예정일 등의 이벤트 표시

"급여 0원"과 "아직 근무 전"을 동일하게 처리하지 않는다.

---

## 11. 사용자 화면 흐름

```text
앱 진입
  ↓
대시보드
  ├─ 이번 달 급여
  ├─ 세금 확인
  ├─ 출국 준비
  └─ 금융권리 캘린더
       ↓
급여 확인
  ↓
자료 업로드
  ↓
OCR 확인
  ↓
분석
  ↓
결과 저장
  ↓
캘린더 반영
```

---

## 12. MVP 범위 제외

- 실제 회원가입/로그인
- 실제 금융기관 계좌 인증
- 실제 오픈뱅킹 Access Token 발급
- 실제 세금 신고 제출
- 실제 보험/국민연금 신청 제출
- 실제 송금
- 실제 법률 자문

외부 API는 Mock 또는 교체 가능한 Service Layer로 구성한다.
