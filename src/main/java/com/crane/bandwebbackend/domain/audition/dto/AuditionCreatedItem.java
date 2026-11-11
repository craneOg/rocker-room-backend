package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record AuditionCreatedItem(
        String id,
        String type,
        String songId,
        String songTitle,
        SongStatus songStatus,
        String partId,
        String applicantPerformanceMemberId,
        String linkedScheduleId,
        OffsetDateTime scheduledAt,
        String memo,
        AuditionResult result,
        String version) {

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static AuditionCreatedItem from(AuditionEntry entry) {
        AuditionItemResponse item = AuditionItemResponse.from(entry);
        return AuditionCreatedItem.builder()
                .id(item.id())
                .type(item.type())
                .songId(item.songId())
                .songTitle(item.songTitle())
                .songStatus(item.songStatus())
                .partId(item.partId())
                .applicantPerformanceMemberId(item.applicantPerformanceMemberId())
                .linkedScheduleId(item.linkedScheduleId())
                .scheduledAt(item.scheduledAt())
                .memo(item.memo())
                .result(item.result())
                .version(item.version())
                .build();
    }
}
