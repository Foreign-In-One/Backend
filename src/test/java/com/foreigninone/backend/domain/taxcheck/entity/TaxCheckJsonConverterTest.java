package com.foreigninone.backend.domain.taxcheck.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxCheckJsonConverterTest {
    private final TaxCheckJsonConverter converter = new TaxCheckJsonConverter();

    @Test
    void moneyRoundTripRetainsBigDecimal() {
        BigDecimal amount = new BigDecimal("9999999999999.99");
        Map<String, Object> restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(Map.of("amount", amount)));
        assertThat(restored.get("amount")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) restored.get("amount")).isEqualByComparingTo(amount);
    }

    @Test
    void malformedSnapshotIsNotSilentlyAnEmptyMap() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{broken")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesH2EscapedJson() {
        assertThat(converter.convertToEntityAttribute("\"{\\\"amount\\\":1.25}\"").get("amount"))
                .isEqualTo(new BigDecimal("1.25"));
    }
}
