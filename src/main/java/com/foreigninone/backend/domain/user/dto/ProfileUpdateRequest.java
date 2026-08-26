package com.foreigninone.backend.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {

    private String employmentStatus;
    private String companyName;

    @Min(value = 1, message = "급여일은 1일 이상이어야 합니다.")
    @Max(value = 31, message = "급여일은 31일 이하이어야 합니다.")
    private Integer payday;

    private LocalDate expectedExitDate;
    private String language;
}
