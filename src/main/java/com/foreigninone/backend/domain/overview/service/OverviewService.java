package com.foreigninone.backend.domain.overview.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.overview.dto.DashboardResponse;
import com.foreigninone.backend.domain.overview.dto.RecordType;
import com.foreigninone.backend.domain.overview.dto.RecordsResponse;
import com.foreigninone.backend.domain.overview.repository.OverviewReadRepository;
import com.foreigninone.backend.domain.overview.rule.OverviewRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class OverviewService {
    private final OverviewReadRepository repository;

    public OverviewService(OverviewReadRepository repository) {
        this.repository = repository;
    }

    public DashboardResponse dashboard(long userId, Integer requestedYear) {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        int year = requestedYear == null ? currentYear : requestedYear;
        if (year < 2000 || year > currentYear) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "연도는 2000년부터 현재 연도까지 선택하세요.");
        }
        requireUser(userId);
        var records = OverviewRules.newestFirst(repository.findAllByUserId(userId));
        return new DashboardResponse(year, OverviewRules.paySummary(year, records),
                OverviewRules.latest(records, RecordType.PAYCHECK),
                OverviewRules.latest(records, RecordType.TAX_CHECK),
                OverviewRules.latest(records, RecordType.EXIT_CHECK), records.stream().limit(3).toList());
    }

    public RecordsResponse records(long userId, RecordType filter) {
        requireUser(userId);
        return OverviewRules.records(OverviewRules.newestFirst(repository.findAllByUserId(userId)), filter);
    }

    private void requireUser(long userId) {
        if (userId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "사용자 ID는 양수여야 합니다.");
        if (!repository.userExists(userId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
}
