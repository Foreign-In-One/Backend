package com.foreigninone.backend.domain.paycheck.repository;

import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaycheckRepository extends JpaRepository<Paycheck, Long> {
    Optional<Paycheck> findByPaycheckIdAndUser_UserId(Long paycheckId, Long userId);
    Optional<Paycheck> findByUser_UserIdAndPayPeriod(Long userId, String payPeriod);
    List<Paycheck> findByUser_UserIdOrderByPayPeriodDesc(Long userId);
    List<Paycheck> findByUser_UserIdAndPayPeriodBetweenOrderByPayPeriodDesc(Long userId, String fromPeriod, String toPeriod);
}
