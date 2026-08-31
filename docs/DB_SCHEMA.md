# PayCycle AI DB Schema

## 1. DB 기준

- DB: MySQL 8+
- PK: `BIGINT AUTO_INCREMENT`
- 시간: `DATETIME`
- 금액: `DECIMAL(15,2)`
- 구조화된 분석 결과/목록: MySQL `JSON`
- 비밀번호: 해시 문자열 저장

MVP에서는 회원가입/로그인을 구현하지 않고 Seed User를 사용한다.

---

# 2. ERD 개요

```text
USER
 ├── 1:N BANK_TRANSACTION
 ├── 1:N DOCUMENT
 ├── 1:N PAYCHECK
 ├── 1:N TAX_CHECK
 ├── 1:N EXIT_CHECK
 └── 1:N CALENDAR_EVENT

BANK_TRANSACTION
 └── 1:N PAYCHECK

DOCUMENT
 ├── 1:N PAYCHECK (contract_document_id)
 ├── 1:N PAYCHECK (payslip_document_id)
 ├── 1:N PAYCHECK (bank_receipt_document_id)
 ├── 1:N TAX_CHECK (tax_document_id)
 └── 1:N EXIT_CHECK (exit_document_id)
```

---

# 3. USER

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `user_id` | BIGINT | PK | 사용자 ID |
| `name` | VARCHAR(50) | | 이름 |
| `phone` | VARCHAR(20) | UNIQUE | 전화번호 |
| `password` | VARCHAR(255) | | 암호화 비밀번호 |
| `nationality` | VARCHAR(30) | | 국적 |
| `visa_type` | VARCHAR(20) | | 체류자격 |
| `entry_date` | DATE | | 입국일 |
| `employment_status` | VARCHAR(30) | | 근로 상태 |
| `company_name` | VARCHAR(100) | | 사업장명 |
| `work_start_date` | DATE | | 현재 근무 시작일 |
| `payday` | TINYINT | | 계약상 급여일 1~31 |
| `expected_exit_date` | DATE | | 예상 출국일 |
| `language` | VARCHAR(10) | | ko/en/vi/zh |
| `created_at` | DATETIME | | 생성일 |
| `updated_at` | DATETIME | | 수정일 |

---

# 4. BANK_TRANSACTION

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `transaction_id` | BIGINT | PK | 내부 거래 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `bank_name` | VARCHAR(50) | | 은행명 |
| `fintech_use_num` | VARCHAR(30) | | 핀테크이용번호 |
| `bank_tran_id` | VARCHAR(50) | UNIQUE | 금융기관 거래 ID |
| `bank_tran_date` | DATE | | 거래일 |
| `tran_time` | TIME | | 거래시간 |
| `inout_type` | VARCHAR(10) | | 입금/출금 |
| `tran_type` | VARCHAR(30) | | 거래유형 |
| `printed_content` | VARCHAR(255) | | 통장 인자내용 |
| `tran_amt` | DECIMAL(15,2) | | 거래 금액 |
| `after_balance_amt` | DECIMAL(15,2) | | 거래 후 잔액 |
| `branch_name` | VARCHAR(100) | | 거래 지점 |
| `transaction_category` | VARCHAR(30) | | 내부 분류 |
| `created_at` | DATETIME | | 저장일 |

### 내부 분류

```text
SALARY
SALARY_CANDIDATE
GENERAL_DEPOSIT
WITHDRAWAL
UNKNOWN
```

### Index

```text
UNIQUE(bank_tran_id)
INDEX(user_id, bank_tran_date)
INDEX(user_id, inout_type, bank_tran_date)
```

API 상위 메타데이터(`api_tran_id`, `rsp_code`, `next_page_yn` 등)는 이 테이블에 저장하지 않는다.

---

# 5. DOCUMENT

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `document_id` | BIGINT | PK | 문서 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `document_type` | VARCHAR(30) | | 문서 종류 |
| `original_filename` | VARCHAR(255) | | 원본 파일명 |
| `file_path` | VARCHAR(500) | | 파일 저장 위치 |
| `mime_type` | VARCHAR(100) | | MIME 타입 |
| `file_size` | BIGINT | | 파일 크기 |
| `ocr_status` | VARCHAR(20) | | OCR 상태 |
| `extracted_data` | JSON | | OCR 구조화 결과 |
| `uploaded_at` | DATETIME | | 업로드일 |
| `updated_at` | DATETIME | | 수정일 |

