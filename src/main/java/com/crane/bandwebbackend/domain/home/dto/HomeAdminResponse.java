package com.crane.bandwebbackend.domain.home.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 138
@Getter
@Builder
public class HomeAdminResponse {

    private final Pending pending;
    private final List<PerformanceCheck> performanceChecks;
    private final ClubStats clubStats;

    @Getter
    @Builder
    public static class Pending {
        private final long joinRequests;
        private final long invitesWaiting;
        private final long participationRequests;
        private final long teamReviews;
        private final long auditionReviews;

        public long total() {
            return joinRequests + invitesWaiting + participationRequests + teamReviews + auditionReviews;
        }
    }

    @Getter
    @Builder
    public static class ClubStats {
        private final long activeMembers;
        private final long admins;
        private final long graduated;
        private final long guests;
        private final Performances performances;
    }

    @Getter
    @Builder
    public static class Performances {
        private final long draft;
        private final long active;
        private final long locked;
        private final long finished;
    }

    // ACTIVE 공연 하나의 확정 점검. satisfied·total은 검사 항목(L-01~L-11) 수 기준
    @Getter
    @Builder
    public static class PerformanceCheck {
        @IdString
        private final Long performanceId;

        private final String performanceName;
        private final int satisfied;
        private final int total;
        private final List<CheckItem> items;
        private final ResponseRates responseRates;
    }

    // kind: L-01~06 BLOCKER, L-07~11 WARNING. count = 위반 줄 수
    @Getter
    @Builder
    public static class CheckItem {
        private final String checkId;
        private final String kind;
        private final boolean satisfied;
        private final long count;
    }

    // 열려 있지 않은 기능은 null
    @Getter
    @Builder
    public static class ResponseRates {
        private final Rate playability;
        private final Rate vote;
        private final Rate availability;
    }

    @Getter
    @Builder
    public static class Rate {
        private final long answered;
        private final long total;
    }
}
