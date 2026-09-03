package com.foreigninone.backend.domain.taxcheck.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic preparation checks; never determines legal eligibility or a refund. */
public final class TaxCheckRules {
    public static final String VERSION = "taxcheck-manual-reference-v1";
    public static final BigDecimal FLAT_RATE = new BigDecimal("0.19");
    public static final String NTS_URL = "https://www.nts.go.kr/nts/na/ntt/selectNttInfo.do?bbsId=1028&mi=2201&nttSn=1347723";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
    private static final Set<Integer> REFERENCE_YEARS = Set.of(2025, 2026);

    private TaxCheckRules() {}

    public record Income(BigDecimal annualIncome, BigDecimal nonTaxableIncome, Boolean confirmed) {}
    public record Conditions(Boolean housingSaving, Boolean isHomeless,
                             Boolean housingSavingProof, Boolean usesDeductions) {}
    public record PayRecord(String payPeriod, BigDecimal actualAmount) {}
    public record PaySummary(BigDecimal totalReceivedPay, int recordedMonths,
                             int amountKnownMonths, List<String> recordedPeriods,
                             List<String> missingAmountPeriods) {}
    public record Context(int taxYear, LocalDate entryDate, String employmentStatus,
                          LocalDate assessedOn, PaySummary paySummary) {}
    public record Evidence(String title, String url) {}
    public record Card(String id, String title, String status, String tone, String summary,
                       List<String> confirmed, List<String> missing,
                       List<String> nextActions, List<Evidence> evidence) {}
    public record Calculation(String mode, String ruleVersion, BigDecimal rate,
                              BigDecimal incomeBase, boolean eligibilityConfirmed,
                              List<String> missingFields, List<String> warnings) {}
    public record Result(BigDecimal annualIncome, BigDecimal flatTaxEstimate,
                         BigDecimal generalTaxEstimate, BigDecimal taxDifference,
                         String residentStatus, Long elapsedDaysReference, String status,
                         List<Card> cards, List<String> requiredDocuments,
                         String analysisSummary, String nextAction, Calculation calculation) {}

    /** Only actualAmount is aggregated. A missing amount is not a zero deposit. */
    public static PaySummary summarizePay(int taxYear, List<PayRecord> records) {
        BigDecimal total = BigDecimal.ZERO;
        List<String> periods = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int known = 0;
        for (PayRecord record : records) {
            YearMonth period = YearMonth.parse(record.payPeriod());
            if (period.getYear() != taxYear) continue;
            if (!seen.add(record.payPeriod())) {
                throw new IllegalArgumentException("같은 귀속월의 Paycheck가 중복되어 집계할 수 없습니다.");
            }
            periods.add(record.payPeriod());
            if (record.actualAmount() == null) {
                missing.add(record.payPeriod());
            } else {
                validateAmount(record.actualAmount(), "Paycheck.actualAmount");
                total = total.add(record.actualAmount());
                known++;
            }
        }
        periods.sort(String::compareTo);
        missing.sort(String::compareTo);
        return new PaySummary(known == 0 ? null : total.setScale(2), periods.size(), known,
                List.copyOf(periods), List.copyOf(missing));
    }

