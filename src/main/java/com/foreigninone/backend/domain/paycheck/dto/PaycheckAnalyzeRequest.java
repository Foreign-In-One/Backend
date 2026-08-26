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
}
