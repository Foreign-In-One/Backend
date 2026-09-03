package com.foreigninone.backend.domain.taxcheck.rule;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TaxCheckRulesTest {
    @TestFactory
    Stream<DynamicTest> ruleScenarios() {
        return TaxCheckRulesScenarios.cases().entrySet().stream()
                .map(test -> DynamicTest.dynamicTest(test.getKey(), test.getValue()::run));
    }
}
