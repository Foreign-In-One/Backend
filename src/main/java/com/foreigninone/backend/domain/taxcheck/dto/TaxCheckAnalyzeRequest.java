package com.foreigninone.backend.domain.taxcheck.dto;

import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Conditions;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Income;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TaxCheckAnalyzeRequest(
        @NotNull(message = "귀속연도는 필수입니다.")
        @Min(value = 2000, message = "귀속연도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "귀속연도 형식을 확인하세요.") Integer taxYear,
        @Positive(message = "문서 ID는 양수여야 합니다.") Long taxDocumentId,
        Income income,
        Conditions conditions
) {}
