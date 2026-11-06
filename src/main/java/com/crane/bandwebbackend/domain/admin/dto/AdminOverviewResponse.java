package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 141. 동아리 내부 데이터(곡·팀 상세)는 담지 않는다
@Getter
@Builder
public class AdminOverviewResponse {

    private final int periodDays;
    private final Metrics metrics;
    private final List<TrendPoint> trend;
    private final JoinStatus joinStatus;
    private final List<Attention> attention;

    @Getter
    @Builder
    public static class Metrics {

        private final long activeClubs;
        private final long totalUsers;
        private final long activeUsers30d;
        private final long ongoingPerformances;
    }

    @Getter
    @Builder
    public static class TrendPoint {

        private final LocalDate date;
        private final long newUsers;
        private final long newClubs;
        private final long newPerformances;
        private final long newSchedules;
    }

    @Getter
    @Builder
    public static class JoinStatus {

        private final long requested;
        private final long approved;
        private final long rejected;
        private final long invited;
        private final long invitationAccepted;
        private final long invitationExpired;
    }

    @Getter
    @Builder
    public static class Attention {

        @IdString
        private final Long clubId;

        private final String clubName;
        private final String reason;
        private final OffsetDateTime lastAdminActiveAt;
    }
}
