package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.part.repository.MembershipPartRepository;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleDetailResponse;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleDetailReader {
	private final EntityManager entityManager;
	private final MembershipPartRepository parts;

	/** 일정 참여자 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<ScheduleDetailResponse.Participant> participants(Long scheduleId) {
		// 전체·팀·곡 대상 규칙 중 하나라도 만족하는 승인 공연 참여자를 조회한다.
		@SuppressWarnings("unchecked")
		List<Number> ids = entityManager.createNativeQuery("""
				SELECT pm.id FROM practice_schedule ps
				JOIN performance_member pm ON pm.performance_id = ps.performance_id AND pm.status = 'APPROVED'
				WHERE ps.id = :scheduleId AND (ps.for_all_participants = TRUE
					OR EXISTS (SELECT 1 FROM practice_schedule_team pst JOIN team_member tm
						ON tm.team_id = pst.team_id WHERE pst.practice_schedule_id = ps.id
						AND tm.performance_member_id = pm.id)
					OR EXISTS (SELECT 1 FROM practice_schedule_song pss JOIN song_session_member ssm
						ON ssm.song_id = pss.song_id WHERE pss.practice_schedule_id = ps.id
						AND ssm.performance_member_id = pm.id))
				""").setParameter("scheduleId", scheduleId).getResultList();
		// 배정 팀에는 없지만 연결 곡의 세션으로 참여하는 지원 연주자를 별도로 찾는다.
		@SuppressWarnings("unchecked")
		List<Number> supportIds = entityManager.createNativeQuery("""
				SELECT DISTINCT ssm.performance_member_id FROM practice_schedule_song pss
				JOIN song_session_member ssm ON ssm.song_id = pss.song_id
				JOIN song s ON s.id = pss.song_id
				WHERE pss.practice_schedule_id = :scheduleId AND s.assigned_team_id IS NOT NULL
				  AND NOT EXISTS (SELECT 1 FROM team_member tm
					  WHERE tm.team_id = s.assigned_team_id
						AND tm.performance_member_id = ssm.performance_member_id)
				""").setParameter("scheduleId", scheduleId).getResultList();
		Set<Long> support = new HashSet<>(supportIds.stream().map(Number::longValue).toList());
		// 참여자별 회원 표시명과 담당 파트를 조합해 상세 응답으로 변환한다.
		return ids.stream().map(Number::longValue).map(id -> {
			PerformanceMember member = entityManager.find(PerformanceMember.class, id);
			var membership = member.getMembership();
			String name = membership.getDisplayName() == null
					? membership.getUser().getName() : membership.getDisplayName();
			List<ScheduleDetailResponse.Part> memberParts = parts.findByMembership_Id(membership.getId())
					.stream().map(link -> new ScheduleDetailResponse.Part(
							link.getPart().getId().toString(), link.getPart().getName())).toList();
			return new ScheduleDetailResponse.Participant(membership.getUser().getId().toString(),
					id.toString(), membership.getId().toString(), name, memberParts,
					membership.getMemberType(), support.contains(id));
		}).toList();
	}
}
