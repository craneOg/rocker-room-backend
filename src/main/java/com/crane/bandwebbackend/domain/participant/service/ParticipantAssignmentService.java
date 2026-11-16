package com.crane.bandwebbackend.domain.participant.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** P-07: 팀장 승계와 영향 수집이 끝난 참여자의 배정 연결만 제거한다. */
@Service
@RequiredArgsConstructor
public class ParticipantAssignmentService {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeAssignments(Long performanceId, Long participantId) {
        // fk_team_leader_is_member 때문에 호출자가 팀장 교체를 먼저 완료해야 한다.
        entityManager
                .createNativeQuery("""
                DELETE FROM team_member
                WHERE performance_id = :performanceId AND performance_member_id = :memberId
                """)
                .setParameter("performanceId", performanceId)
                .setParameter("memberId", participantId)
                .executeUpdate();
        entityManager
                .createNativeQuery("""
                DELETE FROM song_session_member
                WHERE performance_id = :performanceId AND performance_member_id = :memberId
                """)
                .setParameter("performanceId", performanceId)
                .setParameter("memberId", participantId)
                .executeUpdate();
    }
}
