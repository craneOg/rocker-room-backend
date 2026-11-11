package com.crane.bandwebbackend.domain.membership.dto;

import java.util.List;

public record MembershipWithdrawalResult(MembershipResponse resource, List<Object> warnings, Effects effects) {
    public record Effects(
            List<String> approvedPerformanceMemberIds,
            List<String> affectedScheduleIds,
            List<String> affectedUserIds) {}
}
