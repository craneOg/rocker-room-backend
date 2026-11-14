package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
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
public class AuditionCreatedItem {
    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("songId")
    private String songId;

    @JsonProperty("songTitle")
    private String songTitle;

    @JsonProperty("songStatus")
    private SongStatus songStatus;

    @JsonProperty("partId")
    private String partId;

    @JsonProperty("applicantPerformanceMemberId")
    private String applicantPerformanceMemberId;

    @JsonProperty("linkedScheduleId")
    private String linkedScheduleId;

    @JsonProperty("scheduledAt")
    private OffsetDateTime scheduledAt;

    @JsonProperty("memo")
    private String memo;

    @JsonProperty("result")
    private AuditionResult result;

    @JsonProperty("version")
    private String version;

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static AuditionCreatedItem from(AuditionEntry entry) {
        AuditionItemResponse item = AuditionItemResponse.from(entry);
        return AuditionCreatedItem.builder()
                .id(item.getId())
                .type(item.getType())
                .songId(item.getSongId())
                .songTitle(item.getSongTitle())
                .songStatus(item.getSongStatus())
                .partId(item.getPartId())
                .applicantPerformanceMemberId(item.getApplicantPerformanceMemberId())
                .linkedScheduleId(item.getLinkedScheduleId())
                .scheduledAt(item.getScheduledAt())
                .memo(item.getMemo())
                .result(item.getResult())
                .version(item.getVersion())
                .build();
    }
}
