package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.dto.CalendarEvent;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-114·115: 캘린더는 페이지 없이 모든 결과를 돌려주되 2000건 한도를 강제한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CalendarReader {
	private final MembershipRepository memberships;
	private final MembershipService membershipService;
	private final PerformanceService performances;
	private final PerformanceMemberRepository participants;
	private final PracticeScheduleRepository schedules;
	private final PracticeScheduleTeamRepository teamLinks;
	private final PracticeScheduleSongRepository songLinks;

	/** 일정 개인 일정을 조회한다 */
	@Transactional(readOnly = true)
	public List<CalendarEvent> personal(
		Long actorUserId,
		LocalDateTime from,
		LocalDateTime to,
		String clubIds,
		boolean includeCanceled,
		boolean includeDates
	) {
		// 잘못된 시간 범위와 동아리 ID 필터를 요청 처리 초기에 차단한다.
		checkRange(from, to);
		Set<Long> clubs = clubIds == null ? null : parseIds(clubIds, "clubIds");
		List<CalendarEvent> result = new ArrayList<>();
		// 사용자의 유효한 동아리 소속만 순회하고, 요청된 동아리 필터를 적용한다.
		for (Membership membership : memberships.findByUser_Id(actorUserId)) {
			if (membership.getStatus() != MembershipStatus.ACTIVE
					&& membership.getStatus() != MembershipStatus.ON_LEAVE
					&& membership.getStatus() != MembershipStatus.GRADUATED) {
				continue;
			}
			Long clubId = membership.getClub().getId();
			if (clubs != null && !clubs.contains(clubId)) {
				continue;
			}
			// 각 동아리에서 승인된 공연 참여자로 등록된 공연만 개인 캘린더에 포함한다.
			for (Performance performance : performances.list(clubId, actorUserId)) {
				boolean approved = participants.findByPerformance_IdAndMembership_Id(
						performance.getId(), membership.getId())
						.map(member -> member.getStatus() == PerformanceMemberStatus.APPROVED).orElse(false);
				if (!approved) {
					continue;
				}
				// 확정 일정(선택 시 취소 일정 포함) 가운데 본인이 실제 참석 대상인 일정만 수집한다.
				for (PracticeSchedule schedule : schedules.findByPerformance_IdAndPerformance_Club_Id(
						performance.getId(), clubId)) {
					if (schedule.getStatus() != ScheduleStatus.CONFIRMED
							&& !(includeCanceled && schedule.getStatus() == ScheduleStatus.CANCELED)) {
						continue;
					}
					if (overlaps(schedule, from, to) && schedules.findAttendedScheduleIds(
							performance.getId(), participants.findByPerformance_IdAndMembership_Id(
									performance.getId(), membership.getId()).orElseThrow().getId())
							.contains(schedule.getId())) {
						result.add(CalendarEvent.schedule(schedule));
					}
				}
				// 요청한 경우 일정과 함께 공연일도 하나의 캘린더 이벤트로 추가한다.
				addDate(result, performance, from, to, includeDates);
			}
		}
		// 최대 결과 수를 검증하고 시작 시각 순서로 정렬한다.
		return finish(result);
	}

	/** 일정 연결된 동아리를 조회한다 */
	@Transactional(readOnly = true)
	public List<CalendarEvent> club(
		Long clubId,
		Long actorUserId,
		LocalDateTime from,
		LocalDateTime to,
		Long performanceId,
		Long teamId,
		Long songId,
		String eventTypes,
		boolean includeTentative,
		boolean includeCanceled,
		boolean includeDates
	) {
		// 조회 범위를 검증하고 요청자가 해당 동아리의 조회 가능한 회원인지 확인한다.
		checkRange(from, to);
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		// 임시 일정은 운영 정보이므로 동아리 임원에게만 공개한다.
		if (includeTentative && actor.getRole() != MembershipRole.CLUB_ADMIN) {
			throw new ApiException(ApiErrorCode.FORBIDDEN, "임시 일정은 임원만 조회할 수 있습니다.");
		}
		// 일정 유형과 팀·곡 필터를 실제 일정 ID 집합으로 미리 변환한다.
		Set<ScheduleType> types = eventTypes == null ? EnumSet.allOf(ScheduleType.class) : parseTypes(eventTypes);
		Set<Long> teamScheduleIds = teamId == null ? null : new HashSet<>(teamLinks.findByTeam_Id(teamId)
				.stream().map(link -> link.getPracticeSchedule().getId()).toList());
		Set<Long> songScheduleIds = songId == null ? null : new HashSet<>(songLinks.findBySong_Id(songId)
				.stream().map(link -> link.getPracticeSchedule().getId()).toList());
		List<CalendarEvent> result = new ArrayList<>();
		// 사용자가 볼 수 있는 공연을 순회하며 기간·유형·대상·상태 조건을 모두 적용한다.
		for (Performance performance : performances.list(clubId, actorUserId)) {
			if (performanceId != null && !performanceId.equals(performance.getId())) {
				continue;
			}
			// 게스트의 참석 대상 여부를 판단할 수 있도록 승인된 공연 참여자 ID를 구한다.
			Long participantId = participants.findByPerformance_IdAndMembership_Id(
					performance.getId(), actor.getId())
					.filter(member -> member.getStatus() == PerformanceMemberStatus.APPROVED)
					.map(member -> member.getId()).orElse(null);
			for (PracticeSchedule schedule : schedules.findByPerformance_IdAndPerformance_Club_Id(
					performance.getId(), clubId)) {
				if (!overlaps(schedule, from, to) || !types.contains(schedule.getScheduleType())
						|| (teamScheduleIds != null && !teamScheduleIds.contains(schedule.getId()))
						|| (songScheduleIds != null && !songScheduleIds.contains(schedule.getId()))
						|| (schedule.getStatus() == ScheduleStatus.TENTATIVE && !includeTentative)
						|| (schedule.getStatus() == ScheduleStatus.CANCELED && !includeCanceled)) {
					continue;
				}
				// 게스트에게는 본인이 참석 대상으로 연결된 일정만 노출한다.
				if (actor.getMemberType() == com.crane.bandwebbackend.domain.membership.entity.MemberType.GUEST
						&& (participantId == null || !schedules.findAttendedScheduleIds(
								performance.getId(), participantId).contains(schedule.getId()))) {
					continue;
				}
				result.add(CalendarEvent.schedule(schedule));
			}
			// 요청한 경우 공연일 이벤트도 동일한 결과에 합친다.
			addDate(result, performance, from, to, includeDates);
		}
		// 결과 건수 제한을 적용한 뒤 시간순으로 반환한다.
		return finish(result);
	}

	/** 공연 일자를 요청 범위 안의 캘린더 이벤트로 추가한다. */
	private static void addDate(List<CalendarEvent> events, Performance performance,
								LocalDateTime from, LocalDateTime to, boolean include) {
		// 공연일의 KST 자정을 저장 기준인 UTC 시각으로 환산해 조회 범위와 비교한다.
		if (include && performance.getPerformDate() != null) {
			LocalDateTime day = performance.getPerformDate().atStartOfDay().minusHours(9);
			if (!day.isBefore(from) && day.isBefore(to)) {
				events.add(CalendarEvent.performance(performance));
			}
		}
	}

	/** 일정이 조회 시간 범위와 겹치는지 판별한다. */
	private static boolean overlaps(PracticeSchedule schedule, LocalDateTime from, LocalDateTime to) {
		return schedule.getStartAt().isBefore(to) && schedule.getEndAt().isAfter(from);
	}

	/** 캘린더 이벤트를 시간순으로 정렬해 반환한다. */
	private static List<CalendarEvent> finish(List<CalendarEvent> events) {
		// 무제한 캘린더 조회가 과도한 응답을 만들지 않도록 상한을 강제한다.
		if (events.size() > 2000) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "조회 결과가 2000건을 초과합니다.",
					"RANGE_TOO_LARGE");
		}
		return events.stream().sorted(Comparator.comparing(CalendarEvent::startAt)
				.thenComparing(CalendarEvent::id)).toList();
	}

	/** 캘린더 조회 시작·종료 범위와 최대 기간을 검증한다. */
	private static void checkRange(LocalDateTime from, LocalDateTime to) {
		if (from == null || to == null || !from.isBefore(to)) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "조회 기간이 유효하지 않습니다.");
		}
	}

	/** 쉼표로 전달된 선택 식별자 필터를 양의 정수 집합으로 변환한다. */
	private static Set<Long> parseIds(String raw, String field) {
		// 각 값을 공통 ID 검증기로 검사하면서 중복 없는 필터 집합을 만든다.
		Set<Long> ids = new HashSet<>();
		Arrays.stream(raw.split(",", -1)).forEach(value -> ids.add(ApiIds.positive(value, field)));
		return ids;
	}

	/** 쉼표로 전달된 일정 유형 필터를 검증해 집합으로 변환한다. */
	private static Set<ScheduleType> parseTypes(String raw) {
		try {
			// 쉼표로 구분된 문자열을 허용된 ScheduleType 열거형 값으로 변환한다.
			EnumSet<ScheduleType> result = EnumSet.noneOf(ScheduleType.class);
			Arrays.stream(raw.split(",", -1)).forEach(value -> result.add(ScheduleType.valueOf(value)));
			if (result.isEmpty()) {
				throw new IllegalArgumentException();
			}
			return result;
		} catch (IllegalArgumentException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "eventType이 유효하지 않습니다.");
		}
	}
}
