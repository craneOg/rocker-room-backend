package com.crane.bandwebbackend.domain.schedule.controller;

import com.crane.bandwebbackend.domain.schedule.dto.CalendarEvent;
import com.crane.bandwebbackend.domain.schedule.service.CalendarReader;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-114·115. 인증 연결 후 api-v1 스위치를 켠다. */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarReader reader;
    private final CurrentActor actor;

    /** 일정 개인 일정을 조회한다 */
    @GetMapping("/me/calendar")
    public ResponseEntity<ApiResponse<Items>> personal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String clubIds,
            @RequestParam(defaultValue = "false") boolean includeCanceled,
            @RequestParam(defaultValue = "true") boolean includePerformanceDates,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(new Items(reader.personal(
                actor.requireUserId(), utc(from), utc(to), clubIds, includeCanceled, includePerformanceDates))));
    }

    /** 일정 연결된 동아리를 조회한다 */
    @GetMapping("/clubs/{clubId}/calendar")
    public ResponseEntity<ApiResponse<Items>> club(
            @PathVariable String clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String performanceId,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String songId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "false") boolean includeTentative,
            @RequestParam(defaultValue = "false") boolean includeCanceled,
            @RequestParam(defaultValue = "true") boolean includePerformanceDates,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(new Items(reader.club(
                ApiIds.positive(clubId, "clubId"),
                actor.requireUserId(),
                utc(from),
                utc(to),
                id(performanceId, "performanceId"),
                id(teamId, "teamId"),
                id(songId, "songId"),
                eventType,
                includeTentative,
                includeCanceled,
                includePerformanceDates))));
    }

    /** 선택 경로 식별자를 양의 정수로 검증해 변환한다. */
    private static Long id(String value, String field) {
        return value == null ? null : ApiIds.positive(value, field);
    }

    /** 오프셋 시각을 데이터베이스 저장용 UTC 지역 시각으로 변환한다. */
    private static java.time.LocalDateTime utc(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Items {
        @JsonProperty("items")
        private List<CalendarEvent> items;
    }
}
