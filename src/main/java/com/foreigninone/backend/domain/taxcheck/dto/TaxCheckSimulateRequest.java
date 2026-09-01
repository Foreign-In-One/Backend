package com.foreigninone.backend.domain.taxcheck.dto;

import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Conditions;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Income;

/** Omitted/null groups reuse the original; supplied groups replace the whole group. */
public record TaxCheckSimulateRequest(Income income, Conditions conditions) {}
