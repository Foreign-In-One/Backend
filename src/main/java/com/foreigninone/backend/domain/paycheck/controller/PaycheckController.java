package com.foreigninone.backend.domain.paycheck.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckAnalyzeRequest;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckExplainRequest;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckExplainResponse;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckResponse;
import com.foreigninone.backend.domain.paycheck.service.PaycheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paychecks")
@RequiredArgsConstructor
public class PaycheckController {

    private final PaycheckService paycheckService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaycheckResponse>>> getPaychecks(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        List<PaycheckResponse> response = paycheckService.getPaychecks(userId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{paycheckId}")
    public ResponseEntity<ApiResponse<PaycheckResponse>> getPaycheck(
            @PathVariable("paycheckId") Long paycheckId
    ) {
        PaycheckResponse response = paycheckService.getPaycheck(paycheckId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<PaycheckResponse>> analyzePaycheck(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @Valid @RequestBody PaycheckAnalyzeRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        PaycheckResponse response = paycheckService.analyzePaycheck(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "급여 분석이 완료되었습니다."));
    }

    @PostMapping("/{paycheckId}/explain")
    public ResponseEntity<ApiResponse<PaycheckExplainResponse>> explainPaycheck(
            @PathVariable("paycheckId") Long paycheckId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestBody(required = false) PaycheckExplainRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        PaycheckExplainResponse response = paycheckService.explainPaycheck(paycheckId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "AI 설명 조회가 완료되었습니다."));
    }
}
