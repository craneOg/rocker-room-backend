package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Builder;

/** PERF-01 목록의 Performance 필드. 상세 응답의 정책과 운영 건수는 별도 DTO가 필요하다. */
@Builder
public record PerformanceResponse(
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
		OffsetDateTime updatedAt
) {

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
				.createdAt(performance.getCreatedAt().atOffset(ZoneOffset.UTC)
						.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime())
				.updatedAt(performance.getUpdatedAt().atOffset(ZoneOffset.UTC)
						.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime())
				.build();
	}
}
