package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.club.entity.ClubType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-024~027: 가입·초대 결정은 소속 상태와 감사 기록을 함께 저장한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MembershipDecisionService {
    private final MembershipService members;
    private final PerformanceService performances;
    private final ParticipationLookup participants;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 소속 신청을 승인하거나 거절한다 */
    @Transactional
    public Membership decide(
            Long clubId,
            Long membershipId,
            Long actorUserId,
            MembershipStatus expectedSource,
            MembershipStatus target,
            String reason) {
        // 요청자가 기대한 원본 상태와 현재 데이터가 같은지 먼저 확인한다.
        Membership member = members.require(clubId, membershipId);
        if (member.getStatus() != expectedSource) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "처리할 수 없는 소속 상태입니다.");
        }
        if (member.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 처리할 수 없습니다.");
        }
        // 초대 수락·거절은 본인만, 가입 신청 승인·거절은 관리자만 수행한다.
        if (target == MembershipStatus.ACTIVE && member.getStatus() == MembershipStatus.INVITED) {
            if (!member.getUser().getId().equals(actorUserId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "초대받은 본인만 수락할 수 있습니다.");
            }
            if (!LocalDateTime.now(ZoneOffset.UTC).isBefore(members.invitationExpiresAt(member))) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVITATION_EXPIRED", "초대가 만료되었습니다.");
            }
        } else if (target == MembershipStatus.DECLINED && member.getStatus() == MembershipStatus.INVITED) {
            if (!member.getUser().getId().equals(actorUserId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "초대받은 본인만 거절할 수 있습니다.");
            }
        } else {
            members.requireAdmin(clubId, actorUserId);
        }
        // 원본 상태와 목표 상태의 허용 조합을 최종 확인한다.
        boolean allowed = (member.getStatus() == MembershipStatus.PENDING
                        && (target == MembershipStatus.ACTIVE || target == MembershipStatus.REJECTED))
                || (member.getStatus() == MembershipStatus.INVITED
                        && (target == MembershipStatus.ACTIVE || target == MembershipStatus.DECLINED));
        if (!allowed) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "처리할 수 없는 소속 상태입니다.");
        }
        // 상태를 전이하고 감사 로그용 이전 상태와 현재 시각을 보존한다.
        MembershipStatus before = member.getStatus();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        member.transitionTo(target, now);
        // BAND 동아리의 신규 승인자는 진행 중인 모든 공연에 자동 참여시킨다.
        if (before == MembershipStatus.PENDING
                && target == MembershipStatus.ACTIVE
                && member.getClub().getClubType() == ClubType.BAND) {
            // C-14: 밴드형 동아리는 승인 시 진행 중 공연에 자동 편입한다.
            performances.list(clubId, actorUserId).stream()
                    .filter(performance -> performance.getStatus() == PerformanceStatus.DRAFT
                            || performance.getStatus() == PerformanceStatus.ACTIVE
                            || performance.getStatus() == PerformanceStatus.LOCKED)
                    .forEach(performance -> participants.ensureAdminParticipation(performance, member, now));
        }
        // 목표 상태를 도메인 감사 이벤트 이름으로 변환해 변경 내역을 기록한다.
        String action = switch (target) {
            case ACTIVE -> before == MembershipStatus.PENDING ? "MEMBERSHIP_APPROVED" : "INVITATION_ACCEPTED";
            case REJECTED -> "MEMBERSHIP_REJECTED";
            case DECLINED -> "INVITATION_DECLINED";
            default -> throw new IllegalStateException("지원하지 않는 소속 결정입니다.");
        };
        auditRecorder.record(
                clubId,
                actorUserId,
                action,
                "MEMBERSHIP",
                membershipId,
                reason,
                Map.of("from", before.toString(), "to", target.toString()));
        return member;
    }
}
