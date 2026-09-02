package com.foreigninone.backend.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
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
public class AgentChatRequest {

    @NotBlank(message = "question은 필수입니다.")
    private String question;

    private String locale;
}
