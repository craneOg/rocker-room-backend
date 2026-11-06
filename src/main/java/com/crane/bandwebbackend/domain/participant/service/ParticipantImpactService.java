package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantImpactResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleLookup;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-059: 현재 팀·곡·일정 연결을 조회한다. 행은 수정하지 않는다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantImpactService {
	private final MembershipService memberships;
	private final PerformanceMemberRepository participants;
	private final ScheduleLookup schedules;
	private final EntityManager entityManager;

	/** 공연 참여 정보를 조회한다 */
	@Transactional(readOnly = true)
	public ParticipantImpactResponse read(
		Long clubId,
		Long performanceId,
		Long participantId,
		Long actorUserId
	) {
		// 대상 참여자는 본인 또는 관리자만 영향 정보를 볼 수 있다.
		PerformanceMember member = require(clubId, performanceId, participantId);
		if (!member.getMembership().getUser().getId().equals(actorUserId)) {
			memberships.requireAdmin(clubId, actorUserId);
		}
		// 팀·곡·일정 연결을 각각 조회해 제거 영향 목록을 구성한다.
		List<String> teams = ids("SELECT team_id FROM team_member WHERE performance_member_id = :id", participantId);
		List<String> songs = ids("SELECT DISTINCT song_id FROM song_session_member WHERE performance_member_id = :id",
				participantId);
		List<String> scheduleIds = schedules.attendedScheduleIds(performanceId, participantId).stream()
				.map(String::valueOf).toList();
		List<ParticipantImpactResponse.Warning> warnings = new ArrayList<>();
		// 각 곡에 다른 승인 연주자가 남는지 확인해 빈 곡 경고를 생성한다.
		for (String songId : songs) {
			long others = ((Number) entityManager.createNativeQuery("""
					SELECT COUNT(DISTINCT ssm.performance_member_id)
					FROM song_session_member ssm JOIN performance_member pm
					  ON pm.id = ssm.performance_member_id
					WHERE ssm.song_id = :songId AND pm.status = 'APPROVED'
					  AND pm.id <> :memberId
					""").setParameter("songId", Long.valueOf(songId))
					.setParameter("memberId", participantId).getSingleResult()).longValue();
			if (others == 0) {
				warnings.add(new ParticipantImpactResponse.Warning("SONG_WITHOUT_PLAYER", songId,
						"참여 해제 후 이 곡에 활동 연주자가 없습니다."));
			}
		}
		return new ParticipantImpactResponse(teams, songs, scheduleIds,
				List.of(member.getMembership().getUser().getId().toString()), warnings);
	}

	/** 공연 참여 존재와 접근 권한을 확인한다 */
	@Transactional(readOnly = true)
	public PerformanceMember require(
		Long clubId,
		Long performanceId,
		Long participantId
	) {
		// 동아리와 공연 경계를 쿼리에 포함해 다른 공연의 참여자 접근을 숨긴다.
		return participants.findByIdAndPerformance_IdAndClubId(participantId, performanceId, clubId)
				.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "참여자를 찾을 수 없습니다."));
	}

	/** 참여자 영향 쿼리 결과의 식별자를 문자열 목록으로 변환한다. */
	private List<String> ids(String sql, Long participantId) {
		@SuppressWarnings("unchecked")
		List<Number> values = entityManager.createNativeQuery(sql)
				.setParameter("id", participantId).getResultList();
		return values.stream().map(Number::longValue).map(String::valueOf).toList();
	}
}
