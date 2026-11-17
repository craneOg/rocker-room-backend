package com.crane.bandwebbackend.domain.home.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 137
@Getter
@Builder
public class HomeResponse {

    private final List<Banner> banners;
    private final List<Todo> todos;
    private final List<UpcomingSchedule> upcomingSchedules;
    private final List<MyPerformance> myPerformances;

    // CLUB_INACTIVE·MEMBER_GRADUATED는 공연 정보 없이, PERFORMANCE_FINISH_DUE만 공연 정보와 함께
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Banner {
        private final String type;

        @IdString
        private final Long performanceId;

        private final String performanceName;
        private final LocalDate performanceDate;
    }

    // teamId는 TEAM_SUBMIT만, deadline은 마감이 없으면 null
    @Getter
    @Builder
    public static class Todo {
        private final String type;

        @IdString
        private final Long performanceId;

        private final String performanceName;
        private final String label;

        @IdString
        private final Long teamId;

        private final OffsetDateTime deadline;
    }

    @Getter
    @Builder
    public static class UpcomingSchedule {
        @IdString
        private final Long scheduleId;

        @IdString
        private final Long performanceId;

        private final OffsetDateTime startAt;
        private final OffsetDateTime endAt;
        private final String type;
        private final String title;
        private final String performanceName;
        private final String location;
    }

    @Getter
    @Builder
    public static class MyPerformance {
        @IdString
        private final Long performanceId;

        private final String name;
        private final String status;
        private final LocalDate performanceDate;
        private final String myStatus;
        private final List<String> teamNames;
        private final long songCount;
    }
}
