package com.foreigninone.backend.domain.agent.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.agent.dto.AgentChatRequest;
import com.foreigninone.backend.domain.agent.dto.AgentChatResponse;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckRequest;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckResponse;
import com.foreigninone.backend.domain.agent.service.AiAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService aiAgentService;

    @PostMapping("/paycheck")
    public ResponseEntity<ApiResponse<AgentPaycheckResponse>> analyzePaycheckCase(
            @Valid @RequestBody AgentPaycheckRequest request
    ) {
        AgentPaycheckResponse response = aiAgentService.analyzePaycheckCase(request.getPaycheckId(), request.getCaseType());
        return ResponseEntity.ok(ApiResponse.ok(response, "AI 분석이 완료되었습니다."));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatResponse>> answerChatQuestion(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @Valid @RequestBody AgentChatRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        AgentChatResponse response = aiAgentService.answerChatQuestion(userId, request.getQuestion(), request.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
