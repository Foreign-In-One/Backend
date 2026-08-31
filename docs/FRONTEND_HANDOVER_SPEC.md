# PayCycle AI - 프론트엔드 연동 명세서 및 인수인계 가이드 (Frontend Handover Spec)

본 문서는 프론트엔드(Next.js / React) 개발팀이 백엔드 REST API를 안정적이고 신속하게 연동할 수 있도록 작성된 **최종 API 명세서 및 주의사항 가이드**입니다.

---

## 1. 기본 환경 및 공통 규격

### 🌐 Base URL & 인증
* **Local Base URL**: `http://localhost:8080`
* **인증 방식 (MVP Demo)**:
  * 모든 API 요청 시 다음 Request Header를 전달하거나 생략할 수 있습니다.
  * Header: `X-Demo-User-Id: 1` (기본값: `1`번 사용자 민수)
  * 프론트엔드 환경변수 설정 (`.env.local`):
    ```env
    NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
    ```
* **CORS**: `http://localhost:3000`의 모든 요청(GET, POST, PATCH, PUT, DELETE, OPTIONS)이 허용되어 있습니다.

### 📦 공통 응답 봉투 (Envelope)
모든 REST API 응답은 아래 표준 JSON 포맷으로 래핑되어 반환됩니다:
```json
{
  "success": true,
  "data": { ... },
  "message": "성공 메시지",
  "code": null
}
```
* 에러 발생 시: `success: false`, `data: null`, `message: "상세 에러 내용"`, `code: "ERROR_CODE"`

---

## 2. Google Cloud Document AI 및 OCR 데이터 추출 스펙

### 📄 1) 문서 업로드 API
* **Endpoint**: `POST /api/documents`
* **Content-Type**: `multipart/form-data`
* **지원 파일 포맷**: PDF (`application/pdf`) 및 이미지 전 포맷 (`image/png`, `image/jpeg`, `image/jpg`, `image/webp`, `image/tiff`)
* **Request Parameters**:
  * `file`: MultipartFile (PDF 또는 이미지 파일)
  * `documentType`: Enum (`EMPLOYMENT_CONTRACT`, `PAYSLIP`, `BANK_RECEIPT`, `TAX_DOCUMENT`, `INSURANCE_DOCUMENT`, `PENSION_DOCUMENT`, `OTHER`)
* **Response**:
```json
{
  "success": true,
  "data": {
    "documentId": 3,
    "fileName": "2026_08_payslip.pdf",
    "documentType": "PAYSLIP",
    "ocrStatus": "PENDING",
    "createdAt": "2026-08-26T12:00:00"
  }
}
```

---

### 🔍 2) OCR 실행 및 필드 추출 API
* **Endpoint**: `POST /api/documents/{documentId}/ocr`
* **동작 원리**: 백엔드에 GCP Document AI 키가 설정되어 있으면 실제 GCP Document AI를 호출하고, 키가 없거나 호출 실패 시 자체 Mock 추출 엔진으로 자동 Fallback되어 항상 안전하게 응답합니다.

#### 📌 문서 유형별 필수 추출 키 (Extracted Data Keys)
프론트엔드에서 사용자 검수 화면이나 PayCheck 대조 시 사용하는 `extractedData` 필드 목록입니다:

| 문서 유형 (`documentType`) | 추출 키 (`Key`) | 데이터 타입 | 설명 및 예시 |
|---|---|---|---|
| **`PAYSLIP`**<br>(임금명세서) | `payPeriod` | String | 급여 귀속월 (`"2026-08"`) |
| | `baseSalary` | Number | 기본급 (`2200000`) |
| | `totalPayment` | Number | 지급총액 / 지급합계 (`2380000`) |
| | `overtimeAllowance`| Number | 연장/야간/휴일 수당 (`180000`) |
| | `deduction` | Number | 공제총액 / 세금 / 4대보험 (`0`) |
| | `netPay` | Number | **실지급액 (차인지급액)** (`2380000`) |
| | `companyName` | String | 회사명 / 사업장명 (`"한국정밀"`) |
| | `paymentDate` | String | 명세서상 지급일 (`"2026-08-25"`) |
| **`EMPLOYMENT_CONTRACT`**<br>(근로계약서) | `companyName` | String | 회사명 (`"한국정밀"`) |
| | `baseSalary` | Number | 계약상 기본급 (`2300000`) |
| | `payday` | Integer | 매월 정기 급여일 (`25`) |
| | `workStartDate` | String | 근로 개시일 (`"2025-03-10"`) |
| | `contractDurationMonths`| Integer | 계약 기간 개월수 (`36`) |
| **`BANK_RECEIPT`**<br>(입금확인증) | `bankName` | String | 은행명 (`"하나은행"`) |
| | `depositAmount` | Number | 실입금액 (`2260000`) |
| | `afterBalanceAmt` | Number | 거래후 잔액 (`6760000`) |
| | `depositDate` | String | 입금일 (`"2026-08-25"`) |
| | `sender` | String | 보낸사람 / 적요 (`"한국정밀 급여"`) |

