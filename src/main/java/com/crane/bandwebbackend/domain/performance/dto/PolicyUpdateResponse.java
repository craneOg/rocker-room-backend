package com.crane.bandwebbackend.domain.performance.dto;

import java.util.List;

public record PolicyUpdateResponse(PerformancePolicyResponse policy, List<Warning> warnings) {
    public record Warning(String type, String message) {}
}
