package com.crane.bandwebbackend.domain.availability.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-100·101·102. 이어지는 30분 슬롯은 한 구간으로 합쳐 반환
@Getter
@Builder
public class AvailabilityResponse {

    private final Long performanceMemberId;
    private final OffsetDateTime from;
    private final OffsetDateTime to;
    private final List<Interval> slots;

    @Getter
    @Builder
    public static class Interval {

        private final OffsetDateTime startAt;
        private final OffsetDateTime endAt;
    }
}
