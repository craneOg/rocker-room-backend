package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.participant.dto.MyParticipationResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-054: 내 참여 카드용 집계는 현재 팀·곡·응답 행에서 계산한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MyParticipationReader {
    private final PerformanceMemberService members;
    private final PerformanceService performances;
    private final EntityManager entityManager;

    /** 공연 참여 정보를 조회한다 */
    @Transactional(readOnly = true)
    public MyParticipationResponse read(Long clubId, Long performanceId, Long actorUserId) {
        // 본인 참여 행과 공연 정책을 조회해 이후 진행도 노출 조건을 결정한다.
        var member = members.mine(clubId, performanceId, actorUserId);
        var policy = performances.policy(clubId, performanceId, actorUserId);
        Long id = member.getId();
        // 승인 팀과 팀장 여부를 조회해 본인 팀 목록으로 변환한다.
        @SuppressWarnings("unchecked")
        List<Object[]> teamRows =
                entityManager.createNativeQuery("""
				SELECT t.id, t.name, t.leader_member_id FROM team_member tm
				JOIN team t ON t.id = tm.team_id WHERE tm.performance_member_id = :id
				AND t.status = 'APPROVED' ORDER BY t.name
				""").setParameter("id", id).getResultList();
        List<MyParticipationResponse.MyTeam> teams = teamRows.stream()
                .map(row -> new MyParticipationResponse.MyTeam(
                        String.valueOf(row[0]), (String) row[1], row[2] != null && ((Number) row[2]).longValue() == id))
                .toList();
        // 확정 곡의 담당 파트와 세트리스트 순서를 조회한다.
        @SuppressWarnings("unchecked")
        List<Object[]> songRows =
                entityManager.createNativeQuery("""
				SELECT s.id, s.title, p.id, p.name, s.setlist_order
				FROM song_session_member ssm JOIN song s ON s.id = ssm.song_id
				JOIN part p ON p.id = ssm.part_id
				WHERE ssm.performance_member_id = :id AND s.status = 'CONFIRMED'
				ORDER BY s.setlist_order, s.id
				""").setParameter("id", id).getResultList();
        List<MyParticipationResponse.MySong> songs = songRows.stream()
                .map(row -> new MyParticipationResponse.MySong(
                        String.valueOf(row[0]),
                        (String) row[1],
                        String.valueOf(row[2]),
                        (String) row[3],
                        row[4] == null ? null : ((Number) row[4]).intValue()))
                .toList();
        // 참여 상태와 기능 활성 여부에 따라 후보곡·가불·투표·가능 시간 진행도를 계산한다.
        boolean applicant = member.getStatus() == PerformanceMemberStatus.APPLIED
                || member.getStatus() == PerformanceMemberStatus.APPROVED;
        boolean approved = member.getStatus() == PerformanceMemberStatus.APPROVED;
        MyParticipationResponse.Usage candidates = applicant && policy.getCandidateState() != FeatureState.DISABLED
                ? new MyParticipationResponse.Usage(
                        count("""
					SELECT COUNT(*) FROM song WHERE performance_id = :performanceId
					AND proposed_by_membership_id = :membershipId AND status <> 'WITHDRAWN'
					""", performanceId, member.getMembership().getId(), id),
                        policy.getCandidateLimitPerMember())
                : null;
        Long unanswered = applicant && policy.getPlayabilityState() != FeatureState.DISABLED
                ? count("""
					SELECT COUNT(*) FROM song s WHERE s.performance_id = :performanceId
					AND s.status IN ('PROPOSED','CONFIRMED')
					AND NOT EXISTS (SELECT 1 FROM song_playability sp WHERE sp.song_id = s.id
					  AND sp.performance_member_id = :memberId)
					""", performanceId, member.getMembership().getId(), id)
                : null;
        MyParticipationResponse.Usage votes = approved && policy.getVoteState() != FeatureState.DISABLED
                ? new MyParticipationResponse.Usage(
                        count("""
					SELECT COUNT(*) FROM song_vote WHERE performance_id = :performanceId
					AND performance_member_id = :memberId
					""", performanceId, member.getMembership().getId(), id), policy.getVoteMaxChoices())
                : null;
        Long availability = approved && policy.getAvailabilityState() != FeatureState.DISABLED
                ? count("""
					SELECT COUNT(*) FROM available_slot WHERE performance_member_id = :memberId
					""", performanceId, member.getMembership().getId(), id)
                : null;
        return new MyParticipationResponse(
                id.toString(),
                performanceId.toString(),
                member.getMembership().getId().toString(),
                member.getStatus(),
                member.getSource(),
                member.getCreatedAt()
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime(),
                member.getUpdatedAt()
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime(),
                teams,
                songs,
                new MyParticipationResponse.Progress(candidates, unanswered, votes, availability));
    }

    /** 참여 현황 집계를 위한 매개변수화된 개수 쿼리를 실행한다. */
    private long count(String sql, Long performanceId, Long membershipId, Long memberId) {
        // SQL이 실제로 포함한 이름 파라미터만 선택적으로 바인딩한다.
        var query = entityManager.createNativeQuery(sql);
        if (sql.contains(":performanceId")) {
            query.setParameter("performanceId", performanceId);
        }
        if (sql.contains(":membershipId")) {
            query.setParameter("membershipId", membershipId);
        }
        if (sql.contains(":memberId")) {
            query.setParameter("memberId", memberId);
        }
        return ((Number) query.getSingleResult()).longValue();
    }
}
