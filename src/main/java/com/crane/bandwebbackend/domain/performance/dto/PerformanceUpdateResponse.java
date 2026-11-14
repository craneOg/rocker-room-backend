package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-040: 기본 정보와 저장 후 경고. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PerformanceUpdateResponse {
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

    @JsonProperty("warnings")
    private List<Warning> warnings;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Warning {
        @JsonProperty("type")
        private String type;

        @JsonProperty("message")
        private String message;
    }

    /** 공연 엔티티를 응답 데이터로 변환한다 */
    public static PerformanceUpdateResponse from(PerformanceResponse base, List<Warning> warnings) {
        return new PerformanceUpdateResponse(
                base.getId(),
                base.getClubId(),
                base.getTitle(),
                base.getPerformDate(),
                base.getLocation(),
                base.getPreset(),
                base.getStatus(),
                base.getCreatedByUserId(),
                base.getVersion(),
                base.getCreatedAt(),
                base.getUpdatedAt(),
                warnings);
    }
}
