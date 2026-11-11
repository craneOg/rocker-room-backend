package com.crane.bandwebbackend.domain.membership.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** API-142의 action별 params는 서비스에서 엄격하게 검사한다. */
public record MembershipBulkRequest(
        @NotEmpty(message = "처리할 소속은 필수입니다.") @Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
        List<String> membershipIds,

        @NotNull(message = "처리 작업은 필수입니다.") Action action,
        @NotNull(message = "처리 값은 필수입니다.") JsonNode params,
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason) {
    public enum Action {
        CHANGE_STATUS,
        CHANGE_PARTS,
        ASSIGN_PERFORMANCE
    }
}
