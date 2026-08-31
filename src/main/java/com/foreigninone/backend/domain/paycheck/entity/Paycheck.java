package com.foreigninone.backend.domain.paycheck.entity;

import com.foreigninone.backend.domain.bank.entity.BankTransaction;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paychecks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_pay_period", columnNames = {"user_id", "pay_period"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Paycheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paycheck_id")
    private Long paycheckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private BankTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_document_id")
    private Document contractDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_document_id")
    private Document payslipDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_receipt_document_id")
    private Document bankReceiptDocument;

    @Column(name = "pay_period", length = 7, nullable = false)
    private String payPeriod;

    @Column(name = "contract_amount", precision = 15, scale = 2)
    private BigDecimal contractAmount;

    @Column(name = "payslip_amount", precision = 15, scale = 2)
    private BigDecimal payslipAmount;

    @Column(name = "actual_amount", precision = 15, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "difference_amount", precision = 15, scale = 2)
    private BigDecimal differenceAmount;

    @Column(name = "expected_payment_date")
    private LocalDate expectedPaymentDate;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PaycheckStatus status;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @Column(name = "next_action", columnDefinition = "TEXT")
    private String nextAction;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateAnalysisResult(
            BigDecimal contractAmount,
            BigDecimal payslipAmount,
            BigDecimal actualAmount,
            BigDecimal differenceAmount,
            LocalDate expectedPaymentDate,
            LocalDateTime paymentDate,
            PaycheckStatus status,
            String analysisSummary,
            String nextAction,
            BankTransaction transaction,
            Document contractDocument,
            Document payslipDocument,
            Document bankReceiptDocument
    ) {
        this.contractAmount = contractAmount;
        this.payslipAmount = payslipAmount;
        this.actualAmount = actualAmount;
        this.differenceAmount = differenceAmount;
        this.expectedPaymentDate = expectedPaymentDate;
        this.paymentDate = paymentDate;
        this.status = status;
        this.analysisSummary = analysisSummary;
        this.nextAction = nextAction;
        this.analyzedAt = LocalDateTime.now();
        if (transaction != null) this.transaction = transaction;
        if (contractDocument != null) this.contractDocument = contractDocument;
        if (payslipDocument != null) this.payslipDocument = payslipDocument;
        if (bankReceiptDocument != null) this.bankReceiptDocument = bankReceiptDocument;
    }
}