* **Response 예시**:
```json
{
  "success": true,
  "data": {
    "documentId": 3,
    "ocrStatus": "SUCCESS",
    "extractedData": {
      "payPeriod": "2026-08",
      "baseSalary": 2200000,
      "overtimeAllowance": 180000,
      "deduction": 0,
      "netPay": 2380000,
      "companyName": "한국정밀",
      "paymentDate": "2026-08-25"
    }
  }
}
```

---

### ✏️ 3) OCR 추출 데이터 사용자 검수 및 수정 API
사용자가 OCR로 인식된 숫자를 화면에서 직접 수정할 때 호출합니다.
* **Endpoint**: `PATCH /api/documents/{documentId}/extracted-data`
* **Request Body**:
```json
{
  "extractedData": {
    "payPeriod": "2026-08",
    "baseSalary": 2250000,
    "overtimeAllowance": 150000,
    "deduction": 20000,
    "netPay": 2380000
  }
}
```

---

## 3. PayCheck 핵심 도메인 API

### 📊 1) 급여 검증 목록 조회 (Dashboard 메인)
* **Endpoint**: `GET /api/paychecks`
* **Query Params (선택)**: `?from=2026-01-01&to=2026-12-31`
* **Response 예시**:
```json
{
  "success": true,
  "data": [
    {
      "paycheckId": 1,
      "payPeriod": "2026-07",
      "contractAmount": 2300000.00,
      "payslipAmount": 2380000.00,
      "actualAmount": 2380000.00,
      "differenceAmount": 0.00,
      "expectedPaymentDate": "2026-07-25",
      "paymentDate": "2026-07-25T09:10:00",
      "status": "NORMAL",
      "analysisSummary": "임금명세서 실지급액과 실제 입금액이 일치합니다.",
      "nextAction": "특이사항이 없습니다."
    }
  ]
}
```

> 💡 **데모 시연 안내**: 초기 시드 데이터에는 7월 급여만 PayCheck으로 등록되어 있습니다. 상단의 **[🔄 급여 동기화]** 버튼을 누르면 8월 급여(-12만원 차액)가 실시간으로 감지되어 목록에 추가됩니다!

---

### 🤖 2) AI 이상징후 원인 설명 및 사장님 질문카드 생성 API (다국어 지원)
* **Endpoint**: `POST /api/paychecks/{paycheckId}/explain`
* **다국어 지원**: 사용자 프로필의 `language`(`vi`, `en`, `zh`, `th`, `ko`) 및 국적에 맞춰 `nativeScript`가 해당 모국어로 동적 번역 생성됩니다.
* **Response 예시**:
```json
{
  "success": true,
  "data": {
    "summary": "2026-08 급여의 실입금액이 명세서 금액보다 120,000원 적게 입금되어 확인이 필요합니다.",
    "reasons": [
      "2026-08 임금명세서",
      "급여 입금 통장 거래내역"
    ],
    "nextActions": [
      "임금명세서 상세 공제 내역 확인",
      "추가 공제 항목(세금, 4대보험, 가불금 등) 여부 확인",
      "사업주 사실 확인 문의"
    ],
    "employerQuestionCards": [
      {
        "title": "2026-08 급여 차액 120,000원 확인 요청",
        "koreanScript": "안녕하세요 사장님, 한국정밀 2026-08 급여 입금액(2,260,000원)과 명세서 금액에 120,000원의 차이가 있어 확인 부탁드립니다.",
        "nativeScript": "Xin chào giám đốc, lương tháng 2026-08 có chênh lệch 120,000 won giữa phiếu lương và tiền vào tài khoản, nhờ giám đốc kiểm tra giúp tôi."
      }
    ]
  }
}
```

