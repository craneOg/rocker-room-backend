package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.home.service.SuccessionNoticeService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** P-06·07/U-05④: 처리 사유와 팀 상태에 따라 승계한 뒤 호출자가 참여 배정을 제거한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TeamLeaderSuccession {
    public enum Mode {
        SELF_WITHDRAWAL,
        ADMIN_REMOVAL,
        MEMBERSHIP_WITHDRAWAL,
        SERVICE_WITHDRAWAL
    }

    private final EntityManager entityManager;
    private final SuccessionNoticeService notices;

    /** 지정값은 처리한 팀마다 소비하며, 호출자는 전체 처리 후 남은 지정값을 거부한다. */
    public Set<Long> apply(Long performanceId, Long memberId, Map<Long, Long> specified, Mode mode) {
        @SuppressWarnings("unchecked")
        List<Object[]> teams = entityManager
                .createNativeQuery("""
                SELECT id, status FROM team
                WHERE performance_id = :performanceId AND leader_member_id = :memberId
                ORDER BY id FOR UPDATE
                """)
                .setParameter("performanceId", performanceId)
                .setParameter("memberId", memberId)
                .getResultList();
        Set<Long> affectedUsers = new HashSet<>();
        for (Object[] team : teams) {
            Long teamId = ((Number) team[0]).longValue();
            String status = (String) team[1];
            Long desired = specified.remove(teamId);
            if ("DRAFT".equals(status)) {
                deleteDraft(teamId, affectedUsers);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object[]> candidates = entityManager
                    .createNativeQuery("""
                    SELECT pm.id, m.id, m.user_id FROM team_member tm
                    JOIN performance_member pm ON pm.id = tm.performance_member_id
                    JOIN membership m ON m.id = pm.membership_id
                    WHERE tm.team_id = :teamId AND pm.id <> :memberId
                      AND pm.status IN ('APPLIED','APPROVED')
                      AND m.status IN ('ACTIVE','ON_LEAVE','GRADUATED')
                    ORDER BY tm.created_at, tm.id
                    """)
                    .setParameter("teamId", teamId)
                    .setParameter("memberId", memberId)
                    .getResultList();
            boolean self = mode == Mode.SELF_WITHDRAWAL;
            // SUBMITTED의 관리자·탈퇴 처리는 지정값과 무관하게 자동 승계한다.
            boolean useSpecified = self || !"SUBMITTED".equals(status);
            Object[] chosen;
            if (useSpecified && desired != null) {
                chosen = candidates.stream()
                        .filter(row -> ((Number) row[1]).longValue() == desired)
                        .findFirst()
                        .orElseThrow(() -> invalidSuccessor(mode));
            } else if (self && !candidates.isEmpty()) {
                throw invalidSuccessor(mode);
            } else {
                chosen = candidates.isEmpty() ? null : candidates.get(0);
            }
            entityManager
                    .createNativeQuery("""
                    UPDATE team SET leader_member_id = :leaderId, version = version + 1 WHERE id = :teamId
                    """)
                    .setParameter("leaderId", chosen == null ? null : ((Number) chosen[0]).longValue())
                    .setParameter("teamId", teamId)
                    .executeUpdate();
            if (chosen != null) {
                Long successorUserId = ((Number) chosen[2]).longValue();
                affectedUsers.add(successorUserId);
                Long clubId = entityManager
                        .getReference(Performance.class, performanceId)
                        .getClub()
                        .getId();
                notices.teamLeader(clubId, performanceId, teamId, successorUserId);
            }
        }
        return affectedUsers;
    }

    private void deleteDraft(Long teamId, Set<Long> affectedUsers) {
        @SuppressWarnings("unchecked")
        List<Number> users =
                entityManager.createNativeQuery("""
                SELECT m.user_id FROM team_member tm
                JOIN performance_member pm ON pm.id = tm.performance_member_id
                JOIN membership m ON m.id = pm.membership_id WHERE tm.team_id = :id
                """).setParameter("id", teamId).getResultList();
        users.forEach(user -> affectedUsers.add(user.longValue()));
        // UI-28/N4: DRAFT 팀 삭제는 N2의 팀 보존 예외. 곡·일정 엔티티와 연주자 배정은 보존한다.
        entityManager
                .createNativeQuery(
                        "UPDATE song SET candidate_team_id = NULL, version = version + 1 WHERE candidate_team_id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
        entityManager
                .createNativeQuery(
                        "UPDATE song SET assigned_team_id = NULL, version = version + 1 WHERE assigned_team_id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
        entityManager
                .createNativeQuery("DELETE FROM practice_schedule_team WHERE team_id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
        entityManager
                .createNativeQuery("UPDATE team SET leader_member_id = NULL WHERE id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
        entityManager
                .createNativeQuery("DELETE FROM team_member WHERE team_id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
        entityManager
                .createNativeQuery("DELETE FROM team WHERE id = :id")
                .setParameter("id", teamId)
                .executeUpdate();
    }

    public static BusinessException invalidSuccessor(Mode mode) {
        if (mode == Mode.MEMBERSHIP_WITHDRAWAL || mode == Mode.SERVICE_WITHDRAWAL) {
            return new BusinessException(ErrorCode.RULE_VIOLATION, "SUCCESSOR_NOT_ELIGIBLE", "팀장 후임으로 지정할 수 없습니다.");
        }
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "팀장 후임 지정이 유효하지 않습니다.",
                java.util.List.of(ErrorDetail.of("teamLeaderSuccessors", "SUCCESSOR_REQUIRED")));
    }
}
