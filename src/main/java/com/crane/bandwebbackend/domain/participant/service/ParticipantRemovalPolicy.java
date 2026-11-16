package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** X-02·04·07: 공연 행을 잠근 호출 트랜잭션에서 참여 제거 예외 규칙을 공유한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ParticipantRemovalPolicy {
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    public void validate(Performance performance, Long participantId, String reason, boolean reject) {
        if (performance.getStatus() != PerformanceStatus.LOCKED) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REASON_REQUIRED", "확정 공연 변경에는 사유가 필요합니다.");
        }
        if (reject) {
            return;
        }
        // X-04/L-03: 대상자의 연결을 제거한 뒤에도 확정 곡마다 승인 연주자가 남아야 한다.
        @SuppressWarnings("unchecked")
        List<Number> emptySongs = entityManager
                .createNativeQuery("""
                SELECT DISTINCT s.id FROM song s
                JOIN song_session_member target ON target.song_id = s.id
                  AND target.performance_member_id = :memberId
                WHERE s.performance_id = :performanceId AND s.status = 'CONFIRMED'
                  AND NOT EXISTS (SELECT 1 FROM song_session_member other
                    JOIN performance_member pm ON pm.id = other.performance_member_id
                    WHERE other.song_id = s.id AND other.performance_member_id <> :memberId
                      AND pm.status = 'APPROVED')
                """)
                .setParameter("memberId", participantId)
                .setParameter("performanceId", performance.getId())
                .getResultList();
        if (!emptySongs.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RULE_VIOLATION,
                    "참여 해제 후 선정곡에 활동 연주자가 없습니다.",
                    emptySongs.stream()
                            .map(id -> ErrorDetail.of("songId:" + id.longValue(), "SONG_WITHOUT_PLAYER"))
                            .toList());
        }
    }

    public void recordException(Performance performance, Long actorUserId, Long participantId, String reason) {
        if (performance.getStatus() == PerformanceStatus.LOCKED) {
            auditRecorder.record(
                    performance.getClub().getId(),
                    actorUserId,
                    "LOCKED_EXCEPTION_EDIT",
                    "PERFORMANCE_MEMBER",
                    participantId,
                    reason,
                    Map.of());
        }
    }
}
