package com.foreigninone.backend.domain.exitcheck.dto;

import com.foreigninone.backend.domain.exitcheck.entity.ExitCheck;
import com.foreigninone.backend.domain.exitcheck.entity.ExitCheckStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExitCheckResponse {
    private Long exitCheckId;
    private LocalDate expectedExitDate;
    private Integer workDurationMonths;
    private ExitCheckStatus insuranceStatus;
    private ExitCheckStatus pensionStatus;
    private ExitCheckStatus retirementStatus;
    private List<String> missingDocuments;
    private List<Map<String, Object>> checklist;
    private Integer readinessScore;
    private ExitCheckStatus status;
    private String nextAction;
    private String analysisSummary;
    private LocalDateTime analyzedAt;

    public static ExitCheckResponse from(ExitCheck exitCheck) {
        return ExitCheckResponse.builder()
                .exitCheckId(exitCheck.getExitCheckId())
                .expectedExitDate(exitCheck.getExpectedExitDate())
                .workDurationMonths(exitCheck.getWorkDurationMonths())
                .insuranceStatus(exitCheck.getInsuranceStatus())
                .pensionStatus(exitCheck.getPensionStatus())
                .retirementStatus(exitCheck.getRetirementStatus())
                .missingDocuments(exitCheck.getMissingDocuments())
                .checklist(exitCheck.getChecklist())
                .readinessScore(exitCheck.getReadinessScore())
                .status(exitCheck.getStatus())
                .nextAction(exitCheck.getNextAction())
                .analysisSummary(exitCheck.getAnalysisSummary())
                .analyzedAt(exitCheck.getAnalyzedAt())
                .build();
    }
}
