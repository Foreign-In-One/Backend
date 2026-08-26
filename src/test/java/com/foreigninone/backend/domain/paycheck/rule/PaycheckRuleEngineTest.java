package com.foreigninone.backend.domain.paycheck.rule;

import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import com.foreigninone.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaycheckRuleEngineTest {

    private PaycheckRuleEngine ruleEngine;
    private User testUser;

    @BeforeEach
    void setUp() {
        ruleEngine = new PaycheckRuleEngine();
        testUser = User.builder()
                .userId(1L)
                .name("민수")
                .payday(25)
                .build();
    }

    @Test
    @DisplayName("정상 급여 대조: 명세서 238만원, 실입금 238만원 -> NORMAL")
    void testNormalSalary() {
        PaycheckRuleEngine.RuleInput input = PaycheckRuleEngine.RuleInput.builder()
                .user(testUser)
                .payPeriod("2026-07")
                .contractAmount(BigDecimal.valueOf(2300000))
                .payslipAmount(BigDecimal.valueOf(2380000))
                .actualAmount(BigDecimal.valueOf(2380000))
                .expectedPaymentDate(LocalDate.of(2026, 7, 25))
                .actualPaymentDate(LocalDateTime.of(2026, 7, 25, 9, 10))
                .build();

        PaycheckRuleEngine.RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.getStatus()).isEqualTo(PaycheckStatus.NORMAL);
        assertThat(result.getCaseType()).isEqualTo(PaycheckCaseType.NORMAL);
        assertThat(result.getDifferenceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("급여 감소 이상징후: 명세서 238만원, 실입금 226만원 -> EXPLANATION_REQUIRED, 차액 -12만원")
    void testSalaryDecrease() {
        PaycheckRuleEngine.RuleInput input = PaycheckRuleEngine.RuleInput.builder()
                .user(testUser)
                .payPeriod("2026-08")
                .contractAmount(BigDecimal.valueOf(2300000))
                .payslipAmount(BigDecimal.valueOf(2380000))
                .actualAmount(BigDecimal.valueOf(2260000))
                .expectedPaymentDate(LocalDate.of(2026, 8, 25))
                .actualPaymentDate(LocalDateTime.of(2026, 8, 25, 9, 14))
                .build();

        PaycheckRuleEngine.RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.getStatus()).isEqualTo(PaycheckStatus.EXPLANATION_REQUIRED);
        assertThat(result.getCaseType()).isEqualTo(PaycheckCaseType.SALARY_DECREASE);
        assertThat(result.getDifferenceAmount()).isEqualByComparingTo(BigDecimal.valueOf(-120000));
        assertThat(result.getAnalysisSummary()).contains("120,000원의 차이");
    }

    @Test
    @DisplayName("임금명세서 누락: 실입금 226만원만 존재 -> INSUFFICIENT_DATA")
    void testMissingPayslip() {
        PaycheckRuleEngine.RuleInput input = PaycheckRuleEngine.RuleInput.builder()
                .user(testUser)
                .payPeriod("2026-08")
                .contractAmount(BigDecimal.valueOf(2300000))
                .payslipAmount(null)
                .actualAmount(BigDecimal.valueOf(2260000))
                .expectedPaymentDate(LocalDate.of(2026, 8, 25))
                .actualPaymentDate(LocalDateTime.of(2026, 8, 25, 9, 14))
                .build();

        PaycheckRuleEngine.RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.getStatus()).isEqualTo(PaycheckStatus.INSUFFICIENT_DATA);
        assertThat(result.getAnalysisSummary()).contains("대조할 임금명세서가 없습니다");
    }

    @Test
    @DisplayName("급여일 지연: 예정일 25일, 입금일 28일 -> PAYMENT_DELAY")
    void testPaymentDelayed() {
        PaycheckRuleEngine.RuleInput input = PaycheckRuleEngine.RuleInput.builder()
                .user(testUser)
                .payPeriod("2026-08")
                .contractAmount(BigDecimal.valueOf(2300000))
                .payslipAmount(BigDecimal.valueOf(2300000))
                .actualAmount(BigDecimal.valueOf(2300000))
                .expectedPaymentDate(LocalDate.of(2026, 8, 25))
                .actualPaymentDate(LocalDateTime.of(2026, 8, 28, 9, 0))
                .build();

        PaycheckRuleEngine.RuleResult result = ruleEngine.evaluate(input);

        assertThat(result.getCaseType()).isEqualTo(PaycheckCaseType.PAYMENT_DELAY);
        assertThat(result.getAnalysisSummary()).contains("늦게 입금");
    }
}
