package com.crane.bandwebbackend.domain.evaluation.dto;

import com.crane.bandwebbackend.domain.playability.entity.PlayabilityAnswer;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 곡 하나의 가불·투표 집계(API-089 집계 부분, 075 카드, 063 candidateSongs와 공통).
 * playability는 답별 서로 다른 응답자 수. scoreAverage·scoreDistribution은 SCORE 방식일 때만 값
 */
@Getter
@Builder
public class EvaluationSummary {

    private final Map<PlayabilityAnswer, Integer> playability;
    private final List<PartPlayability> playabilityByPart;
    private final int voteCount;
    private final Double scoreAverage;
    // 키 "1"~"5"
    private final Map<String, Integer> scoreDistribution;

    @Getter
    @Builder
    public static class PartPlayability {

        private final Long partId;
        private final String partName;
        private final int possible;
        private final int impossible;
        private final int undecided;
    }
}
