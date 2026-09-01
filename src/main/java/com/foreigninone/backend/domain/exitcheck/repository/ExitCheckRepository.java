package com.foreigninone.backend.domain.exitcheck.repository;

import com.foreigninone.backend.domain.exitcheck.entity.ExitCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExitCheckRepository extends JpaRepository<ExitCheck, Long> {
    Optional<ExitCheck> findByExitCheckIdAndUser_UserId(Long exitCheckId, Long userId);
    List<ExitCheck> findByUser_UserIdOrderByAnalyzedAtDesc(Long userId);
    Optional<ExitCheck> findFirstByUser_UserIdOrderByAnalyzedAtDesc(Long userId);
}
