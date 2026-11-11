package com.crane.bandwebbackend.domain.membership.dto;

import java.util.List;

public record JoinBulkDecisionResponse(List<Success> succeeded, List<Failure> failed, Summary summary) {
    public record Success(String membershipId) {}

    public record Failure(String membershipId, String reason) {}

    public record Summary(int requested, int succeeded, int failed) {}
}
