package com.foreigninone.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "nationality", length = 30)
    private String nationality;

    @Column(name = "visa_type", length = 20)
    private String visaType;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "employment_status", length = 30)
    private String employmentStatus;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "work_start_date")
    private LocalDate workStartDate;

    @Column(name = "payday")
    private Integer payday;

    @Column(name = "expected_exit_date")
    private LocalDate expectedExitDate;

    @Column(name = "language", length = 10)
    private String language;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateProfile(String employmentStatus, String companyName, Integer payday, LocalDate expectedExitDate, String language) {
        if (employmentStatus != null) this.employmentStatus = employmentStatus;
        if (companyName != null) this.companyName = companyName;
        if (payday != null) this.payday = payday;
        if (expectedExitDate != null) this.expectedExitDate = expectedExitDate;
        if (language != null) this.language = language;
    }
}
