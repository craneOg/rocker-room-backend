package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleCreateRequest(
        ScheduleType eventType,
        ScheduleStatus status,
        @NotNull(message = "시작 시각은 필수입니다.") OffsetDateTime startAt,
        @NotNull(message = "종료 시각은 필수입니다.") OffsetDateTime endAt,
        String title,
        @Size(max = 200, message = "일정 장소는 200자 이하여야 합니다.") String location,
        @Size(max = 500, message = "메모는 500자 이하여야 합니다.") String memo,
        Boolean forAllParticipants,
        List<String> teamIds,
        List<String> songIds) {}
