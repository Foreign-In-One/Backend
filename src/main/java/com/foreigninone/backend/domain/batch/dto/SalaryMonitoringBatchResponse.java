package com.foreigninone.backend.domain.batch.dto;

import com.foreigninone.backend.domain.paycheck.dto.PaycheckResponse;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryMonitoringBatchResponse {
    private int processedCount;
    private int createdCount;
    private int updatedCount;
    private List<PaycheckResponse> paychecks;
}
