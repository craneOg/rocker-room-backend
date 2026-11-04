package com.crane.bandwebbackend.domain.vote.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API-096·097. 찬성(APPROVAL) 방식은 1만, 점수(SCORE) 방식은 1~5(V-04)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteRequest {

    @NotNull
    private Integer score;
}
