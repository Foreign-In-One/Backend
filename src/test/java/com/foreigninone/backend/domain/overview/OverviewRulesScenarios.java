package com.foreigninone.backend.domain.overview;

import com.foreigninone.backend.domain.overview.dto.RecordSummary;
import com.foreigninone.backend.domain.overview.dto.RecordType;
import com.foreigninone.backend.domain.overview.rule.OverviewRules;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The same scenarios run in JUnit and in a plain-Java, dependency-free verification. */
public final class OverviewRulesScenarios {
    public record Scenario(String name, Runnable verify) {
    }

    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime LATE = EARLY.plusDays(1);

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario("no records is unknown, not zero", () -> {
                    var sum = OverviewRules.paySummary(2026, List.of());
                    eq(sum.totalReceivedPay(), null);
                    eq(sum.recordedMonths(), 0);
                    eq(sum.amountKnownMonths(), 0);
                }),
                new Scenario("registered unknown amount stays unknown", () -> {
                    var sum = OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", null)));
                    eq(sum.totalReceivedPay(), null);
                    eq(sum.recordedMonths(), 1);
                    eq(sum.amountKnownMonths(), 0);
                    eq(sum.missingAmountPeriods(), List.of("2026-01"));
                }),
                new Scenario("confirmed zero counts as known", () -> {
                    var sum = OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "0.00")));
                    money(sum.totalReceivedPay(), "0");
                    eq(sum.amountKnownMonths(), 1);
                    eq(sum.missingAmountPeriods(), List.of());
                }),
                new Scenario("partial amounts and months are separate", () -> {
                    var sum = OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "2.35"),
                            pay(2, "2026-02", null), pay(3, "2026-03", "0")));
                    money(sum.totalReceivedPay(), "2.35");
                    eq(sum.recordedMonths(), 3);
                    eq(sum.amountKnownMonths(), 2);
                    eq(sum.missingAmountPeriods(), List.of("2026-02"));
                }),
                new Scenario("decimal arithmetic is exact", () -> {
                    money(OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "0.10"),
                            pay(2, "2026-02", "0.20"))).totalReceivedPay(), "0.30");
                }),
                new Scenario("large totals do not overflow integer arithmetic", () -> {
                    money(OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "9999999999999.99"),
                            pay(2, "2026-02", "9999999999999.99"))).totalReceivedPay(), "19999999999999.98");
                }),
                new Scenario("year follows payPeriod, not analysis date", () -> {
                    money(OverviewRules.paySummary(2025, List.of(pay(1, "2025-12", "4"),
                            pay(2, "2026-01", "100"))).totalReceivedPay(), "4");
                }),
                new Scenario("other year is not a fallback income", () -> {
                    eq(OverviewRules.paySummary(2026, List.of(pay(1, "2025-12", "100"))).totalReceivedPay(), null);
                }),
                new Scenario("tax and exit records are not pay amounts", () -> {
                    eq(OverviewRules.paySummary(2026, List.of(record(RecordType.TAX_CHECK, 1, EARLY),
                            record(RecordType.EXIT_CHECK, 1, EARLY))).totalReceivedPay(), null);
                }),
                new Scenario("recorded periods and missing amounts are sorted", () -> {
                    var sum = OverviewRules.paySummary(2026, List.of(pay(1, "2026-12", null),
                            pay(2, "2026-01", null), pay(3, "2026-06", "8")));
                    eq(sum.recordedPeriods(), List.of("2026-01", "2026-06", "2026-12"));
                    eq(sum.missingAmountPeriods(), List.of("2026-01", "2026-12"));
                }),
                new Scenario("recent order follows timestamp instead of ID", () -> {
                    var sorted = OverviewRules.newestFirst(List.of(record(RecordType.PAYCHECK, 99, EARLY),
                            record(RecordType.TAX_CHECK, 1, LATE)));
                    eq(sorted.get(0).recordKey(), "TAX_CHECK:1");
                }),
                new Scenario("same-type ties use descending ID", () -> {
                    var sorted = OverviewRules.newestFirst(List.of(record(RecordType.TAX_CHECK, 1, EARLY),
                            record(RecordType.TAX_CHECK, 2, EARLY)));
                    eq(sorted.get(0).sourceId(), 2L);
                }),
                new Scenario("cross-type ID collisions stay separate with stable order", () -> {
                    var sorted = OverviewRules.newestFirst(List.of(record(RecordType.EXIT_CHECK, 1, EARLY),
                            record(RecordType.TAX_CHECK, 1, EARLY), record(RecordType.PAYCHECK, 1, EARLY)));
                    eq(sorted.stream().map(RecordSummary::recordKey).toList(), List.of("PAYCHECK:1", "TAX_CHECK:1", "EXIT_CHECK:1"));
                }),
                new Scenario("unknown timestamps come last without inventing now", () -> {
                    var sorted = OverviewRules.newestFirst(List.of(record(RecordType.PAYCHECK, 1, null),
                            record(RecordType.EXIT_CHECK, 2, EARLY)));
                    eq(sorted.get(1).recordedAt(), null);
                }),
                new Scenario("latest type is selected from sorted all-year history", () -> {
                    var sorted = OverviewRules.newestFirst(List.of(record(RecordType.EXIT_CHECK, 3, LATE),
                            record(RecordType.TAX_CHECK, 4, EARLY), record(RecordType.TAX_CHECK, 2, LATE)));
                    eq(OverviewRules.latest(sorted, RecordType.TAX_CHECK).sourceId(), 2L);
                }),
                new Scenario("missing latest type stays null", () -> {
                    eq(OverviewRules.latest(List.of(record(RecordType.PAYCHECK, 1, EARLY)), RecordType.EXIT_CHECK), null);
                }),
                new Scenario("type filter retains total counts", () -> {
                    var all = OverviewRules.newestFirst(List.of(pay(1, "2026-01", "1"),
                            record(RecordType.TAX_CHECK, 1, EARLY), record(RecordType.TAX_CHECK, 2, LATE)));
                    var response = OverviewRules.records(all, RecordType.TAX_CHECK);
                    eq(response.items().size(), 2);
                    eq(response.counts().all(), 3L);
                    eq(response.counts().paycheck(), 1L);
                    eq(response.counts().taxCheck(), 2L);
                    eq(response.counts().exitCheck(), 0L);
                }),
                new Scenario("omitting filter returns all records", () -> {
                    var all = List.of(record(RecordType.TAX_CHECK, 1, EARLY), record(RecordType.EXIT_CHECK, 2, EARLY));
                    eq(OverviewRules.records(all, null).items(), all);
                }),
                new Scenario("empty type filter does not erase other counts", () -> {
                    var response = OverviewRules.records(List.of(pay(1, "2026-01", "1")), RecordType.EXIT_CHECK);
                    eq(response.items(), List.of());
                    eq(response.counts().all(), 1L);
                }),
                new Scenario("sorting never mutates the supplied history", () -> {
                    var source = new ArrayList<>(List.of(record(RecordType.PAYCHECK, 1, EARLY),
                            record(RecordType.EXIT_CHECK, 1, LATE)));
                    var copy = List.copyOf(source);
                    OverviewRules.newestFirst(source);
                    eq(source, copy);
                }),
                new Scenario("malformed stored periods fail explicitly", () -> {
                    for (String period : new String[]{null, "2026-1", "2026-00", "2026-13", "invalid"}) {
                        fails(() -> OverviewRules.paySummary(2026, List.of(pay(1, period, "1"))));
                    }
                }),
                new Scenario("duplicate monthly pay is not double counted", () -> {
                    fails(() -> OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "1"), pay(2, "2026-01", "2"))));
                }),
                new Scenario("invalid negative deposits are not a valid summary", () -> {
                    fails(() -> OverviewRules.paySummary(2026, List.of(pay(1, "2026-01", "-1"))));
                })
        );
    }

    public static void main(String[] args) {
        for (Scenario scenario : scenarios()) {
            scenario.verify().run();
            System.out.println("PASS: " + scenario.name());
        }
        System.out.println("Overview rules: " + scenarios().size() + " scenarios passed.");
    }

    private static RecordSummary pay(long id, String period, String amount) {
        return new RecordSummary("PAYCHECK:" + id, RecordType.PAYCHECK, id, EARLY, EARLY,
                "INSUFFICIENT_DATA", null, null, period, null, null,
                amount == null ? null : new BigDecimal(amount), null);
    }

    private static RecordSummary record(RecordType type, long id, LocalDateTime timestamp) {
        return new RecordSummary(type.name() + ":" + id, type, id, timestamp, timestamp,
                "UNKNOWN", null, null, null, type == RecordType.TAX_CHECK ? 2025 : null,
                null, null, null);
    }

    private static void eq(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void money(BigDecimal actual, String expected) {
        if (actual == null || actual.compareTo(new BigDecimal(expected)) != 0) {
            throw new AssertionError("Expected amount " + expected + ", got " + actual);
        }
    }

    private static void fails(Runnable work) {
        try {
            work.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid stored data must not produce a successful summary");
    }
}
