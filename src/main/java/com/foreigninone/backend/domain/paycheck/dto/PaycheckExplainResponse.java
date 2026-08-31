package com.foreigninone.backend.domain.paycheck.dto;

import com.foreigninone.backend.domain.agent.dto.EmployerQuestionCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaycheckExplainResponse {

    private Long paycheckId;
    private String caseType;
    private String summary;
    private List<String> reasons;
    private List<String> nextActions;
    private List<EmployerQuestionCard> employerQuestionCards;
}
