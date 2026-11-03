package com.crane.bandwebbackend.domain.evaluation.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.playability.entity.PlayabilityAnswer;
import com.crane.bandwebbackend.domain.playability.entity.SongPlayability;
import com.crane.bandwebbackend.domain.vote.entity.SongVote;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// 가불·투표 응답 행(API-089·090·095·158 공통). reason은 A에게만, 그 밖에는 NULL(V-09, UI-35)
public final class EvaluationRows {

    private EvaluationRows() {}

    @Getter
    @Builder
    public static class Playability {

        @IdString
        private final Long songId;

        @IdString
        private final Long performanceMemberId;

        private final String displayName;

        @IdString
        private final Long partId;

        private final String partName;
        private final PlayabilityAnswer answer;
        private final String reason;
        // V-08: 임원 대리 입력
        private final boolean enteredByAdmin;
        // API-090 A 전용: 현재 자격·범위 밖 응답(비유효 원본)이면 false, 그 밖에는 NULL
        private final Boolean valid;
        private final OffsetDateTime updatedAt;

        public static Playability of(SongPlayability p, boolean withReason, Boolean valid) {
            return Playability.builder()
                    .songId(p.getSong().getId())
                    .performanceMemberId(p.getPerformanceMember().getId())
                    .displayName(PerformanceAccessService.displayName(p.getPerformanceMember()))
                    .partId(p.getPart().getId())
                    .partName(p.getPart().getName())
                    .answer(p.getAnswer())
                    .reason(withReason ? p.getReason() : null)
                    .enteredByAdmin(p.isEnteredByAdmin())
                    .valid(valid)
                    .updatedAt(KstTime.toKst(p.getUpdatedAt()))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class Vote {

        @IdString
        private final Long songId;

        @IdString
        private final Long performanceMemberId;

        private final String displayName;
        // 찬성 방식 1, 점수 방식 1~5(API-096과 같은 이름)
        private final int score;
        private final boolean enteredByAdmin;
        private final Boolean valid;
        private final OffsetDateTime updatedAt;

        public static Vote of(SongVote v, Boolean valid) {
            return Vote.builder()
                    .songId(v.getSong().getId())
                    .performanceMemberId(v.getPerformanceMember().getId())
                    .displayName(PerformanceAccessService.displayName(v.getPerformanceMember()))
                    .score(v.getScore())
                    .enteredByAdmin(v.isEnteredByAdmin())
                    .valid(valid)
                    .updatedAt(KstTime.toKst(v.getUpdatedAt()))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class Member {

        @IdString
        private final Long performanceMemberId;

        private final String displayName;
        // API-158 nonResponders: 그 탭에서 응답하지 않은 후보곡 수
        private final Integer unrespondedSongCount;

        public static Member of(PerformanceMember pm) {
            return Member.builder()
                    .performanceMemberId(pm.getId())
                    .displayName(PerformanceAccessService.displayName(pm))
                    .build();
        }
    }
}
