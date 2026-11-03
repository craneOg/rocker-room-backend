package com.crane.bandwebbackend.domain.playability.dto;

import com.crane.bandwebbackend.domain.playability.entity.PlayabilityAnswer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API-091·092. 파트는 경로 {partId}
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayabilityRequest {

    @NotNull
    private PlayabilityAnswer answer;

    // 불가능·보류 사유(선택)
    @Size(max = 500)
    private String reason;
}
