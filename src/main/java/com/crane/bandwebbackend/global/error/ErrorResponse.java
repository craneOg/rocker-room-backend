package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.response.Warning;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 공통 오류 본문: {error:{code, message, details[{field, reason}], requestId, warnings?}}
@Getter
public class ErrorResponse {

    private final Body error;

    public ErrorResponse(Body error) {
        this.error = error;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Body {

        private final String code;
        private final String message;
        private final List<Detail> details;
        private final String requestId;
        // CONFIRMATION_REQUIRED일 때만
        private final List<Warning> warnings;
    }

    @Getter
    @Builder
    public static class Detail {

        private final String field;
        private final String reason;
    }
}
