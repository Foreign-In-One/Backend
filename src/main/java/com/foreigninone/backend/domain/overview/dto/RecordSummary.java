package com.foreigninone.backend.domain.overview.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A read-only projection, not a new saved result or a recalculated analysis. */
public record RecordSummary(
        String recordKey,
        RecordType type,
        Long sourceId,
        LocalDateTime recordedAt,
        LocalDateTime analyzedAt,
        String status,
        String analysisSummary,
        String nextAction,
        String payPeriod,
        Integer taxYear,
        LocalDate expectedExitDate,
        BigDecimal actualAmount,
        Integer readinessScore
) {
}
