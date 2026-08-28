package com.foreigninone.backend.domain.user.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.calendar.service.CalendarEventService;
import com.foreigninone.backend.domain.user.dto.ProfileResponse;
import com.foreigninone.backend.domain.user.dto.ProfileUpdateRequest;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final CalendarEventService calendarEventService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ProfileResponse.from(user);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Integer oldPayday = user.getPayday();
        java.time.LocalDate oldExitDate = user.getExpectedExitDate();

        user.updateProfile(
                request.getEmploymentStatus(),
                request.getCompanyName(),
                request.getPayday(),
                request.getExpectedExitDate(),
                request.getLanguage()
        );

        // Side-effects: sync CalendarEvents
        if (user.getPayday() != null) {
            calendarEventService.syncPaydayEventsForUser(user);
        }

        if (user.getExpectedExitDate() != null) {
            calendarEventService.syncExitEvent(user);
        }

        return ProfileResponse.from(user);
    }
}
