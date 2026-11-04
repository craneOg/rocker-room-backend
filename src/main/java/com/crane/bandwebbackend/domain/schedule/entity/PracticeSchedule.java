package com.crane.bandwebbackend.domain.schedule.entity;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 참석자 = 연결 팀의 팀원 ∪ 연결 곡의 연주자(객원 포함), forAllParticipants면 승인된 참여자 전원
@Getter
@Entity
@Table(
		name = "practice_schedule",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_practice_schedule_id_performance",
				columnNames = {"id", "performance_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeSchedule extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "performance_id", nullable = false)
	private Performance performance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ScheduleType scheduleType;

	// TENTATIVE는 임원만 열람
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ScheduleStatus status;

	@Column(length = 100)
	private String title;

	// ck_practice_schedule_range: startAt < endAt
	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(length = 200)
	private String location;

	@Column(nullable = false)
	private boolean forAllParticipants;

	@Column(length = 500)
	private String memo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	private User createdBy;

	@Version
	@Column(nullable = false)
	private Long version;

	/** 일정 생성한다 */
	public static PracticeSchedule create(
		Performance performance,
		ScheduleType type,
		ScheduleStatus status,
		String title,
		LocalDateTime startAt,
		LocalDateTime endAt,
		String location,
		boolean forAllParticipants,
		String memo,
		User createdBy
	) {
		if (performance == null || type == null || status == null || createdBy == null
				|| status == ScheduleStatus.CANCELED) {
			throw new IllegalArgumentException("일정 생성 값이 유효하지 않습니다.");
		}
		checkRange(startAt, endAt);
		PracticeSchedule schedule = new PracticeSchedule();
		schedule.performance = performance;
		schedule.scheduleType = type;
		schedule.status = status;
		schedule.title = title;
		schedule.startAt = startAt;
		schedule.endAt = endAt;
		schedule.location = location;
		schedule.forAllParticipants = forAllParticipants;
		schedule.memo = memo;
		schedule.createdBy = createdBy;
		return schedule;
	}

	/** 일정 연결 항목을 교체한다 */
	public void replace(
		ScheduleType type,
		String title,
		LocalDateTime startAt,
		LocalDateTime endAt,
		String location,
		boolean forAllParticipants,
		String memo
	) {
		if (status == ScheduleStatus.CANCELED || type == null) {
			throw new IllegalStateException("취소된 일정을 수정할 수 없습니다.");
		}
		checkRange(startAt, endAt);
		this.scheduleType = type;
		this.title = title;
		this.startAt = startAt;
		this.endAt = endAt;
		this.location = location;
		this.forAllParticipants = forAllParticipants;
		this.memo = memo;
	}

	/** 일정 지정한 상태로 전환한다 */
	public void transitionTo(ScheduleStatus target) {
		if (target == null || target == status || status == ScheduleStatus.CANCELED) {
			throw new IllegalStateException("일정 상태를 변경할 수 없습니다.");
		}
		this.status = target;
	}

	private static void checkRange(LocalDateTime startAt, LocalDateTime endAt) {
		if (startAt == null || endAt == null || !startAt.isBefore(endAt)
				|| startAt.getMinute() % 30 != 0 || endAt.getMinute() % 30 != 0
				|| startAt.getSecond() != 0 || endAt.getSecond() != 0
				|| startAt.getNano() != 0 || endAt.getNano() != 0) {
			throw new IllegalArgumentException("일정은 유효한 30분 단위 구간이어야 합니다.");
		}
	}
}