### Document Type

```text
EMPLOYMENT_CONTRACT
PAYSLIP
BANK_RECEIPT
TAX_DOCUMENT
INSURANCE_DOCUMENT
PENSION_DOCUMENT
OTHER
```

---

# 6. PAYCHECK

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `paycheck_id` | BIGINT | PK | 급여 검증 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `transaction_id` | BIGINT | FK | BANK_TRANSACTION.transaction_id |
| `contract_document_id` | BIGINT | FK | 계약서 DOCUMENT ID |
| `payslip_document_id` | BIGINT | FK | 임금명세서 DOCUMENT ID |
| `bank_receipt_document_id` | BIGINT | FK | 입금증빙 DOCUMENT ID |
| `pay_period` | VARCHAR(7) | | YYYY-MM |
| `contract_amount` | DECIMAL(15,2) | | 계약상 급여 |
| `payslip_amount` | DECIMAL(15,2) | | 명세서 실지급액 |
| `actual_amount` | DECIMAL(15,2) | | 실제 입금액 |
| `difference_amount` | DECIMAL(15,2) | | 차액 |
| `expected_payment_date` | DATE | | 예상 급여일 |
| `payment_date` | DATETIME | | 실제 입금일 |
| `status` | VARCHAR(30) | | 상태 |
| `analysis_summary` | TEXT | | 분석 요약 |
| `next_action` | TEXT | | 다음 행동 |
| `analyzed_at` | DATETIME | | 분석일 |
| `created_at` | DATETIME | | 생성일 |
| `updated_at` | DATETIME | | 수정일 |

### 상태

```text
NORMAL
EXPLANATION_REQUIRED
INSUFFICIENT_DATA
CONFIRMATION_REQUIRED
NOT_RECEIVED
```

### UNIQUE

MVP에서 사용자별 월 1개 결과를 유지한다면:

```text
UNIQUE(user_id, pay_period)
```

재분석 이력을 별도 저장하는 구조로 변경하면 이 UNIQUE를 제거하고 `analysis_version`을 추가한다.

---

# 7. TAX_CHECK

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `tax_check_id` | BIGINT | PK | 세금 분석 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `tax_document_id` | BIGINT | FK | 세금 문서 |
| `tax_year` | INT | | 귀속연도 |
| `resident_status` | VARCHAR(20) | | 거주자 상태 |
| `annual_income` | DECIMAL(15,2) | | 연간 총급여 |
| `flat_tax_estimate` | DECIMAL(15,2) | | 19% 예상 세액 |
| `general_tax_estimate` | DECIMAL(15,2) | | 일반세율 예상 세액 |
| `tax_difference` | DECIMAL(15,2) | | 예상 차이 |
| `benefit_summary` | JSON | | 적용 가능 혜택 |
| `required_documents` | JSON | | 필요 증빙 |
| `status` | VARCHAR(30) | | 상태 |
| `next_action` | TEXT | | 다음 행동 |
| `analysis_summary` | TEXT | | 분석 요약 |
| `analyzed_at` | DATETIME | | 분석일 |
| `created_at` | DATETIME | | 생성일 |
| `updated_at` | DATETIME | | 수정일 |

### 상태

```text
POSSIBLE
NOT_APPLICABLE
REVIEW_REQUIRED
UNKNOWN
```

---

# 8. EXIT_CHECK

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `exit_check_id` | BIGINT | PK | 출국 분석 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `exit_document_id` | BIGINT | FK | 출국 관련 문서 |
| `expected_exit_date` | DATE | | 예상 출국일 |
| `work_duration_months` | INT | | 근무기간 |
| `insurance_status` | VARCHAR(30) | | 보험 상태 |
| `pension_status` | VARCHAR(30) | | 연금 상태 |
| `retirement_status` | VARCHAR(30) | | 퇴직 상태 |
| `missing_documents` | JSON | | 부족 서류 |
| `checklist` | JSON | | 준비 목록 |
| `readiness_score` | INT | | 준비도 0~100 |
| `status` | VARCHAR(30) | | 전체 상태 |
| `next_action` | TEXT | | 다음 행동 |
| `analysis_summary` | TEXT | | 분석 요약 |
| `analyzed_at` | DATETIME | | 분석일 |
| `created_at` | DATETIME | | 생성일 |
| `updated_at` | DATETIME | | 수정일 |

