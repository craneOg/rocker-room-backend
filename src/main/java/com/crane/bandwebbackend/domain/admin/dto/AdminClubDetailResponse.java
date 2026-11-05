package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 119. 가입 코드 값과 곡·평가 같은 동아리 내부 개인정보는 담지 않는다
@Getter
@Builder
public class AdminClubDetailResponse {

    private final ClubInfo club;
    private final Creator creator;
    private final boolean joinCodeEnabled;
    private final List<AdminEntry> admins;
    private final Counts counts;
    private final OffsetDateTime lastActiveAt;

    @Getter
    @Builder
    public static class ClubInfo {

        @IdString
        private final Long id;

        private final String name;
        private final String description;
        private final String clubType;
        private final String status;

        @Getter(onMethod_ = @JsonProperty("isSearchable"))
        private final boolean searchable;

        @IdString
        private final Long createdByUserId;

        private final OffsetDateTime createdAt;
        private final OffsetDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class Creator {

        @IdString
        private final Long userId;

        private final String name;
    }

    @Getter
    @Builder
    public static class AdminEntry {

        @IdString
        private final Long membershipId;

        @IdString
        private final Long userId;

        private final String displayName;
        private final String handle;
        private final String status;
        private final OffsetDateTime lastActiveAt;
    }

    @Getter
    @Builder
    public static class Counts {

        private final long members;
        private final long performances;
        private final MemberStatus memberStatus;
        private final PerformanceStatus performanceStatus;
    }

    @Getter
    @Builder
    public static class MemberStatus {

        private final long active;
        private final long onLeave;
        private final long graduated;
        private final long guests;
    }

    @Getter
    @Builder
    public static class PerformanceStatus {

        private final long draft;
        private final long active;
        private final long locked;
        private final long finished;
        private final long canceled;
    }
}
