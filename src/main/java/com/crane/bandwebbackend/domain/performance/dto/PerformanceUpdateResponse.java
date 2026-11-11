package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** API-040: 기본 정보와 저장 후 경고. */
public record PerformanceUpdateResponse(
        String id,
        String clubId,
        String title,
        LocalDate performDate,
        String location,
        PerformancePreset preset,
        PerformanceStatus status,
        String createdByUserId,
        String version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<Warning> warnings) {
    public record Warning(String type, String message) {}

    /** 공연 엔티티를 응답 데이터로 변환한다 */
    public static PerformanceUpdateResponse from(PerformanceResponse base, List<Warning> warnings) {
        return new PerformanceUpdateResponse(
                base.id(),
                base.clubId(),
                base.title(),
                base.performDate(),
                base.location(),
                base.preset(),
                base.status(),
                base.createdByUserId(),
                base.version(),
                base.createdAt(),
                base.updatedAt(),
                warnings);
    }
}
