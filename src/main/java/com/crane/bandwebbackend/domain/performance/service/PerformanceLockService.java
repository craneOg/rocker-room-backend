package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.LockChecklistResponse;
import com.crane.bandwebbackend.domain.performance.dto.LockMutationResponse;
import com.crane.bandwebbackend.domain.performance.dto.LockValidationResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-050~052. 같은 L-01~L-11 계산을 검사·확정·홈 준비 점검에 제공한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceLockService {
    private final MembershipService memberships;
    private final PerformanceRepository performances;
    private final LockReadinessReader readiness;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 공연 확정 점검 항목을 조회한다 */
    @Transactional(readOnly = true)
    public LockChecklistResponse checklist(Long clubId, Long performanceId, Long actorUserId) {
        // 관리자 권한과 공연 존재를 확인한 뒤 현재 데이터를 체크리스트로 평가한다.
        memberships.requireAdmin(clubId, actorUserId);
        requirePerformance(clubId, performanceId);
        return new LockChecklistResponse(
                LockChecklistEvaluator.evaluate(readiness.read(performanceId)).getItems());
    }

    /** 공연 입력과 상태를 검증한다 */
    @Transactional(readOnly = true)
    public LockValidationResponse validate(Long clubId, Long performanceId, Long actorUserId, String version) {
        // 실제 변경 없이 동아리·상태·버전과 잠금 준비 조건을 미리 계산한다.
        memberships.requireAdmin(clubId, actorUserId);
        Performance performance = requirePerformance(clubId, performanceId);
        requireActiveClub(performance);
        requireActive(performance, version);
        var evaluation = LockChecklistEvaluator.evaluate(readiness.read(performanceId));
        return new LockValidationResponse(
                evaluation.getHardErrors(),
                evaluation.getWarnings(),
                evaluation.getNormalizedSetlist(),
                false,
                null,
                Map.of("performance", performance.getVersion().toString()));
    }

    /** 공연 최종 확정한다 */
    @Transactional
    public LockMutationResponse lock(
            Long clubId, Long performanceId, Long actorUserId, String version, boolean confirmed) {
        // 공연 행을 잠그고 미리보기와 동일한 상태·버전 조건을 다시 확인한다.
        memberships.requireAdmin(clubId, actorUserId);
        Performance performance = performances
                .findWithLockByIdAndClub_Id(performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
        requireActiveClub(performance);
        requireActive(performance, version);
        // 곡 수정과 동시에 진행될 때 순서 검증과 재배치를 같은 트랜잭션에 묶는다.
        entityManager
                .createNativeQuery("""
				SELECT id FROM song WHERE performance_id = :performanceId FOR UPDATE
				""")
                .setParameter("performanceId", performanceId)
                .getResultList();
        // 잠금 직전 스냅샷을 평가해 필수 오류는 차단하고 경고는 사용자 확인을 요구한다.
        var evaluation = LockChecklistEvaluator.evaluate(readiness.readLocked(performanceId));
        if (!evaluation.getHardErrors().isEmpty()) {
            var details = new ArrayList<ErrorDetail>();
            details.add(ErrorDetail.of("request", "LOCK_REQUIREMENTS_FAILED"));
            evaluation.getHardErrors().forEach(item -> {
                String field = switch (item.getTargetType()) {
                    case "SONG" -> "songId";
                    case "TEAM" -> "teamId";
                    case "PERFORMANCE_MEMBER" -> "performanceMemberId";
                    case "AUDITION" -> "auditionId";
                    default -> "request";
                };
                details.add(ErrorDetail.of(field + ":" + item.getTargetId(), item.getType()));
            });
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "셋리스트 확정 조건을 충족하지 못했습니다.", details);
        }
        if (!evaluation.getWarnings().isEmpty() && !confirmed) {
            throw BusinessException.confirmationRequired(
                    "경고를 확인한 뒤 다시 요청해 주세요.",
                    evaluation.getWarnings().stream()
                            .map(item -> item.asWarning())
                            .toList());
        }
        // UNIQUE(performance_id, setlist_order)를 피하기 위해 비운 뒤 1부터 다시 지정한다.
        entityManager
                .createNativeQuery("""
				UPDATE song SET setlist_order = NULL
				WHERE performance_id = :performanceId AND status = 'CONFIRMED'
				""")
                .setParameter("performanceId", performanceId)
                .executeUpdate();
        // 평가기가 정규화한 순서를 곡별로 다시 저장한다.
        for (var row : evaluation.getNormalizedSetlist()) {
            entityManager
                    .createNativeQuery("""
					UPDATE song SET setlist_order = :newOrder, version = version + 1
					WHERE id = :songId AND performance_id = :performanceId AND status = 'CONFIRMED'
					""")
                    .setParameter("newOrder", row.getOrder())
                    .setParameter("songId", Long.valueOf(row.getSongId()))
                    .setParameter("performanceId", performanceId)
                    .executeUpdate();
        }
        // 셋리스트 반영 후 공연 상태와 감사 로그를 함께 확정한다.
        performance.transitionTo(PerformanceStatus.LOCKED);
        // 검증 규칙 2.2: 확정 이벤트와 별개로 모든 공연 상태 전이를 기록한다.
        auditRecorder.record(
                clubId,
                actorUserId,
                "PERFORMANCE_STATUS_CHANGED",
                "PERFORMANCE",
                performanceId,
                null,
                Map.of("from", "ACTIVE", "to", "LOCKED"));
        auditRecorder.record(
                clubId,
                actorUserId,
                "SETLIST_CONFIRMED",
                "PERFORMANCE",
                performanceId,
                null,
                Map.of("confirmedSongCount", evaluation.getNormalizedSetlist().size()));
        entityManager.flush();
        return new LockMutationResponse(
                PerformanceResponse.from(performance), evaluation.getWarnings(), LockMutationResponse.Effects.none());
    }

    /** 동아리에 속한 공연을 조회하고 없으면 API 오류를 발생시킨다. */
    private Performance requirePerformance(Long clubId, Long performanceId) {
        return performances
                .findByIdAndClub_Id(performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
    }

    /** 공연이 잠금 가능한 활성 상태이고 버전이 일치하는지 확인한다. */
    private static void requireActive(Performance performance, String version) {
        if (performance.getStatus() != PerformanceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "진행 중 공연에서만 확정할 수 있습니다.");
        }
        if (version == null || !version.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "version이 유효하지 않습니다.");
        }
        if (!version.equals(performance.getVersion().toString())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
        }
    }

    /** 공연의 동아리가 활성 상태인지 확인한다. */
    private static void requireActiveClub(Performance performance) {
        if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 확정할 수 없습니다.");
        }
    }
}
