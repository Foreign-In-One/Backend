package com.foreigninone.backend.domain.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployerQuestionCard {
    private String title;
    private String koreanScript;
    private String nativeScript;
}
