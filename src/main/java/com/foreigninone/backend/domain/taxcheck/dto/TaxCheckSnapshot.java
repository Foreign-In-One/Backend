package com.foreigninone.backend.domain.taxcheck.dto;

import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Conditions;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Context;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Income;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.Result;

/** Persisted input/result snapshot, not a live join of the current Paycheck totals. */
public record TaxCheckSnapshot(int schemaVersion, Income income, Conditions conditions,
                               Context context, Result result) {}
