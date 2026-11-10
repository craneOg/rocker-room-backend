package com.crane.bandwebbackend.domain.team.dto;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationSummary;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-063. candidateSongs는 팀원과 A에게만(그 밖에는 빈 목록)
@Getter
@Builder
public class TeamDetailResponse {

    private final TeamResponse team;
    private final List<TeamMemberResponse> members;
    private final List<AssignedSong> songs;
    private final List<CandidateSong> candidateSongs;

    // 이 팀이 담당하는 선정곡
    @Getter
    @Builder
    public static class AssignedSong {

        @IdString
        private final Long id;

        @IdString
        private final Long performanceId;

        private final String title;
        private final String artist;
        private final SongStatus status;

        @IdString
        private final Long assignedTeamId;

        @IdString
        private final Long candidateTeamId;

        private final Integer setlistOrder;
        private final int sessionCount;
        // 필요 편성이 없으면 NULL
        private final Boolean requirementsSatisfied;

        @IdString
        private final Long version;
    }

    // 이 팀 범위의 후보곡 + API-089 집계
    @Getter
    @Builder
    public static class CandidateSong {

        @IdString
        private final Long id;

        @IdString
        private final Long performanceId;

        private final String title;
        private final String artist;
        private final SongStatus status;

        @IdString
        private final Long assignedTeamId;

        @IdString
        private final Long candidateTeamId;

        private final Integer setlistOrder;
        private final EvaluationSummary evaluation;

        @IdString
        private final Long version;
    }
}
