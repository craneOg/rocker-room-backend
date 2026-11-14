package com.crane.bandwebbackend.domain.participant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ParticipantRemovalRequest {
    @JsonProperty("performanceVersion")
    @NotBlank(message = "공연 버전은 필수입니다.")
    @Pattern(regexp = "[0-9]+", message = "공연 버전은 0 이상의 정수여야 합니다.")
    private String performanceVersion;

    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;

    @JsonProperty("teamLeaderSuccessors")
    private List<Successor> teamLeaderSuccessors;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Successor {
        @JsonProperty("teamId")
        private String teamId;

        @JsonProperty("membershipId")
        private String membershipId;
    }
}
