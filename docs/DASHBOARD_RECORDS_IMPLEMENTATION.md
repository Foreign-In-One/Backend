# Dashboard · Records 조회 API v1

## 범위와 기준

- 사용자 승인: Dashboard/Records 조회 API 추가. 공통 인증·사용자 정책·삭제·DB 구조 변경은 제외.
- 기준 dev: `9605956288204ab5bfd89a32b25d7616c579eab1`.
- TaxCheck 컬럼 확인: PR #7 `30f4dded7282d64356faf8834f32c2472318be5b`.
- ExitCheck 컬럼 확인: PR #6 `18e261d040c20d87e3f77447c81212ad9566ef7a`.
- 확인일: 2026-09-01. 이 시점에 두 PR은 미병합이었다. 이 변경은 두 PR을 변경하거나 병합하지 않는다.
- 기존 PRD/API_SPEC/DB_SCHEMA 및 실제 Entity의 물리 테이블 이름을 기준으로 한다.

## 구현 방식과 의존성

`domain/overview`의 Controller → Service → 읽기 전용 JDBC Repository 구조다.
두 화면이 같은 기록 요약을 사용하므로 작은 조회 모듈 하나를 공유한다.
기존 Spring Data JPA가 사용하는 DataSource와 JDBC 지원을 이용한다. 새 라이브러리·설정·Entity·테이블을 추가하지 않는다.

JPA는 그대로 유지한다. 이 조회 모듈만 `JdbcTemplate`으로 필요한 컬럼을 읽는다.
이 방식은 미병합 TaxCheck/ExitCheck Java 클래스에 대한 컴파일 의존성을 없애지만,
**실제 DB의 `tax_checks`, `exit_checks` 테이블 의존성을 없애는 것은 아니다.**

- dev에서 별도 기능 브랜치를 만들어 코드 작성·독립 H2 테스트를 진행할 수 있다.
- 실제 서버 통합 실행은 두 원천 도메인의 스키마가 준비된 개발 DB에서 검증해야 한다.
- 이 API를 위해 생산 DB에 빈 테이블을 임의 생성하지 않는다.
- 테이블/쿼리 실패를 `[]`, `0`, 샘플 결과로 바꾸지 않는다. 기존 공통 `500 INTERNAL_SERVER_ERROR`를 반환한다.

## 공통 요청/응답

기존 개발용 선택 우선순위 `userId` query → `X-User-Id` → `X-Demo-User-Id` → `1`을 유지한다.
선택된 사용자가 없으면 `404 USER_NOT_FOUND`, ID가 0 이하이거나 형식이 틀리면 `400 INVALID_REQUEST`.
아래 예시는 모두 공통 응답의 `data` 부분이다. 성공 응답에 `Cache-Control: no-store`를 설정한다.

이 선택값은 로그인·소유권 인증이 아니다. 사용자별 WHERE 조건은 **선택된 데이터 범위**만 제한한다.
클라이언트가 다른 ID를 선택하는 문제를 해결하지 않으므로 실제 문서·개인정보의 공개 서비스 용도로 사용할 수 없다.
가상 데이터로 개발하며, 공개 배포 전 인증/접근 범위/업로드 정책은 별도로 합의·구현해야 한다.

## GET `/api/dashboard`

선택 query `year`: 2000부터 현재 한국 날짜의 연도까지. 생략 시 Asia/Seoul의 현재 연도.
소수·문자·허용 범위 밖 연도는 `400 INVALID_REQUEST`.

```json
{
  "year": 2026,
  "paySummary": {
    "totalReceivedPay": null,
    "recordedMonths": 0,
    "amountKnownMonths": 0,
    "recordedPeriods": [],
    "missingAmountPeriods": []
  },
  "latestPaycheck": null,
  "latestTaxCheck": null,
  "latestExitCheck": null,
  "recentRecords": []
}
```

위 예시는 기록이 없는 사용자의 응답이다. 기록이 있으면 각 최신 필드에 아래 RecordSummary 객체가 들어간다.
예를 들어 1월 2,600,000.10원, 2월 금액 미확인, 3월 확인된 0원이면 합계 2,600,000.10원,
등록 3개월·금액 확인 2개월·미확인 목록 `["2026-02"]`가 된다.

### 급여 집계

- 같은 사용자의 `paychecks.pay_period`가 선택 연도인 행의 `actual_amount`만 합산한다.
- 입금일·분석일·생성일 기준 연도가 아니다. 예: 2025-12 급여를 2026년에 분석해도 2025에 포함한다.
- 계약금액·명세서 금액·TaxCheck 입력 소득·OCR 수치를 실제 입금액으로 대체하지 않는다.
- 데이터가 없거나 모든 금액이 미확인이면 합계 `null`. 확인된 실제 0원은 `0`이며 알려진 월로 센다.
- 일부만 확인되면 알려진 금액만 더한다. 화면에는 “기록된 실입금 합계”로 표시하고 확인된 월 수를 함께 보여준다.
- `recordedMonths`: 등록된 해당 연도 급여월 수. `amountKnownMonths`: 실제 입금액이 알려진 급여월 수.
- `missingAmountPeriods`: 등록은 되었지만 금액이 없는 월. 근무하지 않은 달이나 미등록 월을 임의로 만들지 않는다.
- 월 평균×12로 연환산하지 않는다. 실입금 합계를 세전 연간 소득·세액으로 표시하지 않는다.
- 잘못 저장된 급여월, 같은 해 동일 월 중복, 음수 실입금은 서버 데이터 오류로 처리한다. 조용히 합계에서 빼지 않는다.

### 최신 결과

