package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MyAuditionItem {
    @JsonProperty("id")
    private String id;

    @JsonProperty("clubId")
    private String clubId;

    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("songId")
    private String songId;

    @JsonProperty("songTitle")
    private String songTitle;

    @JsonProperty("partId")
    private String partId;

    @JsonProperty("partName")
    private String partName;

    @JsonProperty("scheduledAt")
    private OffsetDateTime scheduledAt;

    @JsonProperty("result")
    private AuditionResult result;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("judgedAt")
    private OffsetDateTime judgedAt;

    @JsonProperty("isLatest")
    @Getter(onMethod_ = @JsonProperty("isLatest"))
    private boolean isLatest;

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static MyAuditionItem from(AuditionEntry entry, boolean latest) {
        boolean session = entry.getPerformanceMember() != null;
        return MyAuditionItem.builder()
                .id(entry.getId().toString())
                .clubId(entry.getSong().getPerformance().getClub().getId().toString())
                .performanceId(entry.getPerformanceId().toString())
                .type(session ? "SESSION" : "SONG")
                .songId(entry.getSong().getId().toString())
                .songTitle(entry.getSong().getTitle())
                .partId(entry.getPart() == null ? null : entry.getPart().getId().toString())
                .partName(entry.getPart() == null ? null : entry.getPart().getName())
                .scheduledAt(
                        entry.getPracticeSchedule() == null
                                ? null
                                : kst(entry.getPracticeSchedule().getStartAt()))
                .result(entry.getResult())
                .comment(session ? entry.getComment() : null)
                .judgedAt(kst(entry.getReviewedAt()))
                .isLatest(latest)
                .build();
    }

    private static OffsetDateTime kst(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime();
    }
}
