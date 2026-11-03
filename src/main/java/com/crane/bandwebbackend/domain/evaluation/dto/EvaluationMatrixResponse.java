package com.crane.bandwebbackend.domain.evaluation.dto;

import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * API-158 SONG-005 곡×사람 표. 응답하지 않은 칸은 행 자체가 없음.
 * 이름을 볼 수 없으면 members·nonResponders = NULL, playability·votes는 본인 행만
 */
@Getter
@Builder
public class EvaluationMatrixResponse {

    private final VoteMethod voteMethod;
    private final EvaluationType type;
    private final List<SongColumn> songs;
    private final List<EvaluationRows.Member> members;
    private final List<EvaluationRows.Playability> playability;
    private final List<EvaluationRows.Vote> votes;
    private final ResponseRate responseRate;
    private final List<EvaluationRows.Member> nonResponders;

    @Getter
    @Builder
    public static class SongColumn {

        private final Long id;
        private final String title;
        private final String artist;
        private final Long candidateTeamId;
    }

    // UI-25: total = 응답 대상자 수, answered = 한 곡이라도 응답(대리 입력 포함)한 사람 수
    @Getter
    @Builder
    public static class ResponseRate {

        private final int total;
        private final int answered;
    }
}
