package com.crane.bandwebbackend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 009 요청. successors·teamLeaderSuccessors·confirmed는 null 불가라 보내면서 null이면 서비스가 400으로 거부한다.
// 보내지 않은 것과 구분하려고 UpdateProfileRequest처럼 "보냈는지"를 기록한다
@Getter
@NoArgsConstructor
public class AccountWithdrawalRequest {

    private String reason;
    private List<ClubSuccessor> successors = List.of();
    private List<TeamSuccessor> teamLeaderSuccessors = List.of();
    private Boolean confirmed = false;

    private boolean successorsNull;
    private boolean teamLeaderSuccessorsNull;
    private boolean confirmedNull;

    @JsonSetter("reason")
    private void readReason(String reason) {
        this.reason = reason;
    }

    @JsonSetter("successors")
    private void readSuccessors(List<ClubSuccessor> successors) {
        this.successors = successors;
        this.successorsNull = successors == null;
    }

    @JsonSetter("teamLeaderSuccessors")
    private void readTeamLeaderSuccessors(List<TeamSuccessor> teamLeaderSuccessors) {
        this.teamLeaderSuccessors = teamLeaderSuccessors;
        this.teamLeaderSuccessorsNull = teamLeaderSuccessors == null;
    }

    @JsonSetter("confirmed")
    private void readConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
        this.confirmedNull = confirmed == null;
    }

    @JsonIgnore
    public boolean isSuccessorsNull() {
        return successorsNull;
    }

    @JsonIgnore
    public boolean isTeamLeaderSuccessorsNull() {
        return teamLeaderSuccessorsNull;
    }

    @JsonIgnore
    public boolean isConfirmedNull() {
        return confirmedNull;
    }

    // 동아리 임원 후임: 지정할 사용자
    @Getter
    @NoArgsConstructor
    public static class ClubSuccessor {

        private String clubId;
        private String userId;
    }

    // 팀장 후임: 지정할 소속
    @Getter
    @NoArgsConstructor
    public static class TeamSuccessor {

        private String teamId;
        private String membershipId;
    }
}
