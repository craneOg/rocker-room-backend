package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.home.service.SuccessionNoticeService;
import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import com.crane.bandwebbackend.domain.membership.dto.MembershipWithdrawalRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipWithdrawalResult;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.ParticipantAssignmentService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantRemovalPolicy;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.participant.service.TeamLeaderSuccession;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleLookup;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-030 임원 내보내기. */
@Service
@Transactional
@RequiredArgsConstructor
public class MembershipWithdrawalService {
    private final MembershipService memberships;
    private final MembershipRepository repository;
    private final ParticipationLookup participants;
    private final ParticipantAssignmentService assignments;
    private final ParticipantRemovalPolicy removalPolicy;
    private final TeamLeaderSuccession succession;
    private final SuccessionNoticeService notices;
    private final ScheduleLookup schedules;
    private final MembershipPartReader parts;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 소속 철회하거나 탈퇴한다 */
    @Transactional
    public MembershipWithdrawalResult withdraw(
            Long clubId, Long membershipId, Long actorUserId, MembershipWithdrawalRequest request) {
        // 마지막 관리자 판단이 동시에 실행되지 않도록 다른 소속 조회보다 먼저 동아리를 잠근다.
        Club club = entityManager.find(Club.class, clubId, LockModeType.PESSIMISTIC_WRITE);
        if (club == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다.");
        }
        if (club.getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 내보낼 수 없습니다.");
        }
        // 잠금 안에서 대상과 요청자 권한을 다시 읽어 본인 탈퇴와 관리자 내보내기를 구분한다.
        Membership target = memberships.require(clubId, membershipId);
        boolean selfWithdrawal = target.getUser().getId().equals(actorUserId);
        if (selfWithdrawal) {
            memberships.requireMember(clubId, actorUserId);
        } else {
            memberships.requireAdmin(clubId, actorUserId);
        }
        entityManager.refresh(target, LockModeType.PESSIMISTIC_WRITE);
        if (target.getStatus() != MembershipStatus.ACTIVE
                && target.getStatus() != MembershipStatus.ON_LEAVE
                && target.getStatus() != MembershipStatus.GRADUATED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_MEMBERSHIP_STATUS", "내보낼 수 없는 소속 상태입니다.");
        }
        // 본문 생략을 빈 요청으로 정규화하고 사유·후임 목록의 형식을 검증한다.
        if (request == null) {
            request = new MembershipWithdrawalRequest(null, null, List.of());
        }
        if (request.reason() != null && request.reason().length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason은 500자 이하여야 합니다.");
        }
        Map<Long, Long> specified = new HashMap<>();
        if (request.teamLeaderSuccessors() != null
                && request.teamLeaderSuccessors().size() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "팀장 후임은 최대 100건입니다.");
        }
        if (request.teamLeaderSuccessors() != null) {
            for (var item : request.teamLeaderSuccessors()) {
                if (item == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "팀장 후임 항목이 비어 있습니다.");
                }
                Long team = ApiIds.positive(item.teamId(), "teamLeaderSuccessors.teamId");
                Long candidate = ApiIds.positive(item.membershipId(), "teamLeaderSuccessors.membershipId");
                if (specified.put(team, candidate) != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "중복 팀장 후임입니다.");
                }
            }
        }
        // 응답에 담을 영향 일정과 알림 대상 사용자를 중복 없는 집합으로 수집한다.
        Set<Long> affectedSchedules = new HashSet<>();
        Set<Long> affectedUsers = new HashSet<>();
        affectedUsers.add(target.getUser().getId());
        // 진행 중 공연의 유효 참여를 철회하고, 팀장이면 먼저 후임을 배정한다.
        for (var participant : participants.membersOfMembership(membershipId).stream()
                .sorted(Comparator.comparing(member -> member.getPerformance().getId()))
                .toList()) {
            // X-02·04·07: 직접 철회와 같은 공연 잠금 안에서 최신 상태를 읽고 검증한다.
            entityManager.refresh(participant.getPerformance(), LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(participant);
            PerformanceStatus performanceStatus = participant.getPerformance().getStatus();
            if ((performanceStatus != PerformanceStatus.DRAFT
                            && performanceStatus != PerformanceStatus.ACTIVE
                            && performanceStatus != PerformanceStatus.LOCKED)
                    || (participant.getStatus() != PerformanceMemberStatus.APPLIED
                            && participant.getStatus() != PerformanceMemberStatus.APPROVED)) {
                continue;
            }
            removalPolicy.validate(participant.getPerformance(), participant.getId(), request.reason(), false);
            affectedSchedules.addAll(
                    schedules.attendedScheduleIds(participant.getPerformance().getId(), participant.getId()));
            affectedUsers.addAll(succession.apply(
                    participant.getPerformance().getId(),
                    participant.getId(),
                    specified,
                    TeamLeaderSuccession.Mode.MEMBERSHIP_WITHDRAWAL));
            // P-07/API-030: 진행 중 공연의 배정 연결만 제거하고 팀·곡과 과거 편성은 보존한다.
            assignments.removeAssignments(participant.getPerformance().getId(), participant.getId());
            participant.transitionTo(PerformanceMemberStatus.WITHDRAWN, LocalDateTime.now(ZoneOffset.UTC));
            auditRecorder.record(
                    clubId,
                    actorUserId,
                    "PERFORMANCE_MEMBER_WITHDRAWN",
                    "PERFORMANCE_MEMBER",
                    participant.getId(),
                    request.reason(),
                    Map.of("membershipId", membershipId));
            removalPolicy.recordException(
                    participant.getPerformance(), actorUserId, participant.getId(), request.reason());
        }
        // 사용되지 않은 후임 요청은 대상이 팀장이 아닌 팀을 지정한 것으로 판단한다.
        if (!specified.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RULE_VIOLATION, "SUCCESSOR_NOT_ELIGIBLE", "팀장 후임의 팀이 대상과 연결되지 않았습니다.");
        }
        boolean requiresSuccession = false;
        Long successorId = null;
        // 마지막 관리자라면 지정 후임 또는 가장 이른 활성 정회원을 관리자로 승계한다.
        if (target.getRole() == MembershipRole.CLUB_ADMIN) {
            boolean otherAdmin = repository.existsByClub_IdAndIdNotAndRoleAndStatusIn(
                    clubId,
                    membershipId,
                    MembershipRole.CLUB_ADMIN,
                    List.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE));
            if (!otherAdmin) {
                requiresSuccession = true;
                Membership successor = chooseSuccessor(clubId, membershipId, request.successorMembershipId());
                if (successor != null) {
                    successor.changeRole(MembershipRole.CLUB_ADMIN);
                    affectedUsers.add(successor.getUser().getId());
                    successorId = successor.getId();
                    notices.admin(clubId, successor.getUser().getId());
                }
            }
        }
        // 연관 참여와 승계 계산이 끝난 뒤 소속을 최종 탈퇴 상태로 변경한다.
        target.transitionTo(MembershipStatus.WITHDRAWN, LocalDateTime.now(ZoneOffset.UTC));
        if (requiresSuccession) {
            if (successorId == null) {
                club.deactivate();
                auditRecorder.record(
                        clubId,
                        actorUserId,
                        "CLUB_DEACTIVATED",
                        "CLUB",
                        clubId,
                        request.reason(),
                        Map.of("formerAdminMembershipId", membershipId));
            } else {
                auditRecorder.record(
                        clubId,
                        actorUserId,
                        "ADMIN_SUCCESSION",
                        "MEMBERSHIP",
                        successorId,
                        request.reason(),
                        Map.of("formerAdminMembershipId", membershipId));
            }
        }
        // 전체 변경 이력을 남기고 즉시 flush해 제약조건 오류도 이 트랜잭션에서 확인한다.
        auditRecorder.record(
                clubId, actorUserId, "MEMBERSHIP_WITHDRAWN", "MEMBERSHIP", membershipId, request.reason(), Map.of());
        entityManager.flush();
        return new MembershipWithdrawalResult(
                MembershipResponse.from(target, parts.partsOf(membershipId), actorUserId),
                List.of(),
                new MembershipWithdrawalResult.Effects(
                        List.of(),
                        affectedSchedules.stream().sorted().map(String::valueOf).toList(),
                        affectedUsers.stream().sorted().map(String::valueOf).toList()));
    }

    /** 마지막 임원의 후임을 지정값 또는 가입 승인 순서로 선택한다. */
    private Membership chooseSuccessor(Long clubId, Long targetId, String desired) {
        List<Membership> candidates = repository.findByClub_Id(clubId).stream()
                .filter(member -> !member.getId().equals(targetId))
                .filter(member ->
                        member.getStatus() == MembershipStatus.ACTIVE && member.getMemberType() == MemberType.REGULAR)
                .sorted(Comparator.comparing(Membership::getApprovedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Membership::getId))
                .toList();
        if (desired == null) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        Long wanted = ApiIds.positive(desired, "successorMembershipId");
        return candidates.stream()
                .filter(member -> member.getId().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RULE_VIOLATION, "SUCCESSOR_NOT_ELIGIBLE", "동아리 임원 후임으로 지정할 수 없습니다."));
    }
}
