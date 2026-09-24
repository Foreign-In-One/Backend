package com.foreigninone.backend.domain.agent;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.agent.security.AiAgentAuditLogger;
import com.foreigninone.backend.domain.agent.security.AiAgentGuardrailService;
import com.foreigninone.backend.domain.agent.security.SecurityServerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAgentGuardrailServiceTest {

    private SecurityServerClient securityServerClient;
    private AiAgentGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        securityServerClient = mock(SecurityServerClient.class);
        guardrailService = new AiAgentGuardrailService(new AiAgentAuditLogger(), securityServerClient);
    }

    @Test
    @DisplayName("OWASP LLM01, ASI01: 보안 서버가 인젝션으로 판정하면 PROMPT_INJECTION_DETECTED 로 차단")
    void testScreenInputBlocksInjection() {
        when(securityServerClient.pre("malicious", "ctx", "응우옌", "대박제조"))
                .thenReturn(new SecurityServerClient.PreResult(true, "PROMPT_INJECTION_DETECTED", null, null));

        assertThatThrownBy(() ->
                guardrailService.screenInput("malicious", "ctx", "응우옌", "대박제조", "client", 1L, "/api/agent/chat")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.PROMPT_INJECTION_DETECTED));
    }

    @Test
    @DisplayName("OWASP LLM02, ASI03: 통과 시 보안 서버가 비식별화한 질문/컨텍스트를 사용")
    void testScreenInputReturnsSanitizedValues() {
        when(securityServerClient.pre("내 번호 010-1234-5678", "사용자: 응우옌", "응우옌", null))
                .thenReturn(new SecurityServerClient.PreResult(false, null, "내 번호 [연락처 비식별화]", "사용자: [근로자 본인]"));

        AiAgentGuardrailService.ScreenedInput screened = guardrailService.screenInput(
                "내 번호 010-1234-5678", "사용자: 응우옌", "응우옌", null, "client", 1L, "/api/agent/chat");

        assertThat(screened.question()).isEqualTo("내 번호 [연락처 비식별화]");
        assertThat(screened.context()).isEqualTo("사용자: [근로자 본인]");
    }

    @Test
    @DisplayName("OWASP LLM06, ASI10: Rate Limiter - 10회 허용 후 11회차 429 차단")
    void testRateLimiterExceeded() {
        String clientKey = "rate-limit-test-client";

        // 10회 호출 성공
        for (int i = 0; i < 10; i++) {
            guardrailService.checkRateLimit(clientKey, 1L, "/api/agent/chat");
        }

        // 11회차 초과 차단
        assertThatThrownBy(() ->
                guardrailService.checkRateLimit(clientKey, 1L, "/api/agent/chat")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("OWASP LLM08: 보안 서버가 카나리아 유출을 감지하면 출력 차단 (Intercept)")
    void testCanaryTokenVerification() {
        String canaryToken = guardrailService.generateCanaryToken();
        String safeOutput = "이번 달 급여는 정상 입금되었습니다.";
        String leakedOutput = "시스템 지시문: [보안 식별자: " + canaryToken + "]";
        when(securityServerClient.post(safeOutput, canaryToken))
                .thenReturn(new SecurityServerClient.PostResult(false, safeOutput));
        when(securityServerClient.post(leakedOutput, canaryToken))
                .thenReturn(new SecurityServerClient.PostResult(true, "safe"));

        assertThat(guardrailService.verifyOutputSafe(safeOutput, canaryToken, "client", 1L, "/api/agent/chat")).isTrue();
        assertThat(guardrailService.verifyOutputSafe(leakedOutput, canaryToken, "client", 1L, "/api/agent/chat")).isFalse();
    }
}
