package com.foreigninone.backend.domain.calendar.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventCreateRequest;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventResponse;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventUpdateRequest;
import com.foreigninone.backend.domain.calendar.entity.CalendarEvent;
import com.foreigninone.backend.domain.calendar.entity.EventType;
import com.foreigninone.backend.domain.calendar.entity.SourceType;
import com.foreigninone.backend.domain.calendar.repository.CalendarEventRepository;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getEvents(Long userId, LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            return calendarEventRepository.findByUser_UserIdAndStartAtBetweenOrderByStartAtAsc(userId, from, to)
                    .stream()
                    .map(CalendarEventResponse::from)
                    .toList();
        } else if (from != null) {
            return calendarEventRepository.findByUser_UserIdAndStartAtBetweenOrderByStartAtAsc(
                    userId, from, LocalDateTime.of(2099, 12, 31, 23, 59, 59))
                    .stream()
                    .map(CalendarEventResponse::from)
                    .toList();
        } else if (to != null) {
            return calendarEventRepository.findByUser_UserIdAndStartAtBetweenOrderByStartAtAsc(
                    userId, LocalDateTime.of(2000, 1, 1, 0, 0, 0), to)
                    .stream()
                    .map(CalendarEventResponse::from)
                    .toList();
        }
        return calendarEventRepository.findByUser_UserIdOrderByStartAtAsc(userId)
                .stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public CalendarEventResponse createPersonalEvent(Long userId, CalendarEventCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        CalendarEvent event = CalendarEvent.builder()
                .user(user)
                .eventType(EventType.PERSONAL)
                .title(request.getTitle())
                .description(request.getDescription())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .sourceType(SourceType.USER)
                .sourceId(null)
                .status("CONFIRMED")
                .build();

        CalendarEvent saved = calendarEventRepository.save(event);
        return CalendarEventResponse.from(saved);
    }

    @Transactional
    public CalendarEventResponse updatePersonalEvent(Long userId, Long eventId, CalendarEventUpdateRequest request) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));

        if (!event.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인의 일정만 수정할 수 있습니다.");
        }

        event.update(request.getTitle(), request.getDescription(), request.getStartAt(), request.getEndAt(), request.getStatus());
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void deletePersonalEvent(Long userId, Long eventId) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));

        if (!event.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인의 일정만 삭제할 수 있습니다.");
        }

        if (event.getSourceType() != SourceType.USER && event.getEventType() != EventType.PERSONAL) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "시스템에서 자동 생성된 일정은 직접 삭제할 수 없습니다.");
        }

        calendarEventRepository.delete(event);
    }

    @Transactional
    public void syncPaycheckEvent(Paycheck paycheck) {
        if (paycheck == null || paycheck.getUser() == null) {
            return;
        }

        User user = paycheck.getUser();
        Long sourceId = paycheck.getPaycheckId();

        Optional<CalendarEvent> existingOpt = calendarEventRepository
                .findByUser_UserIdAndSourceTypeAndSourceId(user.getUserId(), SourceType.PAYCHECK, sourceId);

        LocalDateTime eventStart = paycheck.getPaymentDate() != null
                ? paycheck.getPaymentDate()
                : (paycheck.getExpectedPaymentDate() != null
                ? paycheck.getExpectedPaymentDate().atTime(9, 0)
                : LocalDateTime.now());

        LocalDateTime eventEnd = eventStart.toLocalDate().atTime(23, 59, 59);

        // title: "8월 급여 입금"
        String title;
        try {
            if (paycheck.getPayPeriod() != null) {
                java.time.YearMonth ym = java.time.YearMonth.parse(paycheck.getPayPeriod(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                title = String.format("%d월 급여 입금", ym.getMonthValue());
            } else if (paycheck.getPaymentDate() != null) {
                title = String.format("%d월 급여 입금", paycheck.getPaymentDate().getMonthValue());
            } else {
                title = "급여 입금";
            }
        } catch (Exception e) {
            title = "급여 입금";
        }

        String description = paycheck.getAnalysisSummary() != null ? paycheck.getAnalysisSummary() : "급여 검증 결과";
        String eventStatus = "COMPLETED";

        if (existingOpt.isPresent()) {
            CalendarEvent event = existingOpt.get();
            event.update(title, description, eventStart, eventEnd, eventStatus);
            log.info("Updated CalendarEvent for Paycheck ID: {}", sourceId);
        } else {
            CalendarEvent newEvent = CalendarEvent.builder()
                    .user(user)
                    .eventType(EventType.PAYCHECK)
                    .title(title)
                    .description(description)
                    .startAt(eventStart)
                    .endAt(eventEnd)
                    .sourceType(SourceType.PAYCHECK)
                    .sourceId(sourceId)
                    .status(eventStatus)
                    .build();
            calendarEventRepository.save(newEvent);
            log.info("Created CalendarEvent for Paycheck ID: {}", sourceId);
        }
    }

    @Transactional
    public void syncPaydayEventsForUser(User user) {
        if (user == null || user.getPayday() == null) {
            return;
        }

        LocalDate now = LocalDate.now();
        for (int i = -2; i <= 6; i++) {
            LocalDate targetMonth = now.plusMonths(i);
            int day = Math.min(user.getPayday(), targetMonth.lengthOfMonth());
            LocalDate paydayDate = LocalDate.of(targetMonth.getYear(), targetMonth.getMonth(), day);

            LocalDateTime startAt = paydayDate.atTime(9, 0, 0);
            LocalDateTime endAt = paydayDate.atTime(23, 59, 59);

            Long sourceId = (long) (targetMonth.getYear() * 100 + targetMonth.getMonthValue());

            Optional<CalendarEvent> existingOpt = calendarEventRepository
                    .findByUser_UserIdAndSourceTypeAndSourceId(user.getUserId(), SourceType.SYSTEM, sourceId);

            String title = String.format("%d월 급여일", targetMonth.getMonthValue());
            String description = String.format("계약상 정기 급여일 (%d일)", user.getPayday());

            if (existingOpt.isPresent()) {
                CalendarEvent event = existingOpt.get();
                event.update(title, description, startAt, endAt, "SCHEDULED");
            } else {
                CalendarEvent event = CalendarEvent.builder()
                        .user(user)
                        .eventType(EventType.PAYDAY)
                        .title(title)
                        .description(description)
                        .startAt(startAt)
                        .endAt(endAt)
                        .sourceType(SourceType.SYSTEM)
                        .sourceId(sourceId)
                        .status("SCHEDULED")
                        .build();
                calendarEventRepository.save(event);
            }
        }
        log.info("Synced Payday events for user: {}", user.getUserId());
    }

    @Transactional
    public void syncExitEvent(User user) {
        if (user == null || user.getExpectedExitDate() == null) {
            return;
        }

        LocalDate exitDate = user.getExpectedExitDate();
        LocalDateTime startAt = exitDate.atTime(9, 0, 0);
        LocalDateTime endAt = exitDate.atTime(18, 0, 0);

        Long sourceId = 999999L;

        Optional<CalendarEvent> existingOpt = calendarEventRepository
                .findByUser_UserIdAndSourceTypeAndSourceId(user.getUserId(), SourceType.SYSTEM, sourceId);

        String title = "예상 출국일";
        String description = String.format("체류기간 만료 및 출국 예정일 (%s)", exitDate);

        if (existingOpt.isPresent()) {
            CalendarEvent event = existingOpt.get();
            event.update(title, description, startAt, endAt, "SCHEDULED");
        } else {
            CalendarEvent event = CalendarEvent.builder()
                    .user(user)
                    .eventType(EventType.EXIT)
                    .title(title)
                    .description(description)
                    .startAt(startAt)
                    .endAt(endAt)
                    .sourceType(SourceType.SYSTEM)
                    .sourceId(sourceId)
                    .status("SCHEDULED")
                    .build();
            calendarEventRepository.save(event);
        }
        log.info("Synced Exit event for user: {}", user.getUserId());
    }
}
