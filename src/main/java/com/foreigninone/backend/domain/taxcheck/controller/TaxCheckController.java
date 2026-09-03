package com.foreigninone.backend.domain.taxcheck.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.taxcheck.dto.TaxCheckAnalyzeRequest;
import com.foreigninone.backend.domain.taxcheck.dto.TaxCheckResponse;
import com.foreigninone.backend.domain.taxcheck.dto.TaxCheckSimulateRequest;
import com.foreigninone.backend.domain.taxcheck.service.TaxCheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/api/tax-checks")
@RequiredArgsConstructor
public class TaxCheckController {
    private final TaxCheckService taxCheckService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxCheckResponse>>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "taxYear", required = false) Integer taxYear) {
        return ResponseEntity.ok(ApiResponse.ok(taxCheckService.getTaxChecks(resolveUser(userId, xUserId, demoUserId), taxYear)));
    }

    @GetMapping("/{taxCheckId}")
    public ResponseEntity<ApiResponse<TaxCheckResponse>> detail(
            @PathVariable("taxCheckId") Long taxCheckId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(taxCheckService.getTaxCheck(taxCheckId, resolveUser(userId, xUserId, demoUserId))));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<TaxCheckResponse>> analyze(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId,
            @Valid @RequestBody TaxCheckAnalyzeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(taxCheckService.analyze(resolveUser(userId, xUserId, demoUserId), request),
                "세금 준비 점검 결과를 저장했습니다. 세무 확정 판정이 아닙니다."));
    }

    @PostMapping("/{taxCheckId}/simulate")
    public ResponseEntity<ApiResponse<TaxCheckResponse>> simulate(
            @PathVariable("taxCheckId") Long taxCheckId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestBody TaxCheckSimulateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(taxCheckService.simulate(taxCheckId, resolveUser(userId, xUserId, demoUserId), request),
                "시뮬레이션 결과입니다. 원본과 DB 기록은 변경하지 않았습니다."));
    }

    // Keep malformed TaxCheck JSON/parameter handling local; leave other domains unchanged.
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidFormat(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.error("요청 JSON과 숫자·날짜 형식을 확인하세요.", "INVALID_REQUEST"));
    }

    private Long resolveUser(Long paramUserId, Long xUserId, Long demoUserId) {
        return paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (demoUserId != null ? demoUserId : 1L));
    }
}
