package com.foreigninone.backend.domain.exitcheck.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.exitcheck.dto.ExitCheckAnalyzeRequest;
import com.foreigninone.backend.domain.exitcheck.dto.ExitCheckResponse;
import com.foreigninone.backend.domain.exitcheck.entity.ExitCheck;
import com.foreigninone.backend.domain.exitcheck.repository.ExitCheckRepository;
import com.foreigninone.backend.domain.exitcheck.rule.ExitCheckRuleEngine;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitCheckService {

    private final ExitCheckRepository exitCheckRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ExitCheckRuleEngine ruleEngine;

    @Transactional(readOnly = true)
    public List<ExitCheckResponse> getExitChecks(Long userId) {
        return exitCheckRepository.findByUser_UserIdOrderByAnalyzedAtDesc(userId)
                .stream()
                .map(ExitCheckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExitCheckResponse getExitCheck(Long exitCheckId, Long userId) {
        ExitCheck exitCheck = (userId != null)
                ? exitCheckRepository.findByExitCheckIdAndUser_UserId(exitCheckId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EXIT_CHECK_NOT_FOUND))
                : exitCheckRepository.findById(exitCheckId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EXIT_CHECK_NOT_FOUND));
        return ExitCheckResponse.from(exitCheck);
    }

    @Transactional
    public ExitCheckResponse analyzeExitCheck(Long userId, ExitCheckAnalyzeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate expectedExitDate = request.getExpectedExitDate() != null
                ? request.getExpectedExitDate()
                : user.getExpectedExitDate();

        Document exitDocument = request.getExitDocumentId() != null
                ? documentRepository.findById(request.getExitDocumentId()).orElse(null)
                : null;

        ExitCheckRuleEngine.RuleInput ruleInput = ExitCheckRuleEngine.RuleInput.builder()
                .workStartDate(user.getWorkStartDate())
                .expectedExitDate(expectedExitDate)
                .hasInsuranceRecord(request.getHasInsuranceRecord())
                .hasOwnAccount(request.getHasOwnAccount())
                .hasExitProof(request.getHasExitProof())
                .pensionDeducted(request.getPensionDeducted())
                .hasRecentPayslip(request.getHasRecentPayslip())
                .build();

        ExitCheckRuleEngine.RuleResult ruleResult = ruleEngine.evaluate(ruleInput);

        ExitCheck exitCheck = exitCheckRepository.findFirstByUser_UserIdOrderByAnalyzedAtDesc(userId)
                .orElse(ExitCheck.builder()
                        .user(user)
                        .status(ruleResult.getOverallStatus())
                        .build());

        exitCheck.updateAnalysisResult(
                expectedExitDate,
                ruleResult.getWorkDurationMonths(),
                ruleResult.getInsuranceStatus(),
                ruleResult.getPensionStatus(),
                ruleResult.getRetirementStatus(),
                ruleResult.getMissingDocuments(),
                ruleResult.getChecklist(),
                ruleResult.getReadinessScore(),
                ruleResult.getOverallStatus(),
                ruleResult.getNextAction(),
                ruleResult.getAnalysisSummary(),
                exitDocument
        );

        ExitCheck saved = exitCheckRepository.save(exitCheck);
        return ExitCheckResponse.from(saved);
    }
}
