package com.crane.bandwebbackend.domain.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-142의 action별 params는 서비스에서 엄격하게 검사한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipBulkRequest {
    @JsonProperty("membershipIds")
    @NotEmpty(message = "처리할 소속은 필수입니다.")
    @Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
    private List<String> membershipIds;

    @JsonProperty("action")
    @NotNull(message = "처리 작업은 필수입니다.")
    private Action action;

    @JsonProperty("params")
    @NotNull(message = "처리 값은 필수입니다.")
    private JsonNode params;

    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;

    public enum Action {
        CHANGE_STATUS,
        CHANGE_PARTS,
        ASSIGN_PERFORMANCE
    }
}
