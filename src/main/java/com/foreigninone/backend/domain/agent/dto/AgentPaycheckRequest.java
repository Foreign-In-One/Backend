package com.foreigninone.backend.domain.agent.dto;

import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPaycheckRequest {
    @NotNull(message = "paycheckId는 필수입니다.")
    private Long paycheckId;

    private PaycheckCaseType caseType;
}
