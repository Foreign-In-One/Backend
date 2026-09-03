package com.foreigninone.backend.domain.taxcheck.dto;

import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Conditions;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Income;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.PaySummary;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Result;

import java.time.LocalDateTime;

public record TaxCheckResponse(Long taxCheckId, Long sourceTaxCheckId, boolean simulation,
                               int taxYear, Long taxDocumentId, Income income,
                               Conditions conditions, PaySummary paySummary, Result result,
                               LocalDateTime analyzedAt) {}
