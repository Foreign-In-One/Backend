package com.foreigninone.backend.domain.exitcheck.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.exitcheck.dto.ExitCheckAnalyzeRequest;
import com.foreigninone.backend.domain.exitcheck.dto.ExitCheckResponse;
import com.foreigninone.backend.domain.exitcheck.service.ExitCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exit-checks")
@RequiredArgsConstructor
public class ExitCheckController {

    private final ExitCheckService exitCheckService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExitCheckResponse>>> getExitChecks(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        List<ExitCheckResponse> response = exitCheckService.getExitChecks(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{exitCheckId}")
    public ResponseEntity<ApiResponse<ExitCheckResponse>> getExitCheck(
            @PathVariable("exitCheckId") Long exitCheckId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        ExitCheckResponse response = exitCheckService.getExitCheck(exitCheckId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<ExitCheckResponse>> analyzeExitCheck(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestBody(required = false) ExitCheckAnalyzeRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        ExitCheckAnalyzeRequest body = request != null ? request : ExitCheckAnalyzeRequest.builder().build();
        ExitCheckResponse response = exitCheckService.analyzeExitCheck(userId, body);
        return ResponseEntity.ok(ApiResponse.ok(response, "출국 정산 분석이 완료되었습니다."));
    }
}