    public static Result evaluate(Context context, Income income, Conditions suppliedConditions) {
        if (context.taxYear() < 2000 || context.taxYear() > context.assessedOn().getYear()) {
            throw new IllegalArgumentException("귀속연도는 2000년부터 현재 연도까지 입력하세요.");
        }
        if (income != null) {
            validateAmount(income.annualIncome(), "income.annualIncome");
            validateAmount(income.nonTaxableIncome(), "income.nonTaxableIncome");
        }
        Conditions conditions = suppliedConditions == null
                ? new Conditions(null, null, null, null) : suppliedConditions;
        List<String> missing = new ArrayList<>();
        if (income == null || income.annualIncome() == null) missing.add("ANNUAL_INCOME_REQUIRED");
        if (income == null || income.nonTaxableIncome() == null) missing.add("NON_TAXABLE_INCOME_REQUIRED");
        if (income == null || !Boolean.TRUE.equals(income.confirmed())) missing.add("INCOME_CONFIRMATION_REQUIRED");
        if (!REFERENCE_YEARS.contains(context.taxYear())) missing.add("TAX_YEAR_RULE_NOT_VERIFIED");

        BigDecimal base = null;
        BigDecimal flat = null;
        if (missing.isEmpty()) {
            base = income.annualIncome().add(income.nonTaxableIncome());
            validateAmount(base, "단일세율 참고 계산용 소득 합계");
            // Presentation precision only; not the statutory rounding for a tax return.
            flat = base.multiply(FLAT_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        List<String> warnings = new ArrayList<>(List.of(
                "19% 단일세율을 적용한다고 가정한 소득세 참고액이며 적용 자격을 판정하지 않습니다.",
                "일반세율 비교·지방소득세·기납부세액 정산·환급액은 계산하지 않습니다.",
                "사용자 확인은 문서 진위나 세무 정확성의 자동 검증을 뜻하지 않습니다.",
                "실입금 합계는 세금 계산에 사용하지 않으며 월평균을 12개월로 환산하지 않습니다."));
        if (context.paySummary().recordedMonths() < 12) {
            warnings.add("등록된 급여 기록만 집계했으며, 1년 전체 소득의 완전성을 보장하지 않습니다.");
        }
        if (!context.paySummary().missingAmountPeriods().isEmpty()) {
            warnings.add("입금액이 확인되지 않은 급여 기록이 있습니다.");
        }
        if ("NOT_WORKING".equals(context.employmentStatus())) {
            warnings.add("현재 근무 전 상태입니다. 과거 귀속연도의 소득 유무와는 별도로 확인하세요.");
        }

        Long elapsedDays = elapsedDays(context);
        String residentStatus = elapsedDays == null ? "UNKNOWN" : "REVIEW_REQUIRED";
        Card resident = new Card("resident", "거주자 판단을 위한 자료 확인", residentStatus,
                elapsedDays == null ? "unknown" : "need",
                "입국일만으로 거주자 여부를 확정하지 않습니다. 주소·생활관계·출입국 이력이 필요합니다.",
                elapsedDays == null ? List.of() : List.of("입국일 기반 해당 연도 경과일 참고값: " + elapsedDays + "일"),
                elapsedDays == null ? List.of("유효한 입국일", "주소·출입국 이력") : List.of("주소·출입국 이력"),
                List.of("프로필과 출입국 자료를 확인하세요."), evidence());

        List<String> housingConfirmed = new ArrayList<>();
        List<String> housingMissing = new ArrayList<>();
        questionnaire("주택마련저축 가입", conditions.housingSaving(), housingConfirmed, housingMissing);
        questionnaire("무주택 여부", conditions.isHomeless(), housingConfirmed, housingMissing);
        questionnaire("납입증명서 보유", conditions.housingSavingProof(), housingConfirmed, housingMissing);
        // These three answers alone cannot establish eligibility (e.g. spouse/head requirements).
        housingMissing.add("귀속연도별 공제대상·배우자 관계·소득요건 확인");
        Card housing = new Card("housing", "주택마련저축 공제 준비", "REVIEW_REQUIRED", "need",
                "설문은 준비 항목 확인용입니다. 응답만으로 공제 가능·불가를 확정하지 않습니다.",
                List.copyOf(housingConfirmed), List.copyOf(housingMissing),
                List.of("납입증명서와 공제대상 요건을 확인하세요."), evidence());

        List<String> flatMissing = new ArrayList<>();
        if (flat == null) flatMissing.add("귀속연도와 연간 소득 항목의 입력·확인");
        flatMissing.add("국내 최초 근로일·특수관계·근로 형태 등 적용 자격 확인");
        String deductionNote = Boolean.TRUE.equals(conditions.usesDeductions())
                ? "공제를 이용 중이어도 단일세율 자격이 자동 배제되는 것은 아닙니다. 유불리 비교는 제공하지 않습니다."
                : "공제 이용 여부만으로 단일세율의 적용 자격이나 유리함을 판단하지 않습니다.";
        Card flatCard = new Card("flat", "19% 적용 가정 참고 계산", "REVIEW_REQUIRED", "need",
                flat == null ? "정보가 부족해 참고액을 계산하지 않았습니다. " + deductionNote
                        : "확인한 소득에 19%를 적용한 참고액입니다. " + deductionNote,
                flat == null ? List.of() : List.of("사용자가 확인한 연간 소득 항목 사용"),
                List.copyOf(flatMissing), List.of("회사 연말정산 담당자 또는 국세청에 적용 요건을 확인하세요."), evidence());

        boolean noIncomeInformation = income == null || (income.annualIncome() == null && income.nonTaxableIncome() == null);
        String status = noIncomeInformation && context.paySummary().recordedMonths() == 0 ? "UNKNOWN" : "REVIEW_REQUIRED";
        String summary = flat == null ? "자료 확인이 필요하여 세액 비교를 제공하지 않았습니다."
                : "19% 적용을 가정한 참고액만 계산했습니다. 실제 적용 여부와 유불리는 추가 확인이 필요합니다.";
        String nextAction = flat == null ? "귀속연도별 총급여와 단일세율 계산에 포함할 비과세소득을 확인하세요."
                : "일반세율 비교에 필요한 공제 자료와 단일세율 적용 자격을 확인하세요.";
        return new Result(income != null && Boolean.TRUE.equals(income.confirmed()) ? income.annualIncome() : null,
                flat, null, null, residentStatus, elapsedDays, status,
                List.of(resident, housing, flatCard),
                List.of("해당 귀속연도 근로소득 원천징수영수증 또는 전체 급여명세서", "비과세 항목 내역", "소득·세액공제 증빙"),
                summary, nextAction, new Calculation("FLAT_19_ASSUMPTION", VERSION, FLAT_RATE,
                base, false, List.copyOf(missing), List.copyOf(warnings)));
    }

    private static Long elapsedDays(Context context) {
        if (context.entryDate() == null || context.entryDate().isAfter(context.assessedOn())) return null;
        LocalDate end = LocalDate.of(context.taxYear(), 12, 31);
        if (end.isAfter(context.assessedOn())) end = context.assessedOn();
        LocalDate start = context.entryDate().plusDays(1);
        LocalDate yearStart = LocalDate.of(context.taxYear(), 1, 1);
        if (start.isBefore(yearStart)) start = yearStart;
        return start.isAfter(end) ? 0L : ChronoUnit.DAYS.between(start, end) + 1;
    }

    private static void validateAmount(BigDecimal amount, String field) {
        if (amount != null && (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0)) {
            throw new IllegalArgumentException(field + ": 0 이상, 정수 13자리·소수 2자리 이내로 입력하세요.");
        }
    }

    private static void questionnaire(String label, Boolean value, List<String> confirmed, List<String> missing) {
        if (value == null) missing.add(label + " 응답");
        else confirmed.add(label + ": " + (value ? "예" : "아니오"));
    }

    private static List<Evidence> evidence() {
        return List.of(new Evidence("국세청 2025년 귀속 외국인 근로자 연말정산 안내 (2026-01-07)", NTS_URL));
    }
}
