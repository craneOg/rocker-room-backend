package com.crane.bandwebbackend.domain.participant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-056: 검증 실패 시 전체 트랜잭션을 취소한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ParticipantBulkRequest {
    @JsonProperty("membershipIds")
    @NotEmpty(message = "추가할 부원은 필수입니다.")
    private List<String> membershipIds;

    @JsonProperty("reason")
    private String reason;
}
