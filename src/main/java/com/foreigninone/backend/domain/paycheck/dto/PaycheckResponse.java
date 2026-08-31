package com.foreigninone.backend.domain.paycheck.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaycheckResponse {
    private Long paycheckId;
    private String payPeriod;
    private BigDecimal contractAmount;
    private BigDecimal payslipAmount;
    private BigDecimal actualAmount;
    private BigDecimal differenceAmount;
    private LocalDate expectedPaymentDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paymentDate;

    private PaycheckStatus status;
    private String analysisSummary;
    private String nextAction;

    public static PaycheckResponse from(Paycheck paycheck) {
        return PaycheckResponse.builder()
                .paycheckId(paycheck.getPaycheckId())
                .payPeriod(paycheck.getPayPeriod())
                .contractAmount(paycheck.getContractAmount())
                .payslipAmount(paycheck.getPayslipAmount())
                .actualAmount(paycheck.getActualAmount())
                .differenceAmount(paycheck.getDifferenceAmount())
                .expectedPaymentDate(paycheck.getExpectedPaymentDate())
                .paymentDate(paycheck.getPaymentDate())
                .status(paycheck.getStatus())
                .analysisSummary(paycheck.getAnalysisSummary())
                .nextAction(paycheck.getNextAction())
                .build();
    }
}
