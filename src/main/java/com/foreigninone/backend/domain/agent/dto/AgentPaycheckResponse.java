package com.foreigninone.backend.domain.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPaycheckResponse {
    private String caseType;
    private String headline;
    private String summary;
    private String documentCheckGuide;
    private List<String> reasons;
    private List<String> requiredEvidence;
    private List<String> nextActions;
    private String messageForEmployer;
    private List<EmployerQuestionCard> employerQuestionCards;
}
