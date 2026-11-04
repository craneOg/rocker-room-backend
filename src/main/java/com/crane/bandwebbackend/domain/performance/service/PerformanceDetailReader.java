package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.performance.dto.PerformanceDetailResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformancePolicyResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceDetailReader {
	private final PerformanceService performances;
	private final EntityManager entityManager;
	private final PracticeScheduleRepository schedules;

	/** 공연 정보를 조회한다 */
	@Transactional(readOnly = true)
	public PerformanceDetailResponse read(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 공연과 정책 접근 권한을 확인하고 두 엔티티를 조회한다.
		var performance = performances.detail(clubId, performanceId, actorUserId);
		var policy = performances.policy(clubId, performanceId, actorUserId);
		// 팀·후보곡·확정곡·승인 참여자 수를 상세 화면 집계로 묶는다.
		var counts = new PerformanceDetailResponse.Counts(
				count("SELECT COUNT(*) FROM team WHERE performance_id = :id", performanceId),
				count("SELECT COUNT(*) FROM song WHERE performance_id = :id AND status = 'PROPOSED'", performanceId),
				count("SELECT COUNT(*) FROM song WHERE performance_id = :id AND status = 'CONFIRMED'", performanceId),
				count("SELECT COUNT(*) FROM performance_member WHERE performance_id = :id AND status = 'APPROVED'",
						performanceId));
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		// 현재 이후의 가장 이른 확정 일정을 한국 시각 응답으로 변환한다.
		var next = schedules.findByPerformance_IdAndPerformance_Club_Id(performanceId, clubId).stream()
				.filter(schedule -> schedule.getStatus() == ScheduleStatus.CONFIRMED
						&& !schedule.getStartAt().isBefore(now))
				.min(Comparator.comparing(com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule::getStartAt))
				.map(schedule -> new PerformanceDetailResponse.NextSchedule(schedule.getId().toString(),
						schedule.getTitle(), schedule.getStartAt().atOffset(ZoneOffset.UTC)
								.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime()))
				.orElse(null);
		return new PerformanceDetailResponse(PerformanceResponse.from(performance),
				PerformancePolicyResponse.from(policy), counts, next);
	}

	/** 공연 상세 화면의 하위 데이터 개수를 집계한다. */
	private long count(String sql, Long performanceId) {
		return ((Number) entityManager.createNativeQuery(sql).setParameter("id", performanceId)
				.getSingleResult()).longValue();
	}
}
