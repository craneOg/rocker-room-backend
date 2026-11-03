package com.crane.bandwebbackend.domain.song.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationSummary;
import com.crane.bandwebbackend.domain.playability.entity.PlayabilityAnswer;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.song.entity.SongPartRequirement;
import com.crane.bandwebbackend.global.response.Effects;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.response.Warning;
import com.crane.bandwebbackend.global.time.Kst;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 곡 응답 묶음(API-075·077·085 등)
public final class SongViews {

    private SongViews() {}

    // API-075 카드 = song 필드 + 카드 전용 필드
    @Getter
    @Builder
    public static class Card {

        @JsonUnwrapped
        private final SongResponse song;

        // API-089와 같은 집계(응답자 이름 없음)
        private final EvaluationSummary evaluation;
        // 선정곡 카드용. 후보곡에서는 NULL
        private final Integer sessionCount;
        // 필요 편성이 없으면 NULL
        private final Boolean requirementsSatisfied;
        private final List<MyPlayability> myPlayability;
        // 투표하지 않았거나 투표 대상이 아니면 NULL
        private final MyVote myVote;
    }

    @Getter
    @Builder
    public static class MyPlayability {

        private final Long partId;
        private final String partName;
        private final PlayabilityAnswer answer;
        private final String reason;
    }

    @Getter
    @Builder
    public static class MyVote {

        private final int score;
    }

    // API-075 응답. myVoteCount는 찬성 방식에서만(점수 방식 NULL)
    @Getter
    @Builder
    public static class ListResult {

        @JsonUnwrapped
        private final PageResponse<Card> page;

        private final int myProposalCount;
        private final Integer myVoteCount;
    }

    // API-077
    @Getter
    @Builder
    public static class Detail {

        private final SongResponse song;
        private final List<Session> sessions;
        private final List<Requirement> requirements;
        private final List<Schedule> schedules;
        private final List<Audition> auditions;
        private final EvaluationSummary evaluation;
    }

    @Getter
    @Builder
    public static class Session {

        private final Long id;
        private final Long performanceMemberId;
        private final Long membershipId;
        private final String displayName;
        private final Long partId;
        private final String partName;

        // M-03: 담당 팀의 팀원이 아닌 연주자(조회 때 계산)
        @Getter(onMethod_ = @JsonProperty("isSupport"))
        private final boolean isSupport;

        @Getter(onMethod_ = @JsonProperty("isGuest"))
        private final boolean isGuest;
    }

    @Getter
    @Builder
    public static class Requirement {

        private final Long partId;
        private final String partName;
        private final int requiredCount;

        public static Requirement of(SongPartRequirement r) {
            return Requirement.builder()
                    .partId(r.getPart().getId())
                    .partName(r.getPart().getName())
                    .requiredCount(r.getRequiredCount())
                    .build();
        }
    }

    // 취소된 일정도 status=CANCELED로 포함. 정렬·접기는 FE
    @Getter
    @Builder
    public static class Schedule {

        private final Long id;
        private final String title;
        private final ScheduleType scheduleType;
        private final ScheduleStatus status;
        private final OffsetDateTime startAt;
        private final OffsetDateTime endAt;
        private final String location;

        public static Schedule of(PracticeSchedule s) {
            return Schedule.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .scheduleType(s.getScheduleType())
                    .status(s.getStatus())
                    .startAt(Kst.fromUtc(s.getStartAt()))
                    .endAt(Kst.fromUtc(s.getEndAt()))
                    .location(s.getLocation())
                    .build();
        }
    }

    // 곡 심사(performanceMemberId NULL) / 세션 심사. result NULL = 심사 대기
    @Getter
    @Builder
    public static class Audition {

        private final Long id;
        private final Long performanceMemberId;
        private final Long partId;
        private final String partName;
        private final AuditionResult result;
        private final String comment;
        private final OffsetDateTime reviewedAt;
        private final OffsetDateTime createdAt;

        public static Audition of(AuditionEntry a) {
            return Audition.builder()
                    .id(a.getId())
                    .performanceMemberId(
                            a.getPerformanceMember() == null
                                    ? null
                                    : a.getPerformanceMember().getId())
                    .partId(a.getPart() == null ? null : a.getPart().getId())
                    .partName(a.getPart() == null ? null : a.getPart().getName())
                    .result(a.getResult())
                    .comment(a.getComment())
                    .reviewedAt(Kst.fromUtc(a.getReviewedAt()))
                    .createdAt(Kst.fromUtc(a.getCreatedAt()))
                    .build();
        }
    }

    // 변경 응답: {song, sessions?, requirements?, warnings, effects?}
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        private final SongResponse song;
        private final List<Session> sessions;
        private final List<Requirement> requirements;
        private final List<Warning> warnings;
        private final Effects effects;
    }
}
