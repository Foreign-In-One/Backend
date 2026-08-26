package com.foreigninone.backend.domain.paycheck.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckResponse;
import com.foreigninone.backend.domain.agent.service.AiAgentService;
import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import com.foreigninone.backend.domain.bank.repository.BankTransactionRepository;
import com.foreigninone.backend.domain.calendar.service.CalendarEventService;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckAnalyzeRequest;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckExplainResponse;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckResponse;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.paycheck.rule.PaycheckRuleEngine;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaycheckService {

    private final PaycheckRepository paycheckRepository;
    private final UserRepository userRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final DocumentRepository documentRepository;
    private final PaycheckRuleEngine ruleEngine;
    private final AiAgentService aiAgentService;
    private final CalendarEventService calendarEventService;

    @Transactional(readOnly = true)
    public List<PaycheckResponse> getPaychecks(Long userId, String from, String to) {
        if (from != null && to != null) {
            return paycheckRepository.findByUser_UserIdAndPayPeriodBetweenOrderByPayPeriodDesc(userId, from, to)
                    .stream()
                    .map(PaycheckResponse::from)
                    .toList();
        }
        return paycheckRepository.findByUser_UserIdOrderByPayPeriodDesc(userId)
                .stream()
                .map(PaycheckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaycheckResponse getPaycheck(Long paycheckId) {
        Paycheck paycheck = paycheckRepository.findById(paycheckId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYCHECK_NOT_FOUND));
        return PaycheckResponse.from(paycheck);
    }

    @Transactional
    public PaycheckResponse analyzePaycheck(Long userId, PaycheckAnalyzeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String payPeriod = request.getPayPeriod();

        // 1. 문서 조회
        Document contractDoc = request.getContractDocumentId() != null
                ? documentRepository.findById(request.getContractDocumentId()).orElse(null)
                : findLatestUserDocument(userId, DocumentType.EMPLOYMENT_CONTRACT);

        Document payslipDoc = request.getPayslipDocumentId() != null
                ? documentRepository.findById(request.getPayslipDocumentId()).orElse(null)
                : findDocumentByPeriod(userId, DocumentType.PAYSLIP, payPeriod);

        Document bankReceiptDoc = request.getBankReceiptDocumentId() != null
                ? documentRepository.findById(request.getBankReceiptDocumentId()).orElse(null)
                : findDocumentByPeriod(userId, DocumentType.BANK_RECEIPT, payPeriod);

        // 2. 금융 거래 조회
        BankTransaction transaction = null;
        if (request.getTransactionId() != null) {
            transaction = bankTransactionRepository.findById(request.getTransactionId()).orElse(null);
        } else {
            transaction = findSalaryTransactionForPeriod(userId, payPeriod);
        }

        // 3. 금액 및 일자 산출
        BigDecimal contractAmount = extractAmountFromDoc(contractDoc, "baseSalary", BigDecimal.valueOf(2300000));
        BigDecimal payslipAmount = extractAmountFromDoc(payslipDoc, "netPay", null);
        if (payslipAmount == null) {
            payslipAmount = extractAmountFromDoc(payslipDoc, "baseSalary", null);
        }

        BigDecimal actualAmount = null;
        LocalDateTime actualPaymentDate = null;

        if (transaction != null) {
            actualAmount = transaction.getTranAmt();
            actualPaymentDate = transaction.getBankTranDate().atTime(
                    transaction.getTranTime() != null ? transaction.getTranTime() : java.time.LocalTime.of(9, 0));
        } else if (bankReceiptDoc != null) {
            actualAmount = extractAmountFromDoc(bankReceiptDoc, "depositAmount", null);
            actualPaymentDate = LocalDateTime.now();
        }

        // 예상 급여일 계산
        LocalDate expectedPaymentDate = calculateExpectedPaymentDate(user, payPeriod);

        // 4. 이전 달 급여 기록 조회 (전월 대비 비교용)
        YearMonth ym = YearMonth.parse(payPeriod, DateTimeFormatter.ofPattern("yyyy-MM"));
        String prevPeriod = ym.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Paycheck previousPaycheck = paycheckRepository.findByUser_UserIdAndPayPeriod(userId, prevPeriod).orElse(null);

        // 5. Rule Engine 실행
        PaycheckRuleEngine.RuleInput ruleInput = PaycheckRuleEngine.RuleInput.builder()
                .user(user)
                .payPeriod(payPeriod)
                .contractAmount(contractAmount)
                .payslipAmount(payslipAmount)
                .actualAmount(actualAmount)
                .expectedPaymentDate(expectedPaymentDate)
                .actualPaymentDate(actualPaymentDate)
                .previousPaycheck(previousPaycheck)
                .build();

        PaycheckRuleEngine.RuleResult ruleResult = ruleEngine.evaluate(ruleInput);

        // 6. 저장 또는 업데이트
        Paycheck paycheck = paycheckRepository.findByUser_UserIdAndPayPeriod(userId, payPeriod)
                .orElse(Paycheck.builder()
                        .user(user)
                        .payPeriod(payPeriod)
                        .status(ruleResult.getStatus())
                        .build());

        paycheck.updateAnalysisResult(
                contractAmount,
                payslipAmount,
                actualAmount,
                ruleResult.getDifferenceAmount(),
                expectedPaymentDate,
                actualPaymentDate,
                ruleResult.getStatus(),
                ruleResult.getAnalysisSummary(),
                ruleResult.getNextAction(),
                transaction,
                contractDoc,
                payslipDoc,
                bankReceiptDoc
        );

        Paycheck saved = paycheckRepository.save(paycheck);

        // 7. CalendarEvent 동기화 (직접 호출)
        calendarEventService.syncPaycheckEvent(saved);

        return PaycheckResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaycheckExplainResponse explainPaycheck(Long paycheckId) {
        Paycheck paycheck = paycheckRepository.findById(paycheckId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYCHECK_NOT_FOUND));

        AgentPaycheckResponse agentResponse = aiAgentService.analyzePaycheckCase(paycheckId, null);

        return PaycheckExplainResponse.builder()
                .summary(agentResponse.getSummary())
                .reasons(agentResponse.getRequiredEvidence())
                .nextActions(agentResponse.getNextActions())
                .employerQuestionCards(agentResponse.getEmployerQuestionCards())
                .build();
    }

    private Document findLatestUserDocument(Long userId, DocumentType type) {
        List<Document> docs = documentRepository.findByUser_UserIdAndDocumentType(userId, type);
        return docs.isEmpty() ? null : docs.get(docs.size() - 1);
    }

    private Document findDocumentByPeriod(Long userId, DocumentType type, String payPeriod) {
        List<Document> docs = documentRepository.findByUser_UserIdAndDocumentType(userId, type);
        for (Document doc : docs) {
            Map<String, Object> data = doc.getExtractedData();
            if (data != null && payPeriod.equals(data.get("payPeriod"))) {
                return doc;
            }
        }
        return docs.isEmpty() ? null : docs.get(docs.size() - 1);
    }

    private BankTransaction findSalaryTransactionForPeriod(Long userId, String payPeriod) {
        YearMonth ym = YearMonth.parse(payPeriod, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<BankTransaction> list = bankTransactionRepository
                .findByUser_UserIdAndBankTranDateBetweenOrderByBankTranDateDescTranTimeDesc(userId, start, end);

        return list.stream()
                .filter(tx -> "급여".equals(tx.getTranType()) ||
                        "SALARY".equals(tx.getTransactionCategory()) ||
                        (tx.getPrintedContent() != null && tx.getPrintedContent().contains("급여")))
                .findFirst()
                .orElse(list.isEmpty() ? null : list.get(0));
    }

    private BigDecimal extractAmountFromDoc(Document doc, String key, BigDecimal defaultVal) {
        if (doc == null || doc.getExtractedData() == null) {
            return defaultVal;
        }
        Object val = doc.getExtractedData().get(key);
        if (val instanceof Number) {
            return BigDecimal.valueOf(((Number) val).doubleValue());
        }
        if (val instanceof String) {
            try {
                return new BigDecimal((String) val);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private LocalDate calculateExpectedPaymentDate(User user, String payPeriod) {
        YearMonth ym = YearMonth.parse(payPeriod, DateTimeFormatter.ofPattern("yyyy-MM"));
        int day = user.getPayday() != null ? user.getPayday() : 25;
        int maxDay = ym.lengthOfMonth();
        return ym.atDay(Math.min(day, maxDay));
    }
}
