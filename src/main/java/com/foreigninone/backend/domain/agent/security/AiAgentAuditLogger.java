package com.foreigninone.backend.domain.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * NIST AI RMF (MEASURE 기능): AI 에이전트 보안 이벤트 감사 추적(Audit Trail) 로거
 * 향후 garak, PyRIT 등의 레드티밍 벤치마크 및 보안 감사업무에 필요한 정량 지표 집계를 지원합니다.
 */
@Slf4j
@Component
public class AiAgentAuditLogger {

    public void logSecurityEvent(
            String clientKey,
            Long userId,
            String endpoint,
            AiRiskType riskType,
            String action,
            int inputLength,
            long latencyMs,
            String detail
    ) {
        String jsonLog = String.format(
                "{\"auditType\":\"AI_SECURITY\",\"timestamp\":\"%s\",\"clientKey\":\"%s\",\"userId\":%d,\"endpoint\":\"%s\",\"riskType\":\"%s\",\"owasp\":\"%s\",\"action\":\"%s\",\"inputLength\":%d,\"latencyMs\":%d,\"detail\":\"%s\"}",
                Instant.now().toString(),
                clientKey != null ? clientKey : "unknown",
                userId != null ? userId : -1L,
                endpoint,
                riskType.name(),
                riskType.getOwaspMapping(),
                action,
                inputLength,
                latencyMs,
                escapeJson(detail)
        );

        if (riskType == AiRiskType.NONE) {
            log.info("[AI_AUDIT_LOG] {}", jsonLog);
        } else {
            log.warn("[AI_SECURITY_ALERT] {}", jsonLog);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
