package com.crane.bandwebbackend.domain.availability.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-100·101. 이어지는 30분 슬롯은 한 구간으로 합쳐 반환
@Getter
@Builder
public class AvailableTimesResponse {

    private final List<AvailabilityResponse.Interval> items;
}
