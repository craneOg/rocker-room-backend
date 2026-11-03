package com.crane.bandwebbackend.domain.evaluation.dto;

import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-089: 집계 + 이름 공개 범위에 따른 응답자 목록. namesVisible이 false인 쪽 목록은 NULL
@Getter
@Builder
public class SongEvaluationResponse {

    private final Long songId;
    private final VoteMethod voteMethod;

    @JsonUnwrapped
    private final EvaluationSummary summary;

    private final Pair namesVisible;
    private final List<EvaluationRows.Playability> playabilityResponses;
    private final List<EvaluationRows.Vote> voteResponses;
    private final NonResponders nonResponders;

    @Getter
    @Builder
    public static class Pair {

        private final boolean playability;
        private final boolean vote;
    }

    @Getter
    @Builder
    public static class NonResponders {

        private final List<EvaluationRows.Member> playability;
        private final List<EvaluationRows.Member> vote;
    }
}
