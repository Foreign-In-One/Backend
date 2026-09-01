package com.foreigninone.backend.domain.exitcheck.rule;

import com.foreigninone.backend.domain.exitcheck.entity.ExitCheckStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ExitCheck 규칙 엔진.
 * ERD의 insurance_status(출국만기보험+귀국비용보험) / pension_status(국민연금 반환일시금)
 * / retirement_status(퇴직금 차액) 3개 항목과 종합 status, readiness_score를 산출한다.
 */
@Component
public class ExitCheckRuleEngine {

    private static final int[] ROADMAP_OFFSETS = {-45, -30, -20, -14, -7, 0};
    private static final String[] ROADMAP_LABELS = {
            "출국 준비 시작", "서류 준비", "정산 요청", "보험금 청구 준비", "최종 확인", "출국일"
    };
    private static final String[] ROADMAP_DETAILS = {
            "출국 45일 전, 출국 계획을 세우세요.",
            "출국 30일 전, 필요한 서류를 준비하세요.",
            "출국 20일 전, 사업주에게 정산을 요청하세요.",
            "출국 14일 전, 보험금 청구를 준비하세요.",
            "출국 7일 전, 최종 확인을 하세요.",
            "출국일, 공항에서 서류를 제출하세요."
    };

    @Getter
    @Builder
    public static class RuleInput {
        private final LocalDate workStartDate;
        private final LocalDate expectedExitDate;
        private final Boolean hasInsuranceRecord;
        private final Boolean hasOwnAccount;
        private final Boolean hasExitProof;
        private final Boolean pensionDeducted;
        private final Boolean hasRecentPayslip;
    }

    @Getter
    @Builder
    public static class RuleResult {
        private final Integer workDurationMonths;
        private final ExitCheckStatus insuranceStatus;
        private final ExitCheckStatus pensionStatus;
        private final ExitCheckStatus retirementStatus;
        private final List<String> missingDocuments;
        private final List<Map<String, Object>> checklist;
        private final Integer readinessScore;
        private final ExitCheckStatus overallStatus;
        private final String nextAction;
        private final String analysisSummary;
    }

    public RuleResult evaluate(RuleInput input) {
        Integer months = computeMonths(input.getWorkStartDate());
        boolean over12 = months != null && months >= 12;

        ExitCheckStatus insuranceStatus = evaluateInsurance(months, over12, input);
        ExitCheckStatus pensionStatus = evaluatePension(input.getPensionDeducted());
        ExitCheckStatus retirementStatus = evaluateRetirement(months, over12, input);

        List<String> missingDocuments = collectMissingDocuments(insuranceStatus, pensionStatus, retirementStatus, input);
        List<Map<String, Object>> checklist = input.getExpectedExitDate() != null
                ? buildRoadmap(input.getExpectedExitDate())
                : List.of();

        int readinessScore = computeReadinessScore(insuranceStatus, pensionStatus, retirementStatus);
        ExitCheckStatus overallStatus = computeOverallStatus(insuranceStatus, pensionStatus, retirementStatus);
        String nextAction = computeNextAction(insuranceStatus, pensionStatus, retirementStatus, over12);
        String analysisSummary = computeSummary(months, readinessScore);

        return RuleResult.builder()
                .workDurationMonths(months)
                .insuranceStatus(insuranceStatus)
                .pensionStatus(pensionStatus)
                .retirementStatus(retirementStatus)
                .missingDocuments(missingDocuments)
                .checklist(checklist)
                .readinessScore(readinessScore)
                .overallStatus(overallStatus)
                .nextAction(nextAction)
                .analysisSummary(analysisSummary)
                .build();
    }

    private Integer computeMonths(LocalDate workStartDate) {
        if (workStartDate == null) return null;
        LocalDate now = LocalDate.now();
        if (now.isBefore(workStartDate)) return 0;
        Period period = Period.between(workStartDate, now);
        return Math.max(period.getYears() * 12 + period.getMonths(), 0);
    }

    private ExitCheckStatus evaluateInsurance(Integer months, boolean over12, RuleInput input) {
        if (months == null) return ExitCheckStatus.UNKNOWN;
        if (input.getHasInsuranceRecord() == null) return ExitCheckStatus.CHECK_REQUIRED;
        if (!input.getHasInsuranceRecord()) return ExitCheckStatus.MISSING_DOCUMENT;
        if (!over12) return ExitCheckStatus.CHECK_REQUIRED;
        boolean hasOwnAccount = Boolean.TRUE.equals(input.getHasOwnAccount());
        boolean hasExitProof = Boolean.TRUE.equals(input.getHasExitProof());
        if (!hasOwnAccount || !hasExitProof) return ExitCheckStatus.MISSING_DOCUMENT;
        return ExitCheckStatus.READY;
    }

    /** 국민연금은 공제 이력이 없으면(false) 반환 대상 자체가 아니므로 UNKNOWN(판단 불가/해당 없음)으로 둔다. */
    private ExitCheckStatus evaluatePension(Boolean pensionDeducted) {
        if (pensionDeducted == null) return ExitCheckStatus.CHECK_REQUIRED;
        if (!pensionDeducted) return ExitCheckStatus.UNKNOWN;
        return ExitCheckStatus.MISSING_DOCUMENT;
    }

