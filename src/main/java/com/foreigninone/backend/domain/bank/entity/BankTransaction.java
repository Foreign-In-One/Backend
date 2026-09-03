package com.foreigninone.backend.domain.bank.entity;

import com.foreigninone.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bank_transactions", indexes = {
        @Index(name = "idx_user_date", columnList = "user_id, bank_tran_date"),
        @Index(name = "idx_user_inout_date", columnList = "user_id, inout_type, bank_tran_date")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "fintech_use_num", length = 30)
    private String fintechUseNum;

    @Column(name = "bank_tran_id", length = 50, unique = true, nullable = false)
    private String bankTranId;

    @Column(name = "bank_tran_date", nullable = false)
    private LocalDate bankTranDate;

    @Column(name = "tran_time")
    private LocalTime tranTime;

    @Column(name = "inout_type", length = 10, nullable = false)
    private String inoutType;

    @Column(name = "tran_type", length = 30)
    private String tranType;

    @Column(name = "printed_content", length = 255)
    private String printedContent;

    @Column(name = "tran_amt", precision = 15, scale = 2, nullable = false)
    private BigDecimal tranAmt;

    @Column(name = "after_balance_amt", precision = 15, scale = 2)
    private BigDecimal afterBalanceAmt;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "transaction_category", length = 30)
    private String transactionCategory;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
