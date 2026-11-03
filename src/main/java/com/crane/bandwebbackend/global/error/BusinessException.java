package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.response.Warning;
import java.util.List;
import lombok.Getter;

// reason = 명세의 세부 사유(예: INVALID_STATE). warnings = 409 CONFIRMATION_REQUIRED의 경고 목록(없으면 빈 목록)
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String reason;
    private final List<Warning> warnings;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, null, message);
    }

    public BusinessException(ErrorCode errorCode, String reason, String message) {
        this(errorCode, reason, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String reason, String message, List<Warning> warnings) {
        super(message);
        this.errorCode = errorCode;
        this.reason = reason;
        this.warnings = List.copyOf(warnings);
    }

    // 검증 규칙 0.1: confirmed=true 없이 요청하면 409와 경고 목록. 문장만 있는 경고는 message로 담음
    public static BusinessException confirmationRequired(String message, List<String> warnings) {
        return new BusinessException(
                ErrorCode.CONFIRMATION_REQUIRED,
                null,
                message,
                warnings.stream().map(w -> Warning.builder().message(w).build()).toList());
    }

    public static BusinessException invalidState(String message) {
        return new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", message);
    }

    public static BusinessException reasonRequired() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "REASON_REQUIRED", "확정된 공연에서는 사유를 입력해야 합니다.");
    }
}
