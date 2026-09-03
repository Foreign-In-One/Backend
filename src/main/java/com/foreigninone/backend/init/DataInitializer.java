package com.foreigninone.backend.init;

import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import com.foreigninone.backend.domain.bank.repository.BankTransactionRepository;
import com.foreigninone.backend.domain.calendar.entity.CalendarEvent;
import com.foreigninone.backend.domain.calendar.entity.EventType;
import com.foreigninone.backend.domain.calendar.entity.SourceType;
import com.foreigninone.backend.domain.calendar.repository.CalendarEventRepository;
import com.foreigninone.backend.domain.calendar.service.CalendarEventService;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.entity.OcrStatus;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.exitcheck.repository.ExitCheckRepository;
import com.foreigninone.backend.domain.taxcheck.repository.TaxCheckRepository;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final DocumentRepository documentRepository;
    private final PaycheckRepository paycheckRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventService calendarEventService;
    private final TaxCheckRepository taxCheckRepository;
    private final ExitCheckRepository exitCheckRepository;
    private final EntityManager em;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findById(1L).isEmpty()) {
            log.info("Seed User #1 not found. Safely initializing Seed Data...");
            resetSeedData();
            log.info("Seed Data initialized successfully with User #1.");
        }
    }

    @Transactional
    public void resetSeedData() {
        log.info("Resetting Seed Data safely...");
        calendarEventRepository.deleteAllInBatch();
        exitCheckRepository.deleteAllInBatch();
        taxCheckRepository.deleteAllInBatch();
        paycheckRepository.deleteAllInBatch();
        bankTransactionRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // PostgreSQL 등 시퀀스를 사용하는 환경에서 1번부터 다시 시작하도록 시퀀스 번호 리셋 (MySQL 등에서는 무시됨)
        try {
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS users_user_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS bank_transactions_transaction_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS documents_document_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS paychecks_paycheck_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS calendar_events_event_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS exit_checks_exit_check_id_seq RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER SEQUENCE IF EXISTS tax_checks_tax_check_id_seq RESTART WITH 1").executeUpdate();
        } catch (Exception e) {
            log.debug("Sequence reset skipped: {}", e.getMessage());
        }

        initSeedData();
        log.info("Seed Data reset successfully.");
    }

    private void initSeedData() {
        // ==========================================
        // User #1: 민수 (정상 및 12만원 급여 감소 이상징후 시나리오)
        // ==========================================
        User user1 = User.builder()
                .name("민수")
                .phone("01012345678")
                .password("hashed_password_sample")
                .nationality("베트남")
                .visaType("E-9")
                .entryDate(LocalDate.of(2025, 3, 1))
                .employmentStatus("WORKING")
                .companyName("한국정밀")
                .workStartDate(LocalDate.of(2025, 3, 10))
                .payday(25)
                .expectedExitDate(LocalDate.of(2027, 3, 1))
                .language("ko")
                .build();
        user1 = userRepository.save(user1);

        // Document 1: 근로계약서
        Document contractDoc = Document.builder()
                .user(user1)
                .documentType(DocumentType.EMPLOYMENT_CONTRACT)
                .originalFilename("한국정밀_표준근로계약서_민수.pdf")
                .mimeType("application/pdf")
                .fileSize(245000L)
                .ocrStatus(OcrStatus.SUCCESS)
                .extractedData(Map.of(
                        "companyName", "한국정밀",
                        "baseSalary", 2300000,
                        "payday", 25,
                        "workStartDate", "2025-03-10",
                        "contractDurationMonths", 36
                ))
                .build();
        contractDoc = documentRepository.save(contractDoc);

        // 7월 거래 & 명세서 & PayCheck (정상 케이스)
        BankTransaction txJuly = BankTransaction.builder()
                .user(user1)
                .bankName("하나은행")
                .fintechUseNum("123456789012345678901234")
                .bankTranId("F123456789U4BC34239Z001")
                .bankTranDate(LocalDate.of(2026, 7, 25))
                .tranTime(LocalTime.of(9, 10, 0))
                .inoutType("입금")
                .tranType("급여")
                .printedContent("한국정밀 7월 급여")
                .tranAmt(BigDecimal.valueOf(2380000))
                .afterBalanceAmt(BigDecimal.valueOf(4500000))
                .branchName("분당점")
                .transactionCategory("SALARY")
                .build();
        txJuly = bankTransactionRepository.save(txJuly);

        Document payslipJuly = Document.builder()
                .user(user1)
                .documentType(DocumentType.PAYSLIP)
                .originalFilename("2026년_7월_임금명세서.pdf")
                .mimeType("application/pdf")
                .fileSize(182000L)
                .ocrStatus(OcrStatus.SUCCESS)
                .extractedData(Map.of(
                        "payPeriod", "2026-07",
                        "baseSalary", 2200000,
                        "overtimeAllowance", 180000,
                        "deduction", 0,
                        "netPay", 2380000
                ))
                .build();
        payslipJuly = documentRepository.save(payslipJuly);

        Paycheck paycheckJuly = Paycheck.builder()
                .user(user1)
                .transaction(txJuly)
                .contractDocument(contractDoc)
                .payslipDocument(payslipJuly)
                .payPeriod("2026-07")
                .contractAmount(BigDecimal.valueOf(2300000))
                .payslipAmount(BigDecimal.valueOf(2380000))
                .actualAmount(BigDecimal.valueOf(2380000))
                .differenceAmount(BigDecimal.ZERO)
                .expectedPaymentDate(LocalDate.of(2026, 7, 25))
                .paymentDate(LocalDateTime.of(2026, 7, 25, 9, 10, 0))
                .status(PaycheckStatus.NORMAL)
                .analysisSummary("임금명세서 실지급액과 실제 입금액이 일치합니다.")
                .nextAction("특이사항이 없습니다.")
                .analyzedAt(LocalDateTime.of(2026, 7, 25, 9, 15, 0))
                .build();
        paycheckJuly = paycheckRepository.save(paycheckJuly);
        calendarEventService.syncPaycheckEvent(paycheckJuly);

        // 8월 거래 & 명세서 & PayCheck (12만원 차액 이상징후 케이스)
        BankTransaction txAugust = BankTransaction.builder()
                .user(user1)
                .bankName("하나은행")
                .fintechUseNum("123456789012345678901234")
                .bankTranId("F123456789U4BC34239Z002")
                .bankTranDate(LocalDate.of(2026, 8, 25))
                .tranTime(LocalTime.of(9, 14, 0))
                .inoutType("입금")
                .tranType("급여")
                .printedContent("한국정밀 8월 급여")
                .tranAmt(BigDecimal.valueOf(2260000))
                .afterBalanceAmt(BigDecimal.valueOf(6760000))
                .branchName("분당점")
                .transactionCategory("SALARY")
                .build();
        txAugust = bankTransactionRepository.save(txAugust);

        Document payslipAugust = Document.builder()
                .user(user1)
                .documentType(DocumentType.PAYSLIP)
                .originalFilename("2026년_8월_임금명세서.pdf")
                .mimeType("application/pdf")
                .fileSize(195000L)
                .ocrStatus(OcrStatus.SUCCESS)
                .extractedData(Map.of(
                        "payPeriod", "2026-08",
                        "baseSalary", 2200000,
                        "overtimeAllowance", 180000,
                        "deduction", 0,
                        "netPay", 2380000
                ))
                .build();
        payslipAugust = documentRepository.save(payslipAugust);

        // 8월 거래 & 명세서는 DB에 보관하고, Paycheck 레코드는 아직 생성하지 않음 (상단 [급여 동기화] 버튼 클릭 시 실시간 자동 감지 시연용)
        calendarEventService.syncPaydayEventsForUser(user1);
        calendarEventService.syncExitEvent(user1);

        // ==========================================
        // User #2: 응우옌 (취업 전 / NOT_WORKING 상태)
        // ==========================================
        User user2 = User.builder()
                .name("응우옌")
                .phone("01098765432")
                .nationality("베트남")
                .visaType("D-2")
                .entryDate(LocalDate.of(2026, 2, 20))
                .employmentStatus("NOT_WORKING")
                .companyName(null)
                .workStartDate(null)
                .payday(null)
                .expectedExitDate(LocalDate.of(2028, 2, 28))
                .language("vi")
                .build();
        user2 = userRepository.save(user2);
        calendarEventService.syncExitEvent(user2);

        // ==========================================
        // User #3: 솜차이 (자료 부족 / INSUFFICIENT_DATA 상태)
        // ==========================================
        User user3 = User.builder()
                .name("솜차이")
                .phone("01055554444")
                .nationality("태국")
                .visaType("E-9")
                .entryDate(LocalDate.of(2025, 5, 20))
                .employmentStatus("WORKING")
                .companyName("대영산업")
                .workStartDate(LocalDate.of(2025, 6, 1))
                .payday(10)
                .expectedExitDate(LocalDate.of(2027, 8, 31))
                .language("ko")
                .build();
        user3 = userRepository.save(user3);

        BankTransaction txSomchai = BankTransaction.builder()
                .user(user3)
                .bankName("KB국민은행")
                .fintechUseNum("987654321098765432109876")
                .bankTranId("KB987654321Z001")
                .bankTranDate(LocalDate.of(2026, 8, 10))
                .tranTime(LocalTime.of(10, 0, 0))
                .inoutType("입금")
                .tranType("급여")
                .printedContent("대영산업 8월급여")
                .tranAmt(BigDecimal.valueOf(2100000))
                .afterBalanceAmt(BigDecimal.valueOf(2100000))
                .branchName("안산지점")
                .transactionCategory("SALARY")
                .build();
        txSomchai = bankTransactionRepository.save(txSomchai);

        Paycheck paycheckSomchai = Paycheck.builder()
                .user(user3)
                .transaction(txSomchai)
                .payPeriod("2026-08")
                .contractAmount(BigDecimal.valueOf(2100000))
                .payslipAmount(null)
                .actualAmount(BigDecimal.valueOf(2100000))
                .differenceAmount(BigDecimal.ZERO)
                .expectedPaymentDate(LocalDate.of(2026, 8, 10))
                .paymentDate(LocalDateTime.of(2026, 8, 10, 10, 0, 0))
                .status(PaycheckStatus.INSUFFICIENT_DATA)
                .analysisSummary("급여 입금은 확인되었으나 대조할 임금명세서가 없습니다.")
                .nextAction("이번 달 임금명세서를 업로드하여 상세 공제 내역을 확인하세요.")
                .analyzedAt(LocalDateTime.of(2026, 8, 10, 10, 5, 0))
                .build();
        paycheckSomchai = paycheckRepository.save(paycheckSomchai);
        calendarEventService.syncPaycheckEvent(paycheckSomchai);
        calendarEventService.syncPaydayEventsForUser(user3);
        calendarEventService.syncExitEvent(user3);
    }
}
