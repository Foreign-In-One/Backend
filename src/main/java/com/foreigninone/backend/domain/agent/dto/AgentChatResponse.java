package com.foreigninone.backend.domain.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트 services/ai.ts 의 askAssistant() 가 기대하는 {ok, text, error} 형태와 동일한 계약을 맞춘다.
 * ok=false 또는 text=null 이면 클라이언트가 localAnswer() 규칙 엔진으로 폴백한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    private boolean ok;
    private String text;
    private String error;
}
