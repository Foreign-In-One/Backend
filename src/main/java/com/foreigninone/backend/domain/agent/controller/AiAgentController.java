package com.foreigninone.backend.domain.agent.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckRequest;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckResponse;
import com.foreigninone.backend.domain.agent.service.AiAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
