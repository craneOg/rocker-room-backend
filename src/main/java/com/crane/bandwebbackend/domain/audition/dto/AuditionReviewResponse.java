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
public class AuditionReviewResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("songId")
    private String songId;

    @JsonProperty("songStatus")
    private SongStatus songStatus;

    @JsonProperty("partId")
    private String partId;

    @JsonProperty("partName")
    private String partName;

    @JsonProperty("applicantPerformanceMemberId")
    private String applicantPerformanceMemberId;

    @JsonProperty("applicantName")
    private String applicantName;

    @JsonProperty("result")
    private AuditionResult result;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("judgedBy")
    private String judgedBy;

    @JsonProperty("judgedByName")
    private String judgedByName;

    @JsonProperty("judgedAt")
    private OffsetDateTime judgedAt;

    @JsonProperty("version")
    private String version;

    /** 오디션 엔티티를 응답 데이터로 변환한다 */
    public static AuditionReviewResponse from(AuditionEntry entry) {
        AuditionItemResponse item = AuditionItemResponse.from(entry);
        return AuditionReviewResponse.builder()
                .id(item.getId())
                .type(item.getType())
                .songId(item.getSongId())
                .songStatus(item.getSongStatus())
                .partId(item.getPartId())
                .partName(item.getPartName())
                .applicantPerformanceMemberId(item.getApplicantPerformanceMemberId())
                .applicantName(item.getApplicantName())
                .result(item.getResult())
                .comment(item.getComment())
                .judgedBy(item.getJudgedBy())
                .judgedByName(item.getJudgedByName())
                .judgedAt(item.getJudgedAt())
                .version(item.getVersion())
                .build();
    }
}
