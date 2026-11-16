package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformancePolicyResponse;
import com.crane.bandwebbackend.domain.performance.dto.VoteMethodChangeRequest;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-043·044: 투표 전체 삭제는 공연 잠금 아래 한 트랜잭션에서 실행한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class VoteMethodChangeService {
    private final MembershipService memberships;
    private final PerformanceRepository performances;
    private final PerformancePolicyRepository policies;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 공연 입력과 상태를 검증한다 */
    @Transactional(readOnly = true)
    public Validation validate(Long clubId, Long performanceId, Long actorUserId, VoteMethodChangeRequest body) {
        // 쓰기 잠금 없이 변경 가능성과 삭제될 기존 투표 수를 계산한다.
        State state = state(clubId, performanceId, actorUserId, body, false);
        return new Validation(
                List.of(), new java.util.ArrayList<>(warnings(state)), Map.of("performance", state.getVersion()));
    }

    /** 공연 변경한다 */
    @Transactional
    public ChangeResult change(Long clubId, Long performanceId, Long actorUserId, VoteMethodChangeRequest body) {
        // 공연을 잠근 상태로 현재 정책과 영향 투표 수를 다시 읽는다.
        State state = state(clubId, performanceId, actorUserId, body, true);
        if (body.getReason() == null
                || body.getReason().isBlank()
                || body.getReason().length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "변경 사유는 1~500자여야 합니다.");
        }
        // 기존 투표가 삭제될 경우 명시적인 사용자 확인을 요구한다.
        List<Map<String, Object>> warnings = warnings(state);
        if (!warnings.isEmpty() && !Boolean.TRUE.equals(body.getConfirmed())) {
            throw BusinessException.confirmationRequired("경고를 확인한 뒤 다시 요청해 주세요.", warnings);
        }
        int removed = 0;
        boolean changed = state.getPolicy().getVoteMethod() != body.getVoteMethod();
        // 실제 방식이 달라질 때만 기존 투표 삭제·정책 변경·버전 증가를 수행한다.
        if (changed) {
            removed = entityManager
                    .createNativeQuery("DELETE FROM song_vote WHERE performance_id = :id")
                    .setParameter("id", performanceId)
                    .executeUpdate();
            state.getPolicy().changeVoteMethod(body.getVoteMethod());
            entityManager.lock(state.getPerformance(), LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            auditRecorder.record(
                    clubId,
                    actorUserId,
                    "VOTE_METHOD_CHANGED",
                    "PERFORMANCE",
                    performanceId,
                    body.getReason(),
                    Map.of("removedVoteCount", removed));
        }
        entityManager.flush();
        if (changed) {
            entityManager.refresh(state.getPerformance());
        }
        return new ChangeResult(PerformancePolicyResponse.from(state.getPolicy()), removed);
    }

    /** 투표 방식 변경 전 현재 정책과 기존 투표 영향을 계산한다. */
    private State state(
            Long clubId, Long performanceId, Long actorUserId, VoteMethodChangeRequest body, boolean write) {
        // 공통 권한·입력 검증 후 미리보기와 변경에 맞는 조회 방식을 선택한다.
        memberships.requireAdmin(clubId, actorUserId);
        if (body == null
                || body.getVersion() == null
                || !body.getVersion().matches("[0-9]+")
                || body.getVoteMethod() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "투표 방식 요청이 유효하지 않습니다.");
        }
        var performance = write
                ? performances.findWithLockByIdAndClub_Id(performanceId, clubId).orElse(null)
                : performances.findByIdAndClub_Id(performanceId, clubId).orElse(null);
        if (performance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다.");
        }
        if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 변경할 수 없습니다.");
        }
        if (performance.getStatus() != PerformanceStatus.DRAFT && performance.getStatus() != PerformanceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "정책을 변경할 수 없는 공연 상태입니다.");
        }
        if (!body.getVersion().equals(String.valueOf(performance.getVersion()))) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
        }
        PerformancePolicy policy = policies.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공연 정책을 찾을 수 없습니다."));
        // 같은 방식이면 영향이 없고, 다른 방식이면 삭제될 투표 수를 집계한다.
        long affected = policy.getVoteMethod() == body.getVoteMethod()
                ? 0
                : ((Number) entityManager
                                .createNativeQuery("SELECT COUNT(*) FROM song_vote WHERE performance_id = :id")
                                .setParameter("id", performanceId)
                                .getSingleResult())
                        .longValue();
        return new State(performance, policy, performance.getVersion().toString(), affected);
    }

    /** 기존 투표 삭제 등 방식 변경의 경고 목록을 생성한다. */
    private static List<Map<String, Object>> warnings(State state) {
        return state.getAffectedCount() == 0
                ? List.of()
                : List.of(Map.of(
                        "type", "VOTE_RESET", "affectedCount", state.getAffectedCount(), "message", "기존 투표가 삭제됩니다."));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    private static class State {
        @JsonProperty("performance")
        private Performance performance;

        @JsonProperty("policy")
        private PerformancePolicy policy;

        @JsonProperty("version")
        private String version;

        @JsonProperty("affectedCount")
        private long affectedCount;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Validation {
        @JsonProperty("hardErrors")
        private List<Object> hardErrors;

        @JsonProperty("warnings")
        private List<Object> warnings;

        @JsonProperty("versions")
        private Map<String, String> versions;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class ChangeResult {
        @JsonProperty("policy")
        private PerformancePolicyResponse policy;

        @JsonProperty("removedVoteCount")
        private int removedVoteCount;
    }
}