### 상태

```text
CHECK_REQUIRED
IN_PROGRESS
MISSING_DOCUMENT
READY
UNKNOWN
```

`readiness_score`는 법률적 수급 가능성 점수가 아니라 준비 항목의 완료 수준이다.

---

# 9. CALENDAR_EVENT

| 컬럼 | 타입 | Key | 설명 |
|---|---|---|---|
| `event_id` | BIGINT | PK | 일정 ID |
| `user_id` | BIGINT | FK | USER.user_id |
| `event_type` | VARCHAR(30) | | 일정 유형 |
| `title` | VARCHAR(100) | | 일정 제목 |
| `description` | TEXT | | 설명 |
| `start_at` | DATETIME | | 시작일시 |
| `end_at` | DATETIME | | 종료일시 |
| `source_type` | VARCHAR(30) | | 생성 출처 |
| `source_id` | BIGINT | | 원본 ID |
| `status` | VARCHAR(20) | | 상태 |
| `created_at` | DATETIME | | 생성일 |
| `updated_at` | DATETIME | | 수정일 |

### Event Type

```text
PAYDAY
PAYCHECK
TAX
EXIT
PERSONAL
```

### Source Type

```text
PAYCHECK
TAX_CHECK
EXIT_CHECK
SYSTEM
USER
```

`source_type + source_id`는 논리적 참조이며 실제 FK로 사용하지 않는다.

---

# 10. FK 전체 목록

```text
BANK_TRANSACTION.user_id
→ USER.user_id

DOCUMENT.user_id
→ USER.user_id

PAYCHECK.user_id
→ USER.user_id

PAYCHECK.transaction_id
→ BANK_TRANSACTION.transaction_id

PAYCHECK.contract_document_id
→ DOCUMENT.document_id

PAYCHECK.payslip_document_id
→ DOCUMENT.document_id

PAYCHECK.bank_receipt_document_id
→ DOCUMENT.document_id

TAX_CHECK.user_id
→ USER.user_id

TAX_CHECK.tax_document_id
→ DOCUMENT.document_id

EXIT_CHECK.user_id
→ USER.user_id

EXIT_CHECK.exit_document_id
→ DOCUMENT.document_id

CALENDAR_EVENT.user_id
→ USER.user_id
```

---

# 11. 외부 데이터 저장 원칙

## 금융결제원 오픈뱅킹

```text
외부 API
→ 거래 조회
→ 필요한 거래만 BANK_TRANSACTION 저장
```

API 응답의 모든 호출 메타데이터를 저장하지 않는다.

## OCR

```text
Document 파일
→ OCR
→ extracted_data JSON 저장
```

## 국가법령정보

법령 원문 전체를 DB에 복제하지 않는다.

Rule에 공식 법령 식별정보를 관리하고 필요 시 API를 조회한다.

## 국세청 세무일정

연 단위로 가져와 필요한 일정만 `CALENDAR_EVENT`로 저장할 수 있다.

---

# 12. Seed 데이터 시나리오

## User #1: 정상 + 이상 급여 사례

```text
국적: 베트남
체류자격: E-9
사업장: 한국정밀
급여일: 매월 25일
```

### 7월

```text
계약상 급여: 2,300,000
명세서:      2,380,000
입금:        2,380,000
결과:        NORMAL
```

### 8월

```text
계약상 급여: 2,300,000
명세서:      2,380,000
입금:        2,260,000
차이:        -120,000
결과:        EXPLANATION_REQUIRED
```

## User #2: 취업 전

급여 데이터가 없는 상태.

```text
employment_status = NOT_WORKING
```

## User #3: 자료 부족 사례

급여 입금은 존재하지만 임금명세서가 없는 상태.

```text
status = INSUFFICIENT_DATA
```

이 3가지 Seed 상태는 각각 다른 UI/자동화 흐름을 테스트하기 위해 유지한다.
