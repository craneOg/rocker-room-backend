package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantImpactResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantMutationResult;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-058·060: 참여 행은 보존하고 현재 유효 집계에서 제외한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantRemovalService {
    private final MembershipService memberships;
    private final PerformanceService performances;
    private final ParticipantImpactService impact;
    private final ParticipantAssignmentService assignments;
    private final ParticipantRemovalPolicy removalPolicy;
    private final TeamLeaderSuccession succession;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 공연 참여 제외한다 */
    @Transactional
    public ParticipantMutationResult remove(
            Long clubId,
            Long performanceId,
            Long participantId,
            Long actorUserId,
            ParticipantRemovalRequest request,
            boolean reject) {
        // 본문 생략을 빈 요청으로 정규화하고 공연 행을 잠가 동시 결정을 직렬화한다.
        if (request == null) {
            request = new ParticipantRemovalRequest(null, null, List.of());
        }
        final ParticipantRemovalRequest body = request;
        var performance = performances.requireLocked(clubId, performanceId);
        if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 변경할 수 없습니다.");
        }
        // 거절·타인 제거는 관리자에게, 본인 철회는 해당 소속에게 권한을 부여한다.
        PerformanceMember member = impact.require(clubId, performanceId, participantId);
        boolean self = member.getMembership().getUser().getId().equals(actorUserId);
        if (reject || !self) {
            memberships.requireAdmin(clubId, actorUserId);
        } else {
            memberships.requireMember(clubId, actorUserId);
        }
        // 거절은 신청 대기와 공연 버전을 요구하고 철회는 유효 참여 상태를 요구한다.
        if (reject) {
            if (body.performanceVersion() == null || !body.performanceVersion().matches("[0-9]+")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "performanceVersion이 필요합니다.");
            }
            if (!body.performanceVersion().equals(performance.getVersion().toString())) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
            }
            if (member.getStatus() != PerformanceMemberStatus.APPLIED) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "신청 대기 참여자만 거절할 수 있습니다.");
            }
        } else if (member.getStatus() != PerformanceMemberStatus.APPLIED
                && member.getStatus() != PerformanceMemberStatus.APPROVED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "철회할 수 없는 참여 상태입니다.");
        }
        // 공연 상태와 확정 공연의 필수 사유를 검증한다.
        PerformanceStatus status = performance.getStatus();
        if (status != PerformanceStatus.DRAFT
                && status != PerformanceStatus.ACTIVE
                && status != PerformanceStatus.LOCKED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "공연 상태가 변경을 허용하지 않습니다.");
        }
        if (status == PerformanceStatus.DRAFT && !reject && self) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "초안 공연에서는 임원만 참여자를 뺄 수 있습니다.");
        }
        if (body.reason() != null && body.reason().length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "사유는 500자 이하여야 합니다.");
        }
        // 변경 전에 팀·곡·일정 영향을 계산해 응답과 안전 검증에 사용한다.
        ParticipantImpactResponse affected = impact.read(clubId, performanceId, participantId, actorUserId);
        removalPolicy.validate(performance, participantId, body.reason(), reject);
        // 팀장 후임을 먼저 반영한 뒤 참여 상태를 거절 또는 철회로 변경한다.
        TeamLeaderSuccession.Mode mode =
                !reject && self ? TeamLeaderSuccession.Mode.SELF_WITHDRAWAL : TeamLeaderSuccession.Mode.ADMIN_REMOVAL;
        Map<Long, Long> specified = new HashMap<>();
        if (body.teamLeaderSuccessors() != null) {
            for (var item : body.teamLeaderSuccessors()) {
                if (item == null) {
                    throw TeamLeaderSuccession.invalidSuccessor(mode);
                }
                Long teamId = ApiIds.positive(item.teamId(), "teamLeaderSuccessors.teamId");
                Long membershipId = ApiIds.positive(item.membershipId(), "teamLeaderSuccessors.membershipId");
                if (specified.put(teamId, membershipId) != null) {
                    throw TeamLeaderSuccession.invalidSuccessor(mode);
                }
            }
        }
        Set<String> affectedUsers = new HashSet<>(affected.affectedUserIds());
        succession.apply(performanceId, participantId, specified, mode).forEach(id -> affectedUsers.add(id.toString()));
        if (!specified.isEmpty()) {
            throw TeamLeaderSuccession.invalidSuccessor(mode);
        }
        // P-07: 참여 이력은 보존하고 이전 팀·곡 배정은 재참여 시 복원되지 않도록 제거한다.
        assignments.removeAssignments(performanceId, participantId);
        member.transitionTo(
                reject ? PerformanceMemberStatus.REJECTED : PerformanceMemberStatus.WITHDRAWN,
                LocalDateTime.now(ZoneOffset.UTC));
        auditRecorder.record(
                clubId,
                actorUserId,
                reject ? "PERFORMANCE_MEMBER_REJECTED" : "PERFORMANCE_MEMBER_WITHDRAWN",
                "PERFORMANCE_MEMBER",
                participantId,
                body.reason(),
                Map.of("status", member.getStatus()));
        removalPolicy.recordException(performance, actorUserId, participantId, body.reason());
        entityManager.flush();
        return new ParticipantMutationResult(
                ParticipantResponse.from(member),
                affected.warnings().stream().map(warning -> (Object) warning).toList(),
                new ParticipantMutationResult.Effects(
                        List.of(),
                        affected.scheduleIds(),
                        affectedUsers.stream().sorted().toList()));
    }
}
