package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceCreateRequest;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-038: 공연과 정책을 한 트랜잭션에서 만든다. */
@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceCreateService {
    private final MembershipService memberships;
    private final PerformanceRepository performances;
    private final PerformancePolicyRepository policies;
    private final PerformancePolicyEditService policyEdit;
    private final BandPerformanceInitializationService bandInitializer;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 공연 생성한다 */
    @Transactional
    public PerformanceResponse create(Long clubId, Long actorUserId, PerformanceCreateRequest request) {
        // 관리자 권한과 동아리 활성 상태를 먼저 확인한다.
        var actor = memberships.requireAdmin(clubId, actorUserId);
        if (actor.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 공연을 만들 수 없습니다.");
        }
        if (request == null
                || request.getTitle() == null
                || request.getTitle().isBlank()
                || request.getTitle().length() > 200
                || (request.getLocation() != null && request.getLocation().length() > 200)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "공연 기본 정보가 유효하지 않습니다.");
        }
        // 프리셋과 선택 정책 덮어쓰기의 구조 및 CUSTOM 필수 필드를 검증한다.
        PerformancePreset preset = request.getPreset() == null ? PerformancePreset.CUSTOM : request.getPreset();
        JsonNode overrides = request.getPolicy();
        if (overrides != null && !overrides.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "policy는 객체여야 합니다.");
        }
        if (preset == PerformancePreset.CUSTOM
                && (overrides == null
                        || !overrides.has("applicationState")
                        || !overrides.has("teamApplicationState")
                        || !overrides.has("candidateState")
                        || !overrides.has("playabilityState")
                        || !overrides.has("voteState")
                        || !overrides.has("availabilityState")
                        || !overrides.has("candidateScope"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CUSTOM 정책의 시작 상태가 필요합니다.");
        }
        // 공연 엔티티를 생성하고 ID가 필요한 정책보다 먼저 저장한다.
        Performance performance;
        try {
            performance = Performance.draft(
                    actor.getClub(),
                    request.getTitle(),
                    request.getPerformDate(),
                    request.getLocation(),
                    preset,
                    actor.getUser());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
        }
        performances.saveAndFlush(performance);
        // 프리셋 기본 정책을 만든 뒤 요청된 투표 방식과 세부 정책을 덮어쓴다.
        PerformancePolicy policy = PerformancePolicy.initial(performance, preset);
        if (overrides != null && overrides.has("voteMethod")) {
            try {
                policy.changeVoteMethod(
                        VoteMethod.valueOf(overrides.path("voteMethod").asText()));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "voteMethod가 유효하지 않습니다.");
            }
        }
        policies.saveAndFlush(policy);
        if (overrides != null && !overrides.isEmpty()) {
            ObjectNode patch = ((ObjectNode) overrides).deepCopy();
            patch.remove("voteMethod");
            patch.put("version", performance.getVersion().toString());
            policyEdit.update(clubId, performance.getId(), actorUserId, patch);
        }
        // BAND 프리셋은 활성 정회원과 기본 팀을 같은 트랜잭션에서 자동 구성한다.
        if (preset == PerformancePreset.BAND) {
            bandInitializer.initialize(performance, actorUserId);
        }
        // 생성 이벤트를 기록하고 모든 제약조건을 즉시 확인한 뒤 응답으로 변환한다.
        auditRecorder.record(
                clubId,
                actorUserId,
                "PERFORMANCE_CREATED",
                "PERFORMANCE",
                performance.getId(),
                null,
                Map.of("preset", preset));
        entityManager.flush();
        return PerformanceResponse.from(performance);
    }
}
