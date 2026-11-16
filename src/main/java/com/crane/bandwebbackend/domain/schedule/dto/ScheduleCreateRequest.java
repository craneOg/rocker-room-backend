package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleCreateRequest {
    @JsonProperty("eventType")
    private ScheduleType eventType;

    @JsonProperty("status")
    private ScheduleStatus status;

    @JsonProperty("startAt")
    @NotNull(message = "시작 시각은 필수입니다.")
    private OffsetDateTime startAt;

    @JsonProperty("endAt")
    @NotNull(message = "종료 시각은 필수입니다.")
    private OffsetDateTime endAt;

    @JsonProperty("title")
    private String title;

    @JsonProperty("location")
    @Size(max = 200, message = "일정 장소는 200자 이하여야 합니다.")
    private String location;

    @JsonProperty("memo")
    @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
    private String memo;

    @JsonProperty("forAllParticipants")
    private Boolean forAllParticipants;

    @JsonProperty("teamIds")
    private List<String> teamIds;

    @JsonProperty("songIds")
    private List<String> songIds;
}