---

### ⚙️ 3) 급여 분석 수동 실행 API
* **Endpoint**: `POST /api/paychecks/analyze`
* **Request Body**:
```json
{
  "payPeriod": "2026-08",
  "contractDocumentId": 1,
  "payslipDocumentId": 3,
  "bankReceiptDocumentId": null,
  "transactionId": null
}
```

---

## 4. Calendar 도메인 API

* `GET /api/calendar/events?from=2026-08-01&to=2026-08-31` : 일정 조회
* `POST /api/calendar/events` : 개인 일정 등록
* `PATCH /api/calendar/events/{eventId}` : 일정 수정
* `DELETE /api/calendar/events/{eventId}` : 일정 삭제

#### 📅 이벤트 유형 (`eventType`)
* `PAYDAY`: 정기 급여일 (기본 파란색)
* `PAYCHECK`: 실제 급여 입금/분석 완료 이벤트 (정상은 녹색, 이상징후는 주황색)
* `PERSONAL`: 사용자가 직접 등록한 개인 일정 (보라색)
* `EXIT`: 비자 만료 / 출국 예정일 (빨간색)

---

## 5. Profile 도메인 API

* `GET /api/profile` : 사용자 정보 조회
* `PATCH /api/profile` : 프로필 수정
  * `payday`를 변경하면 캘린더의 `PAYDAY` 일정이 자동 재계산됩니다.
  * `expectedExitDate`를 변경하면 캘린더의 `EXIT` 일정이 자동 재계산됩니다.

---

## 6. 배치 및 개발 편의 도구 API (Demo & Testing)

### 🔄 1) 급여 자동 감지 배치 즉시 시뮬레이션 트리거
* **Endpoint**: `POST /api/batch/salary-monitoring`
* **Header**: `X-Demo-User-Id: 1`
* **Response DTO (`SalaryMonitoringBatchResponse`)**:
```json
{
  "success": true,
  "data": {
    "processedCount": 1,
    "createdCount": 1,
    "updatedCount": 0,
    "paychecks": [
      {
        "paycheckId": 2,
        "payPeriod": "2026-08",
        "contractAmount": 2300000.00,
        "payslipAmount": 2380000.00,
        "actualAmount": 2260000.00,
        "differenceAmount": -120000.00,
        "expectedPaymentDate": "2026-08-25",
        "paymentDate": "2026-08-25T09:14:00",
        "status": "EXPLANATION_REQUIRED",
        "analysisSummary": "임금명세서 실지급액과 실제 입금액에서 120,000원의 차이가 확인되었습니다.",
        "nextAction": "이번 달 임금명세서의 공제 및 별도 지급 여부를 확인하세요."
      }
    ]
  },
  "message": "급여 자동 감지 모니터링 배치가 완료되었습니다. (총 1건 감지)"
}
```

### 🛠 2) 기타 유틸리티 API
| Method | Endpoint | 용도 |
|---|---|---|
| `GET` | `/api/mock/bank/transactions` | 모의 오픈뱅킹 계좌 거래내역 조회 |
| `POST` | `/api/dev/reset-seed` | **[시연 리셋용]** 시드 데이터를 초기 상태(7월만 등록된 상태)로 즉시 초기화 |

---

## 7. 프론트엔드 연동 시 주의사항 (Critical Tips)

1. **날짜 포맷**:
   * `payPeriod`는 반드시 `"YYYY-MM"` (예: `"2026-08"`) 형식을 사용합니다.
   * `from`, `to`, `workStartDate`, `expectedExitDate`는 `"YYYY-MM-DD"` 형식을 사용합니다.
2. **차액(`differenceAmount`) 계산 기준**:
   * `differenceAmount = actualAmount - payslipAmount`
   * 차액이 음수(`-120000.00`)이면 **덜 지급된 것(감소)**을 의미하며, 프론트에서 `-120,000원` 또는 `12만원 부족`으로 표시합니다.
3. **사장님 질문카드 복사 UI**:
   * `employerQuestionCards` 배열의 `koreanScript`를 한국인 사장님에게 카카오톡/문자로 보낼 수 있도록 **[복사하기]** 버튼을 배치하면 심사 및 데모에서 높은 점수를 받을 수 있습니다.
