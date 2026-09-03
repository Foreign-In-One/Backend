package com.foreigninone.backend.domain.overview.dto;

import java.util.List;

public record RecordsResponse(List<RecordSummary> items, Counts counts) {
    /** Counts are for all types, independent of the selected type filter. */
    public record Counts(long all, long paycheck, long taxCheck, long exitCheck) {
    }
}
