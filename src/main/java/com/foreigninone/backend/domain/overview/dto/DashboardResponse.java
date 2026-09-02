package com.foreigninone.backend.domain.overview.dto;

import java.util.List;

public record DashboardResponse(
        int year,
        PaySummary paySummary,
        RecordSummary latestPaycheck,
        RecordSummary latestTaxCheck,
        RecordSummary latestExitCheck,
        List<RecordSummary> recentRecords
) {
}
