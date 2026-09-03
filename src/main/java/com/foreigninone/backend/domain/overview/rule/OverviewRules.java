package com.foreigninone.backend.domain.overview.rule;

import com.foreigninone.backend.domain.overview.dto.PaySummary;
import com.foreigninone.backend.domain.overview.dto.RecordSummary;
import com.foreigninone.backend.domain.overview.dto.RecordType;
import com.foreigninone.backend.domain.overview.dto.RecordsResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public final class OverviewRules {
    private OverviewRules() {
    }

    // Ties are deterministic even when IDs overlap between the three tables.
    private static final Comparator<RecordSummary> NEWEST_FIRST = Comparator
            .comparing(RecordSummary::recordedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(RecordSummary::type)
            .thenComparing(RecordSummary::sourceId, Comparator.reverseOrder());

    public static List<RecordSummary> newestFirst(List<RecordSummary> records) {
        return records.stream().sorted(NEWEST_FIRST).toList();
    }

    public static RecordSummary latest(List<RecordSummary> sortedRecords, RecordType type) {
        return sortedRecords.stream().filter(record -> record.type() == type).findFirst().orElse(null);
    }

    public static RecordsResponse records(List<RecordSummary> sortedRecords, RecordType filter) {
        long pay = sortedRecords.stream().filter(record -> record.type() == RecordType.PAYCHECK).count();
        long tax = sortedRecords.stream().filter(record -> record.type() == RecordType.TAX_CHECK).count();
        long exit = sortedRecords.stream().filter(record -> record.type() == RecordType.EXIT_CHECK).count();
        var items = sortedRecords.stream().filter(record -> filter == null || record.type() == filter).toList();
        return new RecordsResponse(items, new RecordsResponse.Counts(pay + tax + exit, pay, tax, exit));
    }

    public static PaySummary paySummary(int year, List<RecordSummary> records) {
        var byPeriod = new TreeMap<String, RecordSummary>();
        for (RecordSummary record : records) {
            if (record.type() != RecordType.PAYCHECK) continue;
            YearMonth period = parseStoredPeriod(record.payPeriod());
            if (period.getYear() != year) continue;
            if (byPeriod.putIfAbsent(record.payPeriod(), record) != null) {
                throw new IllegalStateException("동일 사용자·급여월의 중복 Paycheck를 확인하세요.");
            }
        }
        BigDecimal total = null;
        int known = 0;
        var missing = new ArrayList<String>();
        for (RecordSummary record : byPeriod.values()) {
            BigDecimal actual = record.actualAmount();
            if (actual == null) {
                missing.add(record.payPeriod());
            } else {
                if (actual.signum() < 0) throw new IllegalStateException("저장된 실입금액을 확인하세요.");
                total = total == null ? actual : total.add(actual);
                known++;
            }
        }
        return new PaySummary(total, byPeriod.size(), known, List.copyOf(byPeriod.keySet()), List.copyOf(missing));
    }

    private static YearMonth parseStoredPeriod(String raw) {
        // Invalid stored data is a server-data error, not a missing amount or a client-input error.
        if (raw == null || !raw.matches("[0-9]{4}-[0-9]{2}")) {
            throw new IllegalStateException("저장된 급여월은 YYYY-MM 형식이어야 합니다.");
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("저장된 급여월을 확인하세요.", e);
        }
    }
}
