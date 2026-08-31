package com.foreigninone.backend.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.foreigninone.backend.domain.calendar.entity.CalendarEvent;
import com.foreigninone.backend.domain.calendar.entity.EventType;
import com.foreigninone.backend.domain.calendar.entity.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventResponse {
    private Long eventId;
    private EventType eventType;
    private String title;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;

    private SourceType sourceType;
    private Long sourceId;
    private String status;

    public static CalendarEventResponse from(CalendarEvent event) {
        return CalendarEventResponse.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .title(event.getTitle())
                .description(event.getDescription())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .status(event.getStatus())
                .build();
    }
}
