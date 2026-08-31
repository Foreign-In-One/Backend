package com.foreigninone.backend.domain.bank.repository;

import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    Optional<BankTransaction> findByBankTranId(String bankTranId);
    List<BankTransaction> findByUser_UserIdAndBankTranDateBetweenOrderByBankTranDateDescTranTimeDesc(
            Long userId, LocalDate startDate, LocalDate endDate);
    List<BankTransaction> findByUser_UserIdOrderByBankTranDateDescTranTimeDesc(Long userId);
}
