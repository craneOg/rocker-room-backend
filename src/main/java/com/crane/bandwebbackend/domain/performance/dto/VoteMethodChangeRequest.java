package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class VoteMethodChangeRequest {
    @JsonProperty("version")
    @NotBlank(message = "버전은 필수입니다.")
    @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.")
    private String version;

    @JsonProperty("voteMethod")
    @NotNull(message = "투표 방식은 필수입니다.")
    private VoteMethod voteMethod;

    @JsonProperty("confirmed")
    private Boolean confirmed;

    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;
}
