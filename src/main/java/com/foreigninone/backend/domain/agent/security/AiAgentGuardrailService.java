package com.foreigninone.backend.domain.agent.security;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NIST AI RMF (MANAGE 기능) 및 OWASP Top 10 통합 가드레일 서비스
 * - Pre-Filter: 호출 빈도 제어(Rate Limit)
 * - Input Guard: 보안 서버(NeMo Guardrails 인젝션 분류 + Presidio PII 비식별화) 호출
 * - Post-Filter: 카나리아 토큰(Canary Token) 생성 및 보안 서버 출력 검증으로 시스템 프롬프트 유출 차단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentGuardrailService {

    private final AiAgentAuditLogger auditLogger;
    private final SecurityServerClient securityServerClient;

    @Value("${paycycle.redteam.enabled:false}")
    private boolean redteamEnabled;

    // Rate Limiter 설정: 60초당 최대 10회 허용 (Wallet DoS 방어 - LLM06, ASI10)
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_DURATION_MS = 60_000L;
    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    /**
     * [Pre-Filter 1] Rate Limiter 검사
     */
    public void checkRateLimit(String clientKey, Long userId, String endpoint) {
        if (redteamEnabled) return;
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_DURATION_MS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                auditLogger.logSecurityEvent(
                        clientKey,
                        userId,
                        endpoint,
                        AiRiskType.RATE_LIMIT_EXCEEDED,
                        "BLOCKED",
                        0,
                        0,
                        String.format("호출 빈도 초과: 최근 60초 내 %d회 요청", timestamps.size())
                );
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }

            timestamps.addLast(now);
        }
    }

    /**
     * [Input Guard] 프롬프트 인젝션 차단(LLM01, ASI01) + 질문/컨텍스트 PII 비식별화(LLM02, ASI03)
     * 보안 서버 장애 시 RestClientException 이 전파된다. 호출 측은 LLM 을 호출하지 않고 폴백해야 한다(fail-closed).
     */
    public ScreenedInput screenInput(String question, String context, String userName, String companyName,
                                     String clientKey, Long userId, String endpoint) {
        SecurityServerClient.PreResult result = securityServerClient.pre(question, context, userName, companyName);
        if (result.blocked()) {
            auditLogger.logSecurityEvent(
                    clientKey,
                    userId,
                    endpoint,
                    AiRiskType.PROMPT_INJECTION,
                    "BLOCKED",
                    question.length(),
                    0,
                    "보안 서버 입력 가드 차단: " + result.reason()
            );
            throw new BusinessException(ErrorCode.PROMPT_INJECTION_DETECTED);
        }
        return new ScreenedInput(result.sanitizedQuestion(), result.sanitizedContext());
    }

    /**
     * [Post-Filter 1] 카나리아 토큰 생성
     */
    public String generateCanaryToken() {
        return "CANARY_SEC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * [Post-Filter 2] 출력물 카나리아 토큰 및 시스템 프롬프트 유출 검사
     * @return true: 정상 출력 / false: 탈옥 탐지 및 유출 감지됨
     */
    public boolean verifyOutputSafe(String output, String canaryToken, String clientKey, Long userId, String endpoint) {
        if (output == null) return true;

        if (securityServerClient.post(output, canaryToken).blocked()) {
            auditLogger.logSecurityEvent(
                    clientKey,
                    userId,
                    endpoint,
                    AiRiskType.SYSTEM_PROMPT_LEAK,
                    "INTERCEPTED",
                    output.length(),
                    0,
                    "카나리아 토큰 유출 감지! LLM 출력을 즉시 차단하고 안전 템플릿으로 대체합니다."
            );
            return false;
        }

        return true;
    }

    public record ScreenedInput(String question, String context) {}
}
