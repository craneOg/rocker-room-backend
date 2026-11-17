package com.crane.bandwebbackend.domain.home.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

// API 117
@Getter
@Builder
public class ClubDashboardResponse {

    private final long pendingMemberships;
    private final long invitedMemberships;
    private final long activeMembers;
    private final Map<String, Long> performancesByStatus;
    private final long pendingTeams;
    private final long pendingPerformanceMembers;
    private final long warningsCount;
}
