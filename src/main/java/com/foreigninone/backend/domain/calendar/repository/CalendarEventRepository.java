package com.foreigninone.backend.domain.calendar.repository;

import com.foreigninone.backend.domain.calendar.entity.CalendarEvent;
import com.foreigninone.backend.domain.calendar.entity.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findByUser_UserIdAndStartAtBetweenOrderByStartAtAsc(
            Long userId, LocalDateTime startAt, LocalDateTime endAt);

    List<CalendarEvent> findByUser_UserIdOrderByStartAtAsc(Long userId);

    Optional<CalendarEvent> findByUser_UserIdAndSourceTypeAndSourceId(
            Long userId, SourceType sourceType, Long sourceId);

    void deleteByUser_UserIdAndSourceTypeAndSourceId(
            Long userId, SourceType sourceType, Long sourceId);
}
