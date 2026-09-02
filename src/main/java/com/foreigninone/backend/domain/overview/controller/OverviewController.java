package com.foreigninone.backend.domain.overview.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.overview.dto.DashboardResponse;
import com.foreigninone.backend.domain.overview.dto.RecordType;
import com.foreigninone.backend.domain.overview.dto.RecordsResponse;
import com.foreigninone.backend.domain.overview.service.OverviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api")
public class OverviewController {
    private final OverviewService service;

    public OverviewController(OverviewService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "year", required = false) Integer year) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.dashboard(resolveUser(userId, xUserId, demoUserId), year)));
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<RecordsResponse>> records(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long demoUserId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "type", required = false) RecordType type) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.records(resolveUser(userId, xUserId, demoUserId), type)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParameter(MethodArgumentTypeMismatchException ignored) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiResponse.error("사용자 ID·연도·기록 종류 형식을 확인하세요.", "INVALID_REQUEST"));
    }

    // Existing demo selection policy only. This is NOT authentication/authorization.
    // Do not serve real personal data through this policy on a public deployment.
    private long resolveUser(Long paramUserId, Long xUserId, Long demoUserId) {
        return paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (demoUserId != null ? demoUserId : 1L));
    }
}
