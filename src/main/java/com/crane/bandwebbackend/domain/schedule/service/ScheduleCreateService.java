package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.membership.service.CoreEntityLookupService;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleCreateRequest;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleSong;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleTeam;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.util.Map;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-107: 일정과 대상 연결을 같이 저장한 뒤 경고를 계산한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleCreateService {
	private final MembershipService memberships;
	private final PerformanceService performances;
	private final PracticeScheduleRepository schedules;
	private final PracticeScheduleTeamRepository teams;
	private final PracticeScheduleSongRepository songs;
	private final ScheduleEditService warnings;
	private final CoreEntityLookupService entityLookup;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** 일정 생성한다 */
	@Transactional
	public ScheduleEditService.EditResult create(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		ScheduleCreateRequest request
	) {
		// 관리자 권한과 필수 시간 입력을 확인한다.
		memberships.requireAdmin(clubId, actorUserId);
		if (request == null || request.startAt() == null || request.endAt() == null) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "시작·종료 시각이 필요합니다.");
		}
		// 동아리와 공연 상태가 일정 생성을 허용하는지 검사한다.
		var performance = performances.require(clubId, performanceId);
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 일정을 만들 수 없습니다.",
					"CLUB_INACTIVE");
		}
		if (performance.getStatus() != PerformanceStatus.DRAFT
				&& performance.getStatus() != PerformanceStatus.ACTIVE
				&& performance.getStatus() != PerformanceStatus.LOCKED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "일정을 만들 수 없는 공연 상태입니다.",
					"INVALID_STATE");
		}
		if (request.status() == ScheduleStatus.CANCELED) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "취소 상태로 생성할 수 없습니다.");
		}
		ScheduleType type = request.eventType() == null ? ScheduleType.PRACTICE : request.eventType();
		ScheduleStatus status = request.status() == null ? ScheduleStatus.TENTATIVE : request.status();
		List<Long> teamIds = ids(request.teamIds(), "teamIds");
		List<Long> songIds = ids(request.songIds(), "songIds");
		// 요청된 팀과 곡이 모두 같은 공연 소속인지 엔티티 기준으로 검증한다.
		List<Team> linkedTeams = teamIds.stream().map(id -> {
			Team team = entityManager.find(Team.class, id);
			if (team == null || !team.getPerformance().getId().equals(performanceId)) {
				throw BusinessException.of(ErrorCode.NOT_FOUND, "같은 공연의 팀을 찾을 수 없습니다.");
			}
			return team;
		}).toList();
		List<Song> linkedSongs = songIds.stream().map(id -> {
			Song song = entityManager.find(Song.class, id);
			if (song == null || !song.getPerformance().getId().equals(performanceId)) {
				throw BusinessException.of(ErrorCode.NOT_FOUND, "같은 공연의 곡을 찾을 수 없습니다.");
			}
			return song;
		}).toList();
		// 제목이 없으면 공연일·대표 팀·일정 유형으로 사람이 읽을 수 있는 제목을 만든다.
		String title = request.title();
		if (title == null || title.isBlank()) {
			String prefix = performance.getPerformDate() == null ? ""
					: performance.getPerformDate().getYear() + "년 "
							+ performance.getPerformDate().getMonthValue() + "월 공연 ";
			String teamName = linkedTeams.isEmpty() ? "" : linkedTeams.get(0).getName()
					+ (linkedTeams.size() > 1 ? " 외 " + (linkedTeams.size() - 1) + "팀" : "") + " ";
			String suffix = switch (type) {
				case PRACTICE -> "합주";
				case REHEARSAL -> "리허설";
				case AUDITION -> "오디션";
				case ETC -> "일정";
			};
			title = prefix + teamName + suffix;
		}
		if (title.length() > 100 || (request.location() != null && request.location().length() > 200)
				|| (request.memo() != null && request.memo().length() > 500)) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "일정 문자열 길이가 유효하지 않습니다.");
		}
		// 오프셋 시각을 UTC로 변환해 일정 엔티티를 생성한다.
		PracticeSchedule schedule;
		try {
			schedule = PracticeSchedule.create(performance, type, status, title,
					request.startAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
					request.endAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
					request.location(), Boolean.TRUE.equals(request.forAllParticipants()), request.memo(),
					entityLookup.requireActive(actorUserId));
		} catch (IllegalArgumentException exception) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, exception.getMessage());
		}
		// 일정 ID를 확보한 뒤 팀·곡 대상 연결을 저장한다.
		schedules.saveAndFlush(schedule);
		for (Team team : linkedTeams) {
			teams.save(PracticeScheduleTeam.link(schedule, team));
		}
		for (Song song : linkedSongs) {
			songs.save(PracticeScheduleSong.link(schedule, song));
		}
		entityManager.flush();
		// 확정 일정은 실제 참석자가 있어야 하며, 저장 후 경고와 감사 로그를 반환한다.
		if (status == ScheduleStatus.CONFIRMED && !warnings.hasAttendees(schedule.getId())) {
			throw BusinessException.of(ErrorCode.RULE_VIOLATION, "확정 일정에 참석 대상이 없습니다.",
					"SCHEDULE_TARGET_EMPTY");
		}
		auditRecorder.record(clubId, actorUserId, "SCHEDULE_CREATED", "PRACTICE_SCHEDULE", schedule.getId(), null, Map.of());
		return new ScheduleEditService.EditResult(schedule,
				warnings.warningsFor(clubId, performanceId, schedule.getId(), actorUserId));
	}

	/** 문자열 대상 식별자 목록을 중복 없는 양의 정수 목록으로 변환한다. */
	private static List<Long> ids(List<String> raw, String field) {
		if (raw == null) {
			return List.of();
		}
		List<Long> result = new ArrayList<>();
		for (String item : raw) {
			result.add(ApiIds.positive(item, field));
		}
		if (new HashSet<>(result).size() != result.size()) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, field + "에 중복 값이 있습니다.");
		}
		return result;
	}
}
