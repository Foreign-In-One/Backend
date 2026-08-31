package com.foreigninone.backend.domain.paycheck.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaycheckAnalyzeRequest {

    @NotBlank(message = "급여 기간(YYYY-MM)은 필수입니다.")
    private String payPeriod;

    private Long transactionId;
    private Long contractDocumentId;
    private Long payslipDocumentId;
    private Long bankReceiptDocumentId;

    private java.math.BigDecimal contractAmount;
    private java.math.BigDecimal payslipAmount;
    private java.math.BigDecimal actualAmount;
    private java.math.BigDecimal differenceAmount;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private java.time.LocalDate expectedPaymentDate;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private java.time.LocalDateTime paymentDate;
}
