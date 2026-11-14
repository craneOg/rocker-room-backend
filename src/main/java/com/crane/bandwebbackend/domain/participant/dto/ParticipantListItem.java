package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ParticipantListItem {
    @JsonProperty("id")
    private String id;

    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("membershipId")
    private String membershipId;

    @JsonProperty("status")
    private PerformanceMemberStatus status;

    @JsonProperty("source")
    private PerformanceMemberSource source;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    @JsonProperty("memberType")
    private MemberType memberType;

    @JsonProperty("membershipStatus")
    private MembershipStatus membershipStatus;

    @JsonProperty("parts")
    private List<Part> parts;

    @JsonProperty("teamNames")
    private List<String> teamNames;

    @JsonProperty("playedSongCount")
    private long playedSongCount;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Part {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }
}
