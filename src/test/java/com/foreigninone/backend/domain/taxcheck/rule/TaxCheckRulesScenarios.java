package com.foreigninone.backend.domain.taxcheck.rule;

import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same scenarios run in JUnit and an offline, dependency-free Java smoke test. */
public class TaxCheckRulesScenarios {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    public static Map<String, Runnable> cases() {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("confirmed_gross_plus_non_taxable", () -> {
            Result result = evaluate(new Income(money("30000000"), money("2000000"), true));
            equalMoney("32000000", result.calculation().incomeBase());
            equalMoney("6080000", result.flatTaxEstimate());
            check(result.generalTaxEstimate() == null && result.taxDifference() == null, "comparison must stay null");
            check(!result.calculation().eligibilityConfirmed(), "calculation is not eligibility");
        });
        tests.put("unconfirmed_income_never_calculated", () -> {
            Result result = evaluate(new Income(money("30000000"), BigDecimal.ZERO, false));
            check(result.flatTaxEstimate() == null && result.annualIncome() == null, "unconfirmed income");
            check(result.calculation().missingFields().contains("INCOME_CONFIRMATION_REQUIRED"), "missing reason");
        });
        tests.put("missing_income_is_not_zero", () -> {
            Result result = evaluate(null);
            check(result.flatTaxEstimate() == null && result.annualIncome() == null, "missing amount is not zero");
            check("UNKNOWN".equals(result.status()), "no records and no income");
        });
        tests.put("confirmed_zero_is_zero", () -> equalMoney("0", evaluate(new Income(BigDecimal.ZERO, BigDecimal.ZERO, true)).flatTaxEstimate()));
        tests.put("missing_non_taxable_is_not_zero", () -> {
            Result result = evaluate(new Income(money("30000000"), null, true));
            check(result.flatTaxEstimate() == null, "missing non-taxable");
            check(result.calculation().missingFields().contains("NON_TAXABLE_INCOME_REQUIRED"), "reason");
        });
        tests.put("missing_annual_income", () -> check(evaluate(new Income(null, BigDecimal.ZERO, true)).flatTaxEstimate() == null, "annual income required"));
        tests.put("null_confirmation", () -> check(evaluate(new Income(BigDecimal.ZERO, BigDecimal.ZERO, null)).flatTaxEstimate() == null, "confirmation required"));
        tests.put("negative_money_rejected", () -> expectInvalid(() -> evaluate(new Income(money("-1"), BigDecimal.ZERO, true))));
        tests.put("negative_non_taxable_rejected", () -> expectInvalid(() -> evaluate(new Income(BigDecimal.ZERO, money("-1"), false))));
        tests.put("excess_precision_rejected", () -> expectInvalid(() -> evaluate(new Income(money("0.001"), BigDecimal.ZERO, true))));
        tests.put("column_overflow_rejected", () -> expectInvalid(() -> evaluate(new Income(money("10000000000000"), BigDecimal.ZERO, true))));
        tests.put("combined_base_overflow_rejected", () -> expectInvalid(() -> evaluate(new Income(money("9999999999999.99"), money("0.01"), true))));
        tests.put("decimal_reference_rounding", () -> equalMoney("0.19", evaluate(new Income(money("1.01"), BigDecimal.ZERO, true)).flatTaxEstimate()));
        tests.put("year_filter_and_missing_actual_amount", () -> {
            PaySummary summary = TaxCheckRules.summarizePay(2026, List.of(
                    new PayRecord("2025-12", money("999999")), new PayRecord("2026-01", money("2600000")),
                    new PayRecord("2026-02", null), new PayRecord("2026-03", BigDecimal.ZERO)));
            equalMoney("2600000", summary.totalReceivedPay());
            check(summary.recordedMonths() == 3 && summary.amountKnownMonths() == 2, "counts");
            check(summary.missingAmountPeriods().equals(List.of("2026-02")), "missing periods");
        });
        tests.put("empty_pay_summary", () -> check(TaxCheckRules.summarizePay(2026, List.of()).totalReceivedPay() == null, "empty is unknown"));
        tests.put("all_unknown_pay_amounts", () -> {
            PaySummary summary = TaxCheckRules.summarizePay(2026, List.of(new PayRecord("2026-01", null)));
            check(summary.totalReceivedPay() == null && summary.recordedMonths() == 1, "all unknown");
        });
        tests.put("duplicate_pay_period_rejected", () -> expectInvalid(() -> TaxCheckRules.summarizePay(2026,
                List.of(new PayRecord("2026-01", money("1")), new PayRecord("2026-01", money("2"))))));
        tests.put("negative_pay_record_rejected", () -> expectInvalid(() -> TaxCheckRules.summarizePay(2026,
                List.of(new PayRecord("2026-01", money("-1"))))));
        tests.put("paychecks_never_become_tax_income", () -> {
            PaySummary summary = TaxCheckRules.summarizePay(2026, List.of(new PayRecord("2026-01", money("2600000"))));
            Result result = TaxCheckRules.evaluate(context(2026, LocalDate.of(2025, 1, 1), summary), null, null);
            check(result.annualIncome() == null && result.flatTaxEstimate() == null, "net deposit is not gross");
        });
        tests.put("no_annualization_of_partial_months", () -> {
            PaySummary summary = TaxCheckRules.summarizePay(2026, List.of(new PayRecord("2026-01", money("2600000"))));
            Result result = TaxCheckRules.evaluate(context(2026, null, summary), new Income(money("1000000"), BigDecimal.ZERO, true), null);
            equalMoney("190000", result.flatTaxEstimate());
        });
        tests.put("unsupported_rule_year", () -> {
            Result result = TaxCheckRules.evaluate(context(2024, null, empty()), new Income(money("30000000"), BigDecimal.ZERO, true), null);
            check(result.flatTaxEstimate() == null && result.calculation().missingFields().contains("TAX_YEAR_RULE_NOT_VERIFIED"), "unsupported year");
        });
        tests.put("future_tax_year_rejected", () -> expectInvalid(() -> TaxCheckRules.evaluate(context(2027, null, empty()), null, null)));
        tests.put("183_days_does_not_confirm_residency", () -> {
            for (int days : List.of(182, 183, 184)) {
                Result result = TaxCheckRules.evaluate(context(2026, TODAY.minusDays(days), empty()), null, null);
                check(result.elapsedDaysReference() == days, "days boundary " + days);
                check("REVIEW_REQUIRED".equals(result.residentStatus()), "not legal residence");
            }
        });
        tests.put("entry_day_excluded", () -> {
            Result result = TaxCheckRules.evaluate(context(2026, TODAY, empty()), null, null);
            check(result.elapsedDaysReference() == 0, "entry day excluded");
        });
        tests.put("future_entry_is_unknown", () -> {
            Result result = TaxCheckRules.evaluate(context(2026, TODAY.plusDays(1), empty()), null, null);
            check(result.elapsedDaysReference() == null && "UNKNOWN".equals(result.residentStatus()), "invalid future entry");
        });
        tests.put("previous_year_days_capped_at_december", () -> {
            Result result = TaxCheckRules.evaluate(context(2025, LocalDate.of(2024, 1, 1), empty()), null, null);
            check(result.elapsedDaysReference() == 365, "not days through present year");
        });
        tests.put("housing_answers_never_confirm_eligibility", () -> {
            for (Boolean saving : new Boolean[]{null, false, true}) {
                for (Boolean homeless : new Boolean[]{null, false, true}) {
                    for (Boolean proof : new Boolean[]{null, false, true}) {
                        Result result = TaxCheckRules.evaluate(context(2026, null, empty()), null,
                                new Conditions(saving, homeless, proof, null));
                        check("REVIEW_REQUIRED".equals(result.cards().get(1).status()), "housing eligibility not proven");
                    }
                }
            }
        });
        tests.put("deductions_do_not_automatically_disqualify_flat", () -> {
            Result result = TaxCheckRules.evaluate(context(2026, null, empty()),
                    new Income(money("30000000"), BigDecimal.ZERO, true), new Conditions(null, null, null, true));
            equalMoney("5700000", result.flatTaxEstimate());
            check("REVIEW_REQUIRED".equals(result.cards().get(2).status()), "eligibility must remain review");
        });
        tests.put("not_working_is_not_zero_income", () -> {
            Context context = new Context(2026, null, "NOT_WORKING", TODAY, empty());
            Result result = TaxCheckRules.evaluate(context, null, null);
            check(result.annualIncome() == null && result.flatTaxEstimate() == null, "not working is not zero");
        });
        return tests;
    }

    public static void main(String[] args) {
        Map<String, Runnable> tests = cases();
        tests.forEach((name, test) -> { test.run(); System.out.println("PASS " + name); });
        System.out.println("Passed " + tests.size() + " rule scenarios (including 27 housing combinations).");
    }

    private static Result evaluate(Income income) { return TaxCheckRules.evaluate(context(2026, null, empty()), income, null); }
    private static Context context(int year, LocalDate entry, PaySummary summary) { return new Context(year, entry, "WORKING", TODAY, summary); }
    private static PaySummary empty() { return TaxCheckRules.summarizePay(2026, List.of()); }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void equalMoney(String expected, BigDecimal actual) { check(actual != null && money(expected).compareTo(actual) == 0, "expected " + expected + ", actual " + actual); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
