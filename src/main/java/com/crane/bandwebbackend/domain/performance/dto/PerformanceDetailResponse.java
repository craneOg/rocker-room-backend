package com.crane.bandwebbackend.domain.performance.dto;

import java.time.OffsetDateTime;

public record PerformanceDetailResponse(
        PerformanceResponse performance, PerformancePolicyResponse policy, Counts counts, NextSchedule nextSchedule) {
    public record Counts(long teams, long candidateSongs, long confirmedSongs, long participants) {}

    public record NextSchedule(String id, String title, OffsetDateTime startAt) {}
}
