package com.foreigninone.backend.domain.exitcheck.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExitCheckAnalyzeRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expectedExitDate;

    private Long exitDocumentId;

    private Boolean hasInsuranceRecord;
    private Boolean hasOwnAccount;
    private Boolean hasExitProof;
    private Boolean pensionDeducted;
    private Boolean hasRecentPayslip;
}
