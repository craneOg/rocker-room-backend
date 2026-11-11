package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.global.response.RevisionTag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.Builder;

/** API-147 목록 카드. 임원만 조회한다. */
@Builder
public record AuditionItemResponse(
        String id,
        String type,
        String songId,
        String songTitle,
        SongStatus songStatus,
        String partId,
        String partName,
        String applicantPerformanceMemberId,
        String applicantName,
        String candidateTeamName,
        String linkedScheduleId,
        OffsetDateTime scheduledAt,
        String memo,
        AuditionResult result,
        String comment,
        String judgedBy,
        String judgedByName,
        OffsetDateTime judgedAt,
        String version,
        OffsetDateTime createdAt) {

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static AuditionItemResponse from(AuditionEntry entry) {
        Membership applicant = entry.getPerformanceMember() == null
                ? null
                : entry.getPerformanceMember().getMembership();
        PracticeSchedule schedule = entry.getPracticeSchedule();
        return AuditionItemResponse.builder()
                .id(entry.getId().toString())
                .type(applicant == null ? "SONG" : "SESSION")
                .songId(entry.getSong().getId().toString())
                .songTitle(entry.getSong().getTitle())
                .songStatus(entry.getSong().getStatus())
                .partId(entry.getPart() == null ? null : entry.getPart().getId().toString())
                .partName(entry.getPart() == null ? null : entry.getPart().getName())
                .applicantPerformanceMemberId(
                        entry.getPerformanceMember() == null
                                ? null
                                : entry.getPerformanceMember().getId().toString())
                .applicantName(
                        applicant == null
                                ? null
                                : applicant.getDisplayName() == null
                                        ? applicant.getUser().getName()
                                        : applicant.getDisplayName())
                .candidateTeamName(
                        entry.getSong().getCandidateTeam() == null
                                ? null
                                : entry.getSong().getCandidateTeam().getName())
                .linkedScheduleId(schedule == null ? null : schedule.getId().toString())
                .scheduledAt(schedule == null ? null : kst(schedule.getStartAt()))
                .memo(entry.getMemo())
                .result(entry.getResult())
                .comment(entry.getComment())
                .judgedBy(
                        entry.getReviewedBy() == null
                                ? null
                                : entry.getReviewedBy().getId().toString())
                .judgedByName(
                        entry.getReviewedBy() == null
                                ? null
                                : entry.getReviewedBy().getName())
                .judgedAt(kst(entry.getReviewedAt()))
                .version(RevisionTag.from("a", entry.getId(), entry.getUpdatedAt()))
                .createdAt(kst(entry.getCreatedAt()))
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
