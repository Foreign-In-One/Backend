package com.foreigninone.backend.domain.agent.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NIST AI RMF (MAP 기능) 및 OWASP Top 10 (Agents & LLM) 위험 분류 모델
 */
@Getter
@RequiredArgsConstructor
public enum AiRiskType {
    NONE("정상 요청", "RISK_FREE"),
    RATE_LIMIT_EXCEEDED("호출 빈도 초과 (Wallet DoS 차단)", "LLM06_ASI10"),
    PAYLOAD_TOO_LARGE("입력 페이로드 길이 초과", "LLM06"),
    PII_EXPOSURE_RISK("민감 개인정보(PII) 노출 위험 차단", "LLM02_ASI03"),
    PROMPT_INJECTION("프롬프트 인젝션 및 목표 변조 시도", "LLM01_ASI01"),
    SYSTEM_PROMPT_LEAK("시스템 프롬프트 유출 및 카나리아 탐지", "LLM08"),
    UNAUTHORIZED_USER_ACCESS("비인가 사용자 데이터 컨텍스트 접근", "ASI03");

    private final String description;
    private final String owaspMapping;
}
