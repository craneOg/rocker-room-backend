package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.global.response.RevisionTag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Builder;

/** AUD-03. 곡 심사의 지원자·파트와 심사 대기 결과는 null로 응답한다. */
@Builder
public record AuditionResponse(
        String id,
        String performanceId,
        String songId,
        String performanceMemberId,
        String partId,
        String scheduleId,
        AuditionResult result,
        String reviewerUserId,
        OffsetDateTime reviewedAt,
        String note,
        String version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static AuditionResponse from(AuditionEntry entry) {
        return AuditionResponse.builder()
                .id(entry.getId().toString())
                .performanceId(entry.getPerformanceId().toString())
                .songId(entry.getSong().getId().toString())
                .performanceMemberId(
                        entry.getPerformanceMember() == null
                                ? null
                                : entry.getPerformanceMember().getId().toString())
                .partId(entry.getPart() == null ? null : entry.getPart().getId().toString())
                .scheduleId(
                        entry.getPracticeSchedule() == null
                                ? null
                                : entry.getPracticeSchedule().getId().toString())
                .result(entry.getResult())
                .reviewerUserId(
                        entry.getReviewedBy() == null
                                ? null
                                : entry.getReviewedBy().getId().toString())
                .reviewedAt(utc(entry.getReviewedAt()))
                .note(entry.getComment() == null ? entry.getMemo() : entry.getComment())
                .version(RevisionTag.from("a", entry.getId(), entry.getUpdatedAt()))
                .createdAt(utc(entry.getCreatedAt()))
                .updatedAt(utc(entry.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime();
    }
}
