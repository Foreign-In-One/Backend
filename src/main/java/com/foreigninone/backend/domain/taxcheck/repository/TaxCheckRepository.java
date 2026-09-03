package com.foreigninone.backend.domain.taxcheck.repository;

import com.foreigninone.backend.domain.taxcheck.entity.TaxCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxCheckRepository extends JpaRepository<TaxCheck, Long> {
    Optional<TaxCheck> findByTaxCheckIdAndUser_UserId(Long taxCheckId, Long userId);
    List<TaxCheck> findByUser_UserIdOrderByAnalyzedAtDescTaxCheckIdDesc(Long userId);
    List<TaxCheck> findByUser_UserIdAndTaxYearOrderByAnalyzedAtDescTaxCheckIdDesc(Long userId, Integer taxYear);
}
