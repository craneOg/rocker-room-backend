package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 오디션 심사 결과 입력·정정 요청. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class AuditionReviewRequest {
    @JsonProperty("result")
    @NotNull(message = "오디션 결과는 필수입니다.")
    private AuditionResult result;

    @JsonProperty("comment")
    @Size(max = 500, message = "심사 의견은 500자 이하여야 합니다.")
    private String comment;

    @JsonProperty("reason")
    @Size(max = 500, message = "정정 사유는 500자 이하여야 합니다.")
    private String reason;

    @JsonProperty("version")
    @NotBlank(message = "version은 필수입니다.")
    @Size(max = 100, message = "version은 100자 이하여야 합니다.")
    private String version;
}
