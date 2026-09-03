package com.foreigninone.backend.domain.bank.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.bank.dto.MockBankTransactionItem;
import com.foreigninone.backend.domain.bank.dto.MockBankTransactionResponse;
import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import com.foreigninone.backend.domain.bank.repository.BankTransactionRepository;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankService {

    private final BankTransactionRepository bankTransactionRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    @Transactional(readOnly = true)
    public MockBankTransactionResponse getMockTransactions(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDate startDate = from != null ? from : LocalDate.now().minusMonths(3);
        LocalDate endDate = to != null ? to : LocalDate.now().plusMonths(1);

        List<BankTransaction> transactions = bankTransactionRepository
                .findByUser_UserIdAndBankTranDateBetweenOrderByBankTranDateDescTranTimeDesc(user.getUserId(), startDate, endDate);

        List<MockBankTransactionItem> items = transactions.stream()
                .map(this::convertToItem)
                .toList();

        return MockBankTransactionResponse.builder()
                .apiTranId("MOCK-TRAN-" + UUID.randomUUID().toString().substring(0, 8))
                .rspCode("A0000")
                .rspMessage("정상 처리되었습니다.")
                .resList(items)
                .build();
    }

    private MockBankTransactionItem convertToItem(BankTransaction tx) {
        return MockBankTransactionItem.builder()
                .bankTranId(tx.getBankTranId())
                .bankTranDate(tx.getBankTranDate().format(DATE_FORMATTER))
                .tranTime(tx.getTranTime() != null ? tx.getTranTime().format(TIME_FORMATTER) : "090000")
                .inoutType(tx.getInoutType())
                .tranType(tx.getTranType())
                .printedContent(tx.getPrintedContent())
                .tranAmt(tx.getTranAmt() != null ? tx.getTranAmt().toPlainString() : "0")
                .afterBalanceAmt(tx.getAfterBalanceAmt() != null ? tx.getAfterBalanceAmt().toPlainString() : "0")
                .branchName(tx.getBranchName())
                .bankName(tx.getBankName())
                .fintechUseNum(tx.getFintechUseNum())
                .build();
    }
}
