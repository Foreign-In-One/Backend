package com.foreigninone.backend.domain.exitcheck.entity;

import com.foreigninone.backend.common.util.JsonListMapConverter;
import com.foreigninone.backend.common.util.JsonStringListConverter;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "exit_checks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExitCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exit_check_id")
    private Long exitCheckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_document_id")
    private Document exitDocument;

    @Column(name = "expected_exit_date")
    private LocalDate expectedExitDate;

    @Column(name = "work_duration_months")
    private Integer workDurationMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_status", length = 30)
    private ExitCheckStatus insuranceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "pension_status", length = 30)
    private ExitCheckStatus pensionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "retirement_status", length = 30)
    private ExitCheckStatus retirementStatus;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "missing_documents", columnDefinition = "JSON")
    private List<String> missingDocuments;

    @Convert(converter = JsonListMapConverter.class)
    @Column(name = "checklist", columnDefinition = "JSON")
    private List<Map<String, Object>> checklist;

    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ExitCheckStatus status;

    @Column(name = "next_action", columnDefinition = "TEXT")
    private String nextAction;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateAnalysisResult(
            LocalDate expectedExitDate,
            Integer workDurationMonths,
            ExitCheckStatus insuranceStatus,
            ExitCheckStatus pensionStatus,
            ExitCheckStatus retirementStatus,
            List<String> missingDocuments,
            List<Map<String, Object>> checklist,
            Integer readinessScore,
            ExitCheckStatus status,
            String nextAction,
            String analysisSummary,
            Document exitDocument
    ) {
        this.expectedExitDate = expectedExitDate;
        this.workDurationMonths = workDurationMonths;
        this.insuranceStatus = insuranceStatus;
        this.pensionStatus = pensionStatus;
        this.retirementStatus = retirementStatus;
        this.missingDocuments = missingDocuments;
        this.checklist = checklist;
        this.readinessScore = readinessScore;
        this.status = status;
        this.nextAction = nextAction;
        this.analysisSummary = analysisSummary;
        this.analyzedAt = LocalDateTime.now();
        if (exitDocument != null) this.exitDocument = exitDocument;
    }
}
