package com.crane.bandwebbackend.domain.participant.dto;

import java.util.List;

public record ParticipantImpactResponse(
        List<String> teamIds,
        List<String> songIds,
        List<String> scheduleIds,
        List<String> affectedUserIds,
        List<Warning> warnings) {
    public record Warning(String type, String targetId, String message) {}
}
