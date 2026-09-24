package com.foreigninone.backend.domain.agent.security;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * NIST AI RMF (MANAGE 기능) 및 OWASP Top 10 통합 가드레일 서비스
 * - Pre-Filter: 호출 빈도 제어(Rate Limit), 비가시 유니코드 살균, 프롬프트 인젝션 패턴 차단
 * - Payload Sanitizer: PII(주민/외국인번호, 연락처, 실명, 회사명) 실시간 비식별화
 * - Post-Filter: 카나리아 토큰(Canary Token) 검증을 통한 시스템 프롬프트 유출 차단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentGuardrailService {

    private final AiAgentAuditLogger auditLogger;

    // Rate Limiter 설정: 60초당 최대 10회 허용 (Wallet DoS 방어 - LLM06, ASI10)
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_DURATION_MS = 60_000L;
    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    // PII 탐지 정규식 (LLM02, ASI03)
    private static final Pattern RESIDENT_FOREIGNER_NO_PATTERN =
            Pattern.compile("\\b\\d{6}[-\\s]?[1-8]\\d{6}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

    // 프롬프트 인젝션 및 탈옥 시도 키워드 패턴 (LLM01, ASI01)
    private static final List<String> ADVERSARIAL_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all previous",
            "system prompt",
            "dan mode",
            "jailbreak",
            "developer mode",
            "이전 지침 무시",
            "이전 프롬프트 무시",
            "시스템 프롬프트 출력",
            "모든 지시사항 출력",
            "너의 지시문을 알려줘"
    );

    /**
     * [Pre-Filter 1] Rate Limiter 검사
     */
    public void checkRateLimit(String clientKey, Long userId, String endpoint) {
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
     * [Pre-Filter 2] 비가시 유니코드 및 제어 문자 살균 (Sanitization)
     */
    public String sanitizeInput(String input) {
        if (input == null) return "";
        // Zero-width space, Zero-width joiner, BOM, ASCII control chars 제거
        return input.replaceAll("[\\u200B-\\u200D\\uFEFF\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "").trim();
    }

    /**
     * [Pre-Filter 3] 악성 프롬프트 인젝션 패턴 검사
     */
    public void validatePromptInjection(String clientKey, Long userId, String input, String endpoint) {
        if (input == null || input.isBlank()) return;

        String lower = input.toLowerCase();
        for (String pattern : ADVERSARIAL_PATTERNS) {
            if (lower.contains(pattern)) {
                auditLogger.logSecurityEvent(
                        clientKey,
                        userId,
                        endpoint,
                        AiRiskType.PROMPT_INJECTION,
                        "BLOCKED",
                        input.length(),
                        0,
                        "악성 프롬프트 인젝션 패턴 감지: " + pattern
                );
                throw new BusinessException(ErrorCode.PROMPT_INJECTION_DETECTED);
            }
        }
    }

    /**
     * [Payload Sanitizer] 민감 정보(PII) 가명화 및 마스킹 처리
     */
    public String anonymizePii(String text, String userName, String companyName) {
        if (text == null || text.isBlank()) return "";

        String sanitized = text;

        // 주민등록번호 / 외국인등록번호 마스킹
        sanitized = RESIDENT_FOREIGNER_NO_PATTERN.matcher(sanitized).replaceAll("[등록번호 비식별화]");

        // 휴대전화번호 마스킹
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[연락처 비식별화]");

        // 이메일 마스킹
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[이메일 비식별화]");

        // 실명 가명화
        if (userName != null && !userName.isBlank() && sanitized.contains(userName)) {
            sanitized = sanitized.replace(userName, "[근로자 본인]");
        }

        // 사업장명 가명화
        if (companyName != null && !companyName.isBlank() && sanitized.contains(companyName)) {
            sanitized = sanitized.replace(companyName, "[소속 사업장]");
        }

        return sanitized;
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

        if (output.contains(canaryToken) || output.contains("CANARY_SEC_")) {
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
}
