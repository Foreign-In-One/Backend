package com.foreigninone.backend.domain.overview;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class OverviewRulesTest {
    @TestFactory
    Stream<DynamicTest> scenarios() {
        return OverviewRulesScenarios.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), scenario.verify()::run));
    }
}
