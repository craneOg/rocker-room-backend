package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SCH-01 목록 객체. 연결 팀·곡 ID는 ScheduleTargetReader가 페이지 단위로 공급한다. */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("eventType")
    private ScheduleType eventType;

    @JsonProperty("status")
    private ScheduleStatus status;

    @JsonProperty("title")
    private String title;

    @JsonProperty("startAt")
    private OffsetDateTime startAt;

    @JsonProperty("endAt")
    private OffsetDateTime endAt;

    @JsonProperty("location")
    private String location;

    @JsonProperty("memo")
    private String memo;

    @JsonProperty("forAllParticipants")
    private boolean forAllParticipants;

    @JsonProperty("teamIds")
    private List<String> teamIds;

    @JsonProperty("songIds")
    private List<String> songIds;

    @JsonProperty("performanceTitle")
    private String performanceTitle;

    @JsonProperty("version")
    private String version;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    /** 일정 엔티티를 응답 데이터로 변환한다 */
    public static ScheduleResponse from(PracticeSchedule schedule, List<String> teamIds, List<String> songIds) {
        String title = schedule.getTitle() == null || schedule.getTitle().isBlank()
                ? switch (schedule.getScheduleType()) {
                    case PRACTICE -> "합주";
                    case REHEARSAL -> "리허설";
                    case AUDITION -> "오디션";
                    case ETC -> "일정";
                }
                : schedule.getTitle();
        return ScheduleResponse.builder()
                .id(schedule.getId().toString())
                .performanceId(schedule.getPerformance().getId().toString())
                .eventType(schedule.getScheduleType())
                .status(schedule.getStatus())
                .title(title)
                .startAt(kst(schedule.getStartAt()))
                .endAt(kst(schedule.getEndAt()))
                .location(schedule.getLocation())
                .memo(schedule.getMemo())
                .forAllParticipants(schedule.isForAllParticipants())
                .teamIds(teamIds)
                .songIds(songIds)
                .performanceTitle(schedule.getPerformance().getTitle())
                .version(schedule.getVersion().toString())
                .createdAt(kst(schedule.getCreatedAt()))
                .updatedAt(kst(schedule.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime kst(java.time.LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
    }
}
