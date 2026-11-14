package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PERF-01 목록의 Performance 필드. 상세 응답의 정책과 운영 건수는 별도 DTO가 필요하다. */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PerformanceResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("clubId")
    private String clubId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("performDate")
    private LocalDate performDate;

    @JsonProperty("location")
    private String location;

    @JsonProperty("preset")
    private PerformancePreset preset;

    @JsonProperty("status")
    private PerformanceStatus status;

    @JsonProperty("createdByUserId")
    private String createdByUserId;

    @JsonProperty("version")
    private String version;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    /** 공연 엔티티를 응답 데이터로 변환한다 */
    public static PerformanceResponse from(Performance performance) {
        return PerformanceResponse.builder()
                .id(performance.getId().toString())
                .clubId(performance.getClub().getId().toString())
                .title(performance.getTitle())
                .performDate(performance.getPerformDate())
                .location(performance.getLocation())
                .preset(performance.getPreset())
                .status(performance.getStatus())
                .createdByUserId(performance.getCreatedBy().getId().toString())
                .version(performance.getVersion().toString())
                .createdAt(performance
                        .getCreatedAt()
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime())
                .updatedAt(performance
                        .getUpdatedAt()
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime())
                .build();
    }
}
