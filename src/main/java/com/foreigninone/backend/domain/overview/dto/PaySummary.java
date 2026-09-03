package com.foreigninone.backend.domain.overview.dto;

import java.math.BigDecimal;
import java.util.List;

/** Actual deposits for the requested pay-period year; never taxable/gross annual income. */
public record PaySummary(
        BigDecimal totalReceivedPay,
        int recordedMonths,
        int amountKnownMonths,
        List<String> recordedPeriods,
        List<String> missingAmountPeriods
) {
}
