package com.foreigninone.backend.domain.batch.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.batch.job.SalaryMonitoringJob;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.foreigninone.backend.domain.batch.dto.SalaryMonitoringBatchResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final SalaryMonitoringJob salaryMonitoringJob;

    @PostMapping("/salary-monitoring")
    public ResponseEntity<ApiResponse<SalaryMonitoringBatchResponse>> triggerSalaryMonitoring(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId
    ) {
        Long targetUserId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : headerUserId);
        SalaryMonitoringBatchResponse response = (targetUserId != null)
                ? salaryMonitoringJob.executeSalaryMonitoringForUser(targetUserId)
                : salaryMonitoringJob.executeSalaryMonitoring();

        return ResponseEntity.ok(ApiResponse.ok(response, String.format("급여 자동 감지 모니터링 배치가 완료되었습니다. (총 %d건 감지)", response.getProcessedCount())));
    }
}
