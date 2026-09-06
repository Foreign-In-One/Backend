package com.foreigninone.backend.domain.taxcheck.entity;

import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tax_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TaxCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tax_check_id")
    private Long taxCheckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_document_id")
    private Document taxDocument;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(name = "resident_status", length = 20)
    private String residentStatus;

    @Column(name = "annual_income", precision = 15, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "flat_tax_estimate", precision = 15, scale = 2)
    private BigDecimal flatTaxEstimate;

    @Column(name = "general_tax_estimate", precision = 15, scale = 2)
    private BigDecimal generalTaxEstimate;

    @Column(name = "tax_difference", precision = 15, scale = 2)
    private BigDecimal taxDifference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefit_summary", columnDefinition = "JSON")
    private Map<String, Object> benefitSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_documents", columnDefinition = "JSON")
    private Map<String, Object> requiredDocuments;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private TaxCheckStatus status;

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
}
