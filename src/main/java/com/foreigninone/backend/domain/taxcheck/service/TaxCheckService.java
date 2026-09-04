package com.foreigninone.backend.domain.taxcheck.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.calendar.service.CalendarEventService;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.taxcheck.dto.*;
import com.foreigninone.backend.domain.taxcheck.entity.TaxCheck;
import com.foreigninone.backend.domain.taxcheck.entity.TaxCheckStatus;
import com.foreigninone.backend.domain.taxcheck.repository.TaxCheckRepository;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules;
import com.foreigninone.backend.domain.taxcheck.rule.TaxCheckRules.*;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxCheckService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final TaxCheckRepository taxCheckRepository;
    private final UserRepository userRepository;
    private final PaycheckRepository paycheckRepository;
    private final DocumentRepository documentRepository;
    private final CalendarEventService calendarEventService;
    private final ObjectMapper objectMapper;

    public List<TaxCheckResponse> getTaxChecks(Long userId, Integer taxYear) {
        requireUser(userId);
        if (taxYear != null) validateYear(taxYear);
        List<TaxCheck> records = taxYear == null
                ? taxCheckRepository.findByUser_UserIdOrderByAnalyzedAtDescTaxCheckIdDesc(userId)
                : taxCheckRepository.findByUser_UserIdAndTaxYearOrderByAnalyzedAtDescTaxCheckIdDesc(userId, taxYear);
        return records.stream().map(this::storedResponse).toList();
    }

    public TaxCheckResponse getTaxCheck(Long taxCheckId, Long userId) {
        requireUser(userId);
        return storedResponse(requireTaxCheck(taxCheckId, userId));
    }

    @Transactional
    public TaxCheckResponse analyze(Long userId, TaxCheckAnalyzeRequest request) {
        if (request == null || request.taxYear() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "귀속연도는 필수입니다.");
        }
        validateYear(request.taxYear());
        User user = requireUser(userId);
        Document document = requireDocument(request.taxDocumentId(), userId);
        List<PayRecord> payRecords = paycheckRepository
                .findByUser_UserIdAndPayPeriodBetweenOrderByPayPeriodDesc(userId,
                        request.taxYear() + "-01", request.taxYear() + "-12")
                .stream().map(paycheck -> new PayRecord(paycheck.getPayPeriod(), paycheck.getActualAmount())).toList();
        Context context = new Context(request.taxYear(), user.getEntryDate(), user.getEmploymentStatus(),
                LocalDate.now(KOREA), TaxCheckRules.summarizePay(request.taxYear(), payRecords));
        Result result = TaxCheckRules.evaluate(context, request.income(), request.conditions());
        TaxCheckSnapshot snapshot = new TaxCheckSnapshot(1, request.income(), request.conditions(), context, result);
        Map<String, Object> benefitSummary = objectMapper.convertValue(snapshot, new TypeReference<Map<String, Object>>() {});
        TaxCheck saved = taxCheckRepository.save(TaxCheck.builder()
                .user(user).taxDocument(document).taxYear(request.taxYear())
                .residentStatus(result.residentStatus()).annualIncome(result.annualIncome())
                .flatTaxEstimate(result.flatTaxEstimate()).generalTaxEstimate(null).taxDifference(null)
                .benefitSummary(benefitSummary)
                .requiredDocuments(Map.of("items", result.requiredDocuments()))
                .status(TaxCheckStatus.valueOf(result.status()))
                .analysisSummary(result.analysisSummary()).nextAction(result.nextAction())
                .analyzedAt(LocalDateTime.now(KOREA)).build());
        calendarEventService.syncTaxCheckEvent(saved);
        return response(saved, snapshot, false, result);
    }

    /** Uses the saved input/context snapshot and never modifies a managed entity. */
    public TaxCheckResponse simulate(Long taxCheckId, Long userId, TaxCheckSimulateRequest request) {
        requireUser(userId);
        TaxCheck original = requireTaxCheck(taxCheckId, userId);
        TaxCheckSnapshot snapshot = readSnapshot(original);
        if (!TaxCheckRules.VERSION.equals(snapshot.result().calculation().ruleVersion())) {
            throw new BusinessException(ErrorCode.TAXCHECK_SNAPSHOT_INVALID);
        }
        Income income = request != null && request.income() != null ? request.income() : snapshot.income();
        Conditions conditions = request != null && request.conditions() != null ? request.conditions() : snapshot.conditions();
        Result result = TaxCheckRules.evaluate(snapshot.context(), income, conditions);
        TaxCheckSnapshot scenario = new TaxCheckSnapshot(1, income, conditions, snapshot.context(), result);
        return response(original, scenario, true, result);
    }

    private TaxCheckResponse storedResponse(TaxCheck record) {
        TaxCheckSnapshot snapshot = readSnapshot(record);
        return response(record, snapshot, false, snapshot.result());
    }

    private TaxCheckResponse response(TaxCheck record, TaxCheckSnapshot snapshot, boolean simulation, Result result) {
        return new TaxCheckResponse(simulation ? null : record.getTaxCheckId(),
                simulation ? record.getTaxCheckId() : null, simulation, record.getTaxYear(),
                record.getTaxDocument() == null ? null : record.getTaxDocument().getDocumentId(),
                snapshot.income(), snapshot.conditions(), snapshot.context().paySummary(), result,
                // For simulations this identifies the unchanged source analysis timestamp.
                record.getAnalyzedAt());
    }

    private TaxCheckSnapshot readSnapshot(TaxCheck record) {
        try {
            TaxCheckSnapshot snapshot = objectMapper.convertValue(record.getBenefitSummary(), TaxCheckSnapshot.class);
            if (snapshot == null || snapshot.schemaVersion() != 1 || snapshot.context() == null
                    || snapshot.context().paySummary() == null || snapshot.context().assessedOn() == null
                    || snapshot.context().taxYear() != record.getTaxYear()
                    || snapshot.result() == null || snapshot.result().calculation() == null) {
                throw new BusinessException(ErrorCode.TAXCHECK_SNAPSHOT_INVALID);
            }
            return snapshot;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TAXCHECK_SNAPSHOT_INVALID);
        }
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "사용자 ID는 양수여야 합니다.");
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private TaxCheck requireTaxCheck(Long taxCheckId, Long userId) {
        if (taxCheckId == null || taxCheckId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "분석 ID는 양수여야 합니다.");
        return taxCheckRepository.findByTaxCheckIdAndUser_UserId(taxCheckId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAXCHECK_NOT_FOUND));
    }

    private Document requireDocument(Long documentId, Long userId) {
        if (documentId == null) return null;
        if (documentId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "문서 ID는 양수여야 합니다.");
        Document document = documentRepository.findById(documentId)
                .filter(value -> value.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (document.getDocumentType() != DocumentType.TAX_DOCUMENT && document.getDocumentType() != DocumentType.PAYSLIP) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "세금 문서 또는 급여명세서만 연결할 수 있습니다.");
        }
        // OCR SUCCESS is not user confirmation; extractedData is intentionally never read here.
        return document;
    }

    private void validateYear(int taxYear) {
        if (taxYear < 2000 || taxYear > LocalDate.now(KOREA).getYear()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "귀속연도는 2000년부터 현재 연도까지 입력하세요.");
        }
    }
}
