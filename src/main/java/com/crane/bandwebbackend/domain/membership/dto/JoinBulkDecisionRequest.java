package com.crane.bandwebbackend.domain.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class JoinBulkDecisionRequest {
    @JsonProperty("membershipIds")
    @NotEmpty(message = "처리할 소속은 필수입니다.")
    @Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
    private List<String> membershipIds;

    @JsonProperty("decision")
    @NotNull(message = "승인 또는 거절을 선택해야 합니다.")
    private Decision decision;

    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;

    public enum Decision {
        APPROVE,
        REJECT
    }
}
