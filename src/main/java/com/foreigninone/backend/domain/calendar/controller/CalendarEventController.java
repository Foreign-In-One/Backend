package com.foreigninone.backend.domain.calendar.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventCreateRequest;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventResponse;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventUpdateRequest;
import com.foreigninone.backend.domain.calendar.service.CalendarEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/calendar/events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getEvents(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        LocalDateTime start = from != null ? from.atStartOfDay() : null;
        LocalDateTime end = to != null ? to.atTime(23, 59, 59) : null;

        List<CalendarEventResponse> events = calendarEventService.getEvents(userId, start, end);
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> createEvent(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @Valid @RequestBody CalendarEventCreateRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        CalendarEventResponse response = calendarEventService.createPersonalEvent(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "일정이 등록되었습니다."));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> updateEvent(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody CalendarEventUpdateRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        CalendarEventResponse response = calendarEventService.updatePersonalEvent(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "일정이 수정되었습니다."));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable("eventId") Long eventId
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        calendarEventService.deletePersonalEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.okMessage("일정이 삭제되었습니다."));
    }
}