    private ExitCheckStatus evaluateRetirement(Integer months, boolean over12, RuleInput input) {
        if (months == null) return ExitCheckStatus.UNKNOWN;
        if (!over12) return ExitCheckStatus.CHECK_REQUIRED;
        if (!Boolean.TRUE.equals(input.getHasRecentPayslip())) return ExitCheckStatus.MISSING_DOCUMENT;
        return ExitCheckStatus.READY;
    }

    private List<String> collectMissingDocuments(ExitCheckStatus insuranceStatus, ExitCheckStatus pensionStatus,
                                                   ExitCheckStatus retirementStatus, RuleInput input) {
        List<String> missing = new ArrayList<>();
        if (insuranceStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            if (!Boolean.TRUE.equals(input.getHasInsuranceRecord())) missing.add("출국만기보험 가입 여부 확인 필요");
            if (!Boolean.TRUE.equals(input.getHasOwnAccount())) missing.add("본인 명의 계좌 확인 필요");
            if (!Boolean.TRUE.equals(input.getHasExitProof())) missing.add("출국 증빙 서류 필요");
        }
        if (pensionStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            missing.add("국민연금 납부확인서 필요");
            missing.add("출국 확인 서류 필요");
        }
        if (retirementStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            missing.add("최근 임금명세서 필요");
        }
        return missing;
    }

    private List<Map<String, Object>> buildRoadmap(LocalDate expectedExitDate) {
        List<Map<String, Object>> roadmap = new ArrayList<>();
        for (int i = 0; i < ROADMAP_OFFSETS.length; i++) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("date", expectedExitDate.plusDays(ROADMAP_OFFSETS[i]).toString());
            step.put("label", ROADMAP_LABELS[i]);
            step.put("detail", ROADMAP_DETAILS[i]);
            roadmap.add(step);
        }
        return roadmap;
    }

    private int computeReadinessScore(ExitCheckStatus insuranceStatus, ExitCheckStatus pensionStatus, ExitCheckStatus retirementStatus) {
        List<ExitCheckStatus> considered = new ArrayList<>();
        for (ExitCheckStatus s : List.of(insuranceStatus, pensionStatus, retirementStatus)) {
            if (s != ExitCheckStatus.UNKNOWN) considered.add(s);
        }
        if (considered.isEmpty()) return 0;
        long readyCount = considered.stream().filter(s -> s == ExitCheckStatus.READY).count();
        return (int) Math.round((readyCount * 100.0) / considered.size());
    }

    private ExitCheckStatus computeOverallStatus(ExitCheckStatus insuranceStatus, ExitCheckStatus pensionStatus, ExitCheckStatus retirementStatus) {
        List<ExitCheckStatus> all = List.of(insuranceStatus, pensionStatus, retirementStatus);
        if (all.stream().allMatch(s -> s == ExitCheckStatus.UNKNOWN)) return ExitCheckStatus.UNKNOWN;
        if (all.contains(ExitCheckStatus.MISSING_DOCUMENT)) return ExitCheckStatus.MISSING_DOCUMENT;
        if (all.contains(ExitCheckStatus.CHECK_REQUIRED)) return ExitCheckStatus.CHECK_REQUIRED;
        boolean allReadyOrUnknown = all.stream().allMatch(s -> s == ExitCheckStatus.READY || s == ExitCheckStatus.UNKNOWN);
        if (allReadyOrUnknown) return ExitCheckStatus.READY;
        return ExitCheckStatus.IN_PROGRESS;
    }

    private String computeNextAction(ExitCheckStatus insuranceStatus, ExitCheckStatus pensionStatus,
                                      ExitCheckStatus retirementStatus, boolean over12) {
        if (insuranceStatus == ExitCheckStatus.CHECK_REQUIRED || insuranceStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            return over12
                    ? "출국만기보험·귀국비용보험 가입 여부와 필요 서류를 확인하세요."
                    : "12개월 미만 근무 시 조건을 다시 확인하세요.";
        }
        if (pensionStatus == ExitCheckStatus.CHECK_REQUIRED || pensionStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            return "국민연금공단에 반환일시금을 신청하세요.";
        }
        if (retirementStatus == ExitCheckStatus.CHECK_REQUIRED || retirementStatus == ExitCheckStatus.MISSING_DOCUMENT) {
            return "최근 3개월 임금명세서로 평균임금을 계산해 퇴직금을 확인하세요.";
        }
        return "출국 전 정산 준비가 완료됐습니다.";
    }

    private String computeSummary(Integer months, int readinessScore) {
        String monthsText = months == null ? "근속 개월수를 확인할 수 없습니다." : String.format("총 근속 개월수는 약 %d개월입니다.", months);
        return String.format("%s 출국 전 정산 준비도는 %d%%입니다.", monthsText, readinessScore);
    }
}
