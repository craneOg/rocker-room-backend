package com.crane.bandwebbackend.domain.team.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 팀 API 요청 본문. 사람은 membershipId, 팀원 행은 경로의 teamMemberId로 지정
public final class TeamRequests {

    private TeamRequests() {}

    // API-062 팀 생성 / API-086 연주자로 팀 만들기(sourceSongId)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Create {

        // 있으면 API-086
        private Long sourceSongId;

        @Size(max = 100)
        private String name;

        @Valid
        private List<Member> members;

        private Long leaderMembershipId;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Member {

        @NotNull
        private Long membershipId;

        // 대표 파트(선택)
        private Long partId;
    }

    // API-064 팀명 변경
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Rename {

        @NotNull
        private Long version;

        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String reason;
    }

    // API-065 팀원 추가
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AddMember {

        @NotNull
        private Long version;

        @NotNull
        private Long membershipId;

        private Long partId;

        @Size(max = 500)
        private String reason;
    }

    // API-066 대표 파트 변경. partId NULL = 대표 파트 해제
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ChangePart {

        @NotNull
        private Long version;

        private Long partId;

        @Size(max = 500)
        private String reason;
    }

    // API-067 팀원 제외(본문으로 전달)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class RemoveMember {

        @NotNull
        private Long version;

        private boolean removeSessions;

        @Size(max = 500)
        private String reason;
    }

    // API-068 팀장 교체. NULL = 해제(A만)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Leader {

        @NotNull
        private Long version;

        private Long leaderMembershipId;

        @Size(max = 500)
        private String reason;
    }

    // 상태 전이 공통(069~074). reason은 반려 필수, confirmed는 확인 필요 규칙(0.1)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Transition {

        @NotNull
        private Long version;

        @Size(max = 500)
        private String reason;

        private boolean confirmed;
    }

    // API-155 기본 팀 지정
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Default {

        @NotNull
        private Long teamId;

        @NotNull
        private Long version;

        @Size(max = 500)
        private String reason;
    }
}
