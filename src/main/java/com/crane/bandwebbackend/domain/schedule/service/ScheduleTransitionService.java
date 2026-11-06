package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-112·153: 상태와 version 검사를 같은 트랜잭션에서 수행한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleTransitionService {
	private final MembershipService membershipService;
	private final PracticeScheduleRepository repository;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;
	private final ScheduleEditService editService;

	/** 일정 확정한다 */
	@Transactional
	public ConfirmResult confirm(
		Long clubId,
		Long performanceId,
		Long scheduleId,
		Long actorUserId,
		String version,
		boolean confirmed
	) {
		// 관리자 권한과 일정·동아리·공연 상태를 확인한다.
		membershipService.requireAdmin(clubId, actorUserId);
		PracticeSchedule schedule = repository.findByIdAndPerformance_IdAndPerformance_Club_Id(
				scheduleId, performanceId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
		if (schedule.getPerformance().getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 일정을 확정할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		PerformanceStatus status = schedule.getPerformance().getStatus();
		if (status != PerformanceStatus.DRAFT && status != PerformanceStatus.ACTIVE
				&& status != PerformanceStatus.LOCKED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "공연 상태가 일정을 허용하지 않습니다.",
					"INVALID_STATE");
		}
		// 일정 revision과 임시 상태를 검사해 오래되거나 중복된 확정을 차단한다.
		if (version == null || !version.matches("[0-9]+")) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "version 형식이 올바르지 않습니다.");
		}
		if (!version.equals(String.valueOf(schedule.getVersion()))) {
			throw BusinessException.of(ErrorCode.VERSION_CONFLICT, "일정 버전이 달라졌습니다.");
		}
		if (schedule.getStatus() != ScheduleStatus.TENTATIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "임시 일정만 확정할 수 있습니다.", "INVALID_STATE");
		}
		if (!editService.hasAttendees(scheduleId)) {
			throw BusinessException.of(ErrorCode.RULE_VIOLATION, "확정 일정에 참석 대상이 없습니다.",
					"SCHEDULE_TARGET_EMPTY");
		}
		// 충돌 경고가 있으면 확인을 요구하고, 승인되면 상태와 감사 로그를 변경한다.
		List<Object> warnings = editService.warningsFor(clubId, performanceId, scheduleId, actorUserId);
		if (!warnings.isEmpty() && !confirmed) {
			throw BusinessException.confirmationRequired("경고를 확인한 뒤 다시 요청해 주세요.", warnings);
		}
		schedule.transitionTo(ScheduleStatus.CONFIRMED);
		auditRecorder.record(clubId, actorUserId, "SCHEDULE_STATUS_CHANGED", "PRACTICE_SCHEDULE", scheduleId, null, Map.of("from", "TENTATIVE", "to", "CONFIRMED"));
		entityManager.flush();
		return new ConfirmResult(schedule, warnings);
	}

	public record ConfirmResult(PracticeSchedule schedule, List<Object> warnings) {
	}

	/** 일정 변경한다 */
	@Transactional
	public PracticeSchedule change(
		Long clubId,
		Long performanceId,
		Long scheduleId,
		Long actorUserId,
		String version,
		ScheduleStatus target,
		String reason
	) {
		// 관리자 권한과 일정 버전을 확인한 뒤 허용된 상태 전이만 적용한다.
		membershipService.requireAdmin(clubId, actorUserId);
		PracticeSchedule schedule = repository.findByIdAndPerformance_IdAndPerformance_Club_Id(
				scheduleId, performanceId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
		if (schedule.getPerformance().getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 일정을 변경할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		PerformanceStatus performanceStatus = schedule.getPerformance().getStatus();
		if (performanceStatus != PerformanceStatus.DRAFT && performanceStatus != PerformanceStatus.ACTIVE
				&& performanceStatus != PerformanceStatus.LOCKED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "공연 상태가 일정 변경을 허용하지 않습니다.",
					"INVALID_STATE");
		}
		if (version == null || !version.matches("[0-9]+")) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "version 형식이 올바르지 않습니다.");
		}
		if (!version.equals(String.valueOf(schedule.getVersion()))) {
			throw BusinessException.of(ErrorCode.VERSION_CONFLICT, "일정 버전이 달라졌습니다.");
		}
		if (reason != null && (reason.isBlank() || reason.length() > 500)) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유는 1~500자여야 합니다.");
		}
		// 취소·복구 전 사유를 검증하고 이전·이후 상태를 감사 로그로 남긴다.
		ScheduleStatus previous = schedule.getStatus();
		if (target == ScheduleStatus.TENTATIVE && previous != ScheduleStatus.CONFIRMED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "확정 일정만 임시로 되돌릴 수 있습니다.",
					"NOT_CONFIRMED");
		}
		if (target == ScheduleStatus.CANCELED && previous != ScheduleStatus.TENTATIVE
				&& previous != ScheduleStatus.CONFIRMED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "취소할 수 없는 일정입니다.", "INVALID_STATE");
		}
		schedule.transitionTo(target);
		auditRecorder.record(clubId, actorUserId, "SCHEDULE_STATUS_CHANGED", "PRACTICE_SCHEDULE", scheduleId, reason, Map.of("from", previous.toString(), "to", target.toString()));
		entityManager.flush();
		return schedule;
	}
}
