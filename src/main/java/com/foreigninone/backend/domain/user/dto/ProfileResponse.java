package com.foreigninone.backend.domain.user.dto;

import com.foreigninone.backend.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private Long userId;
    private String name;
    private String phone;
    private String nationality;
    private String visaType;
    private LocalDate entryDate;
    private String employmentStatus;
    private String companyName;
    private LocalDate workStartDate;
    private Integer payday;
    private LocalDate expectedExitDate;
    private String language;

    public static ProfileResponse from(User user) {
        return ProfileResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .phone(user.getPhone())
                .nationality(user.getNationality())
                .visaType(user.getVisaType())
                .entryDate(user.getEntryDate())
                .employmentStatus(user.getEmploymentStatus())
                .companyName(user.getCompanyName())
                .workStartDate(user.getWorkStartDate())
                .payday(user.getPayday())
                .expectedExitDate(user.getExpectedExitDate())
                .language(user.getLanguage())
                .build();
    }
}
