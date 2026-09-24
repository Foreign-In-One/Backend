package com.foreigninone.backend.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 300, message = "질문은 최대 300자까지 입력 가능합니다.")
    private String question;

    private String locale;
}
