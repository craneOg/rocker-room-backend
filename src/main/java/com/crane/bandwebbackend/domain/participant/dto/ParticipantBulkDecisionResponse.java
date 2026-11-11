package com.crane.bandwebbackend.domain.participant.dto;

import java.util.List;

public record ParticipantBulkDecisionResponse(List<Success> succeeded, List<Failure> failed, Summary summary) {
    public record Success(String performanceMemberId) {}

    public record Failure(String performanceMemberId, String reason) {}

    public record Summary(int requested, int succeeded, int failed) {}
}
