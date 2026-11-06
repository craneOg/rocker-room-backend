package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.config.RequestIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import org.slf4j.MDC;

// 오류 본문: { "error": { "code", "message", "details": [{field, reason}], "warnings"?, "requestId" } }
@Getter
public class ErrorResponse {

    private final Body error;

    private ErrorResponse(Body error) {
        this.error = error;
    }

    public static ErrorResponse of(
            ErrorCode code, String message, List<ErrorDetail> details, List<?> warnings) {
        return new ErrorResponse(new Body(code.name(), message, details, warnings, requestId()));
    }

    // 500: code와 details 없이 message·requestId만
    public static ErrorResponse withoutCode(String message) {
        return new ErrorResponse(new Body(null, message, null, null, requestId()));
    }

    private static String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Body {

        private final String code;
        private final String message;
        private final List<ErrorDetail> details;

        // CONFIRMATION_REQUIRED일 때만 값이 있다
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private final List<?> warnings;

        private final String requestId;

        private Body(
                String code,
                String message,
                List<ErrorDetail> details,
                List<?> warnings,
                String requestId) {
            this.code = code;
            this.message = message;
            this.details = details;
            this.warnings = warnings;
            this.requestId = requestId;
        }
    }
}
