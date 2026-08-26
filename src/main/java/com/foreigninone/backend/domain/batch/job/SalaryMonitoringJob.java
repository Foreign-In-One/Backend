package com.foreigninone.backend.domain.batch.job;

import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import com.foreigninone.backend.domain.bank.repository.BankTransactionRepository;
import com.foreigninone.backend.domain.batch.dto.SalaryMonitoringBatchResponse;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckAnalyzeRequest;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckResponse;
import com.foreigninone.backend.domain.paycheck.service.PaycheckService;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalaryMonitoringJob {

    private final UserRepository userRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final PaycheckService paycheckService;

    @Scheduled(cron = "${paycycle.batch.cron:0 0 9 * * *}")
    public SalaryMonitoringBatchResponse executeSalaryMonitoring() {
        return executeSalaryMonitoringInternal(null);
    }

    public SalaryMonitoringBatchResponse executeSalaryMonitoringForUser(Long userId) {
        return executeSalaryMonitoringInternal(userId);
    }

    private SalaryMonitoringBatchResponse executeSalaryMonitoringInternal(Long targetUserId) {
        log.info("Starting SalaryMonitoringJob for targetUserId: {}...", targetUserId != null ? targetUserId : "ALL");
        List<PaycheckResponse> results = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        List<User> users;
        if (targetUserId != null) {
            users = userRepository.findById(targetUserId).map(List::of).orElse(List.of());
        } else {
            users = userRepository.findAll();
        }

        LocalDate now = LocalDate.now();
        LocalDate searchStart = now.minusMonths(2).withDayOfMonth(1);
        LocalDate searchEnd = now.plusMonths(1);

        for (User user : users) {
            if ("NOT_WORKING".equalsIgnoreCase(user.getEmploymentStatus())) {
                continue;
            }

            List<BankTransaction> transactions = bankTransactionRepository
                    .findByUser_UserIdAndBankTranDateBetweenOrderByBankTranDateDescTranTimeDesc(
                            user.getUserId(), searchStart, searchEnd);

            for (BankTransaction tx : transactions) {
                if ("급여".equals(tx.getTranType()) ||
                        "SALARY".equals(tx.getTransactionCategory()) ||
                        (tx.getPrintedContent() != null && tx.getPrintedContent().contains("급여"))) {

                    String payPeriod = tx.getBankTranDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

                    try {
                        PaycheckAnalyzeRequest request = PaycheckAnalyzeRequest.builder()
                                .payPeriod(payPeriod)
                                .transactionId(tx.getTransactionId())
                                .build();

                        PaycheckResponse response = paycheckService.analyzePaycheck(user.getUserId(), request);
                        results.add(response);
                        createdCount++;
                        log.info("Processed salary monitoring for user: {}, period: {}, status: {}",
                                user.getUserId(), payPeriod, response.getStatus());
                    } catch (Exception e) {
                        log.error("Failed to process salary monitoring for user: {}, txId: {}", user.getUserId(), tx.getTransactionId(), e);
                    }
                }
            }
        }

        log.info("Finished SalaryMonitoringJob. Processed {} paychecks.", results.size());
        return SalaryMonitoringBatchResponse.builder()
                .processedCount(results.size())
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .paychecks(results)
                .build();
    }
}