`year` 필터는 **paySummary에만 적용**한다.
`latestPaycheck`, `latestTaxCheck`, `latestExitCheck`는 전체 이력에서 종류별 최신 1개이다.
따라서 과거 연도를 선택해도 최신 TaxCheck의 `taxYear`는 다를 수 있다. 화면에서 자체 연도/기간을 표시한다.
`recentRecords`는 전체 이력 최신 3건. 날짜가 완전히 없으면 목록 마지막에 배치한다.
프로필·캘린더는 기존 API를 사용한다. 새로운 AI 조언/계산을 생성하지 않는다.

## GET `/api/records`

선택 query `type`: `PAYCHECK`, `TAX_CHECK`, `EXIT_CHECK`. 생략하면 전체. 다른 값은 400.

```json
{
  "items": [
    {
      "recordKey": "TAX_CHECK:10002",
      "type": "TAX_CHECK",
      "sourceId": 10002,
      "recordedAt": "2026-08-31T10:00:00",
      "analyzedAt": "2026-08-31T10:00:00",
      "status": "REVIEW_REQUIRED",
      "analysisSummary": "저장된 분석 요약",
      "nextAction": "저장된 다음 행동",
      "payPeriod": null,
      "taxYear": 2026,
      "expectedExitDate": null,
      "actualAmount": null,
      "readinessScore": null
    }
  ],
  "counts": {"all": 1, "paycheck": 0, "taxCheck": 1, "exitCheck": 0}
}
```

필터를 적용해도 `counts`는 선택된 사용자의 **필터 전 전체 건수**를 반환하므로 탭 건수가 변하지 않는다.
위 예시는 TaxCheck 한 건만 있는 사용자다. v1은 페이지네이션 없이 필터에 맞는 모든 기록을 반환한다.

### RecordSummary

| 필드 | 의미 |
| --- | --- |
| `recordKey` | `종류:원본ID`. 서로 다른 테이블의 ID가 같아도 충돌하지 않음 |
| `type`, `sourceId` | 원천 종류·PK. 상세는 기존 종류별 상세 API에서 조회 |
| `recordedAt` | 분석 시각 우선, 없으면 생성 시각. 둘 다 없으면 null |
| `analyzedAt` | 실제 저장된 분석 시각. 생성 시각을 분석 완료 시각으로 위장하지 않음 |
| `status`, `analysisSummary`, `nextAction` | 저장된 원천 값을 그대로 조회. 상태의 법적 의미를 새로 판정하지 않음 |
| `payPeriod`, `actualAmount` | PayCheck만 제공. 다른 종류는 null |
| `taxYear` | TaxCheck만 제공 |
| `expectedExitDate`, `readinessScore` | ExitCheck만 제공. 점수는 준비도이지 법률상 수급 가능성이 아님 |

정렬: `recordedAt DESC`(null 마지막) → 같은 시각은 PAYCHECK/TAX_CHECK/EXIT_CHECK 순서 → 같은 종류면 ID DESC.
시각은 기존 DATETIME 기반의 오프셋 없는 값이며 임의 UTC 변환을 하지 않는다.
기존 도메인이 같은 행을 재분석하여 갱신하면 새 기록을 하나 더 만드는 대신 그 행의 최신 내용이 보인다.
TaxCheck의 새 분석은 별도 행으로 저장되는 현재 정책을 그대로 따른다.

시뮬레이션은 원천 테이블에 저장되지 않으므로 통합 기록에 생기지 않는다.
GET 호출은 DB·프로필·분석 스냅샷·캘린더를 수정하지 않으며 원문 파일/저장 경로/OCR 원문도 반환하지 않는다.

## 프론트 후속 연결 시 주의

- 현재 `/dashboard`, `/records`는 localStorage/Client Context 사용 중이다. 이번 패치는 프론트를 수정하지 않는다.
- API 에러를 빈 목록으로 처리하지 말고 로딩·에러·빈 상태를 분리한다.
- `value > 0`으로 표시 여부를 판단하지 말고 `value !== null`로 0원을 보존한다.
- `recordKey`를 React key로 쓰고, enum을 기존 i18n 키에 매핑한다.
- Records의 삭제 버튼은 이 API와 연결되지 않는다. 공유 데이터 삭제 계약을 정하기 전에는 연결하지 않는다.
- 펼친 TaxCheck 카드 등 상세 내용은 `GET /api/tax-checks/{sourceId}`로 가져온다. 요약을 전체 스냅샷으로 간주하지 않는다.

## 테스트와 검증 상태

- 순수 집계/정렬/필터 규칙 23개 시나리오: Java 17 compiler module로 실행하여 통과.
- H2 API 통합 테스트 13개와 서비스 테스트 3개, 같은 규칙의 JUnit 동적 테스트를 추가했다.
- 전체 Gradle/JUnit 및 실제 MySQL 서버 실행은 패키지 생성 환경에서 실행하지 못했다.
  이 환경은 Java 17만 있으며 프로젝트는 Java 21을 요구한다. 작성 상태와 실행 통과를 구분한다.
- 적용한 노트북에서 `.\gradlew.bat test --console=plain` 실행 필요. 기존 TaxCheck 테스트 성공과 이번 패치 검증은 별개다.
- API 통합 테스트의 H2 DB 이름은 `overview_test`로 기존 테스트와 분리한다.
  `src/test/resources/dashboard-records-fixture-schema.sql`은 미병합 도메인 읽기 컬럼만 제공하는 테스트 픽스처다.
  이 테스트가 성공해도 원천 도메인 생성 API/실제 MySQL/프론트 E2E 연결까지 검증된 것은 아니다.

## 참고

- [Spring JDBC 공식 문서](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)
- [Spring SQL 테스트 공식 문서](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/executing-sql.html)
- [TaxCheck PR #7](https://github.com/Foreign-In-One/Backend/pull/7)
- [ExitCheck PR #6](https://github.com/Foreign-In-One/Backend/pull/6)
