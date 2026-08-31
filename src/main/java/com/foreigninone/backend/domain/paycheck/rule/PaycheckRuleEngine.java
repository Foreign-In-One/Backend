package com.foreigninone.backend.domain.paycheck.rule;

import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import com.foreigninone.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class PaycheckRuleEngine {

    @Getter
    @Builder
    public static class RuleInput {
        private final User user;
        private final String payPeriod;
        private final BigDecimal contractAmount;
        private final BigDecimal payslipAmount;
        private final BigDecimal actualAmount;
        private final LocalDate expectedPaymentDate;
        private final LocalDateTime actualPaymentDate;
        private final Paycheck previousPaycheck;
    }

    @Getter
    @Builder
    public static class RuleResult {
        private final PaycheckStatus status;
        private final PaycheckCaseType caseType;
        private final BigDecimal differenceAmount;
        private final String analysisSummary;
        private final String nextAction;
    }

    public RuleResult evaluate(RuleInput input) {
        BigDecimal actual = input.getActualAmount();
        BigDecimal payslip = input.getPayslipAmount();
        BigDecimal contract = input.getContractAmount();
        LocalDate expectedDate = input.getExpectedPaymentDate();
        LocalDateTime actualDate = input.getActualPaymentDate();

        // 1. 입금 내역이 전혀 없는 경우
        if (actual == null || actual.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate now = LocalDate.now();
            boolean isDelayed = expectedDate != null && now.isAfter(expectedDate.plusDays(3));
            PaycheckCaseType caseType = isDelayed ? PaycheckCaseType.NOT_RECEIVED : PaycheckCaseType.NORMAL;
            PaycheckStatus status = isDelayed ? PaycheckStatus.NOT_RECEIVED : PaycheckStatus.INSUFFICIENT_DATA;

            return RuleResult.builder()
                    .status(status)
                    .caseType(caseType)
                    .differenceAmount(BigDecimal.ZERO)
                    .analysisSummary("해당 급여 기간의 입금 내역이 아직 확인되지 않았습니다.")
                    .nextAction("급여 통장 거래 내역 또는 입금 확인증을 등록하세요.")
                    .build();
        }

        // 2. 임금명세서가 없는 경우 (입금만 감지)
        if (payslip == null) {
            BigDecimal diffWithContract = contract != null ? actual.subtract(contract) : BigDecimal.ZERO;
            return RuleResult.builder()
                    .status(PaycheckStatus.INSUFFICIENT_DATA)
                    .caseType(PaycheckCaseType.UNKNOWN)
                    .differenceAmount(diffWithContract)
                    .analysisSummary("급여 입금은 확인되었으나 대조할 임금명세서가 없습니다.")
                    .nextAction("이번 달 임금명세서를 업로드하여 상세 공제 내역을 확인하세요.")
                    .build();
        }

        // 3. 임금명세서와 실입금액 대조
        BigDecimal diff = actual.subtract(payslip);

        // 이전 월 대비 급여 감소 확인
        boolean hasMoMDecrease = false;
        long momDecreaseAmount = 0L;
        if (input.getPreviousPaycheck() != null && input.getPreviousPaycheck().getActualAmount() != null) {
            BigDecimal prevActual = input.getPreviousPaycheck().getActualAmount();
            if (actual.compareTo(prevActual) < 0) {
                hasMoMDecrease = true;
                momDecreaseAmount = prevActual.subtract(actual).longValue();
            }
        }

        // 날짜 지연 여부 확인
        boolean isPaymentDelayed = false;
        if (expectedDate != null && actualDate != null) {
            if (actualDate.toLocalDate().isAfter(expectedDate)) {
                isPaymentDelayed = true;
            }
        }

        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            if (isPaymentDelayed) {
                return RuleResult.builder()
                        .status(PaycheckStatus.CONFIRMATION_REQUIRED)
                        .caseType(PaycheckCaseType.PAYMENT_DELAY)
                        .differenceAmount(diff)
                        .analysisSummary("임금명세서 실지급액과 입금액은 일치하나, 계약상 급여일보다 늦게 입금되었습니다.")
                        .nextAction("지연 사유를 사업주 또는 급여 담당자에게 확인하세요.")
                        .build();
            } else if (hasMoMDecrease) {
                return RuleResult.builder()
                        .status(PaycheckStatus.NORMAL)
                        .caseType(PaycheckCaseType.NORMAL)
                        .differenceAmount(diff)
                        .analysisSummary(String.format("임금명세서와 실제 입금액이 일치합니다. (지난달 대비 %,d원 감소)", momDecreaseAmount))
                        .nextAction("특이사항이 없습니다.")
                        .build();
            } else {
                return RuleResult.builder()
                        .status(PaycheckStatus.NORMAL)
                        .caseType(PaycheckCaseType.NORMAL)
                        .differenceAmount(diff)
                        .analysisSummary("임금명세서 실지급액과 실제 입금액이 일치합니다.")
                        .nextAction("특이사항이 없습니다.")
                        .build();
            }
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            long absDiff = diff.abs().longValue();
            return RuleResult.builder()
                    .status(PaycheckStatus.EXPLANATION_REQUIRED)
                    .caseType(PaycheckCaseType.SALARY_DECREASE)
                    .differenceAmount(diff)
                    .analysisSummary(String.format("임금명세서 실지급액과 실제 입금액에서 %,d원의 차이가 확인되었습니다.", absDiff))
                    .nextAction("이번 달 임금명세서의 공제 및 별도 지급 여부를 확인하세요.")
                    .build();
        } else {
            long overDiff = diff.longValue();
            return RuleResult.builder()
                    .status(PaycheckStatus.CONFIRMATION_REQUIRED)
                    .caseType(PaycheckCaseType.NORMAL)
                    .differenceAmount(diff)
                    .analysisSummary(String.format("통장 입금액이 임금명세서 실지급액보다 %,d원 더 많습니다.", overDiff))
                    .nextAction("추가 수당이나 정산금이 포함되었는지 확인하세요.")
                    .build();
        }
    }
}
