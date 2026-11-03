package com.crane.bandwebbackend.domain.team.dto;

import com.crane.bandwebbackend.domain.song.dto.SongResponse;
import com.crane.bandwebbackend.domain.team.entity.TeamStatus;
import com.crane.bandwebbackend.global.response.Effects;
import com.crane.bandwebbackend.global.response.Warning;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

public final class TeamResults {

    private TeamResults() {}

    // 팀 변경 응답: {team, song?(API-086), warnings, effects?}
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        private final TeamResponse team;
        private final SongResponse song;
        private final List<Warning> warnings;
        private final Effects effects;
    }

    // API-061: statusCounts는 A에게만(필터와 무관하게 공연 전체 기준)
    @Getter
    @Builder
    public static class ListResult {

        private final List<TeamResponse> content;
        private final Map<TeamStatus, Integer> statusCounts;
    }

    // API-154
    @Getter
    @Builder
    public static class DefaultName {

        private final String name;
    }
}
