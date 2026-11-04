package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 요청자 기준 공연 접근 정보. 역할은 요청마다 membership.role로 판단(G-07)
@Getter
@RequiredArgsConstructor
public class PerformanceAccess {

    private final Performance performance;
    private final Membership membership;
    // 공연 참여자가 아니면 NULL
    private final PerformanceMember performanceMember;

    // A
    public boolean isAdmin() {
        return membership.getRole() == MembershipRole.CLUB_ADMIN;
    }

    public boolean isGuest() {
        return membership.getMemberType() == MemberType.GUEST;
    }

    public Long clubId() {
        return membership.getClub().getId();
    }

    public Long userId() {
        return membership.getUser().getId();
    }

    public Long performanceId() {
        return performance.getId();
    }

    public PerformanceStatus status() {
        return performance.getStatus();
    }

    public Long myMemberId() {
        return performanceMember == null ? null : performanceMember.getId();
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "임원만 할 수 있습니다.");
        }
    }

    // 희망자(APPLIED)도 후보곡 제출·가불 가능
    public PerformanceMember requireParticipant() {
        if (performanceMember == null || !performanceMember.isActiveParticipant()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "공연 참여자만 할 수 있습니다.");
        }
        return performanceMember;
    }

    public PerformanceMember requireApprovedParticipant() {
        if (performanceMember == null || performanceMember.getStatus() != PerformanceMemberStatus.APPROVED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "승인된 공연 참여자만 할 수 있습니다.");
        }
        return performanceMember;
    }

    // 종료·취소된 공연과 비활성 동아리(G-08)는 쓰기 불가
    public void requireEditable() {
        if (membership.getClub().getStatus() == ClubStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리입니다.");
        }
        requireStatus(PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE, PerformanceStatus.LOCKED);
    }

    // 허용 상태가 아니면 409 STATE_CONFLICT(reason: INVALID_STATE)
    public void requireStatus(PerformanceStatus... allowed) {
        if (!Arrays.asList(allowed).contains(performance.getStatus())) {
            throw BusinessException.invalidState("현재 공연 상태(" + performance.getStatus() + ")에서는 할 수 없습니다.");
        }
    }

    public boolean isLocked() {
        return performance.getStatus() == PerformanceStatus.LOCKED;
    }

    // X-02: LOCKED 공연의 예외 수정은 사유 필수
    public void requireReasonIfLocked(String reason) {
        if (isLocked() && (reason == null || reason.isBlank())) {
            throw BusinessException.reasonRequired();
        }
    }

    /** 정책 기능 열림 여부. OPEN이 아니면 closedReason, 마감 시각(UTC)이 지났으면 DEADLINE_PASSED. */
    public static void requireOpen(FeatureState state, LocalDateTime deadline, String closedReason) {
        if (state != FeatureState.OPEN) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, closedReason, "입력 기간이 아닙니다.");
        }
        if (deadline != null && !LocalDateTime.now(ZoneOffset.UTC).isBefore(deadline)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "DEADLINE_PASSED", "마감 시각이 지났습니다.");
        }
    }
}
