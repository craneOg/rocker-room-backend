package com.crane.bandwebbackend.global.error;

import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 오류 본문 details. 없으면 빈 목록
    private final List<ErrorDetail> details;

    // 확인 필요 규칙(CONFIRMATION_REQUIRED)에서 사용자에게 보여 줄 경고 목록. 항목은 type 등을 가진 객체. 없으면 빈 목록
    private final List<Map<String, Object>> warnings;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ErrorDetail> details) {
        this(errorCode, message, details, List.of());
    }

    // 명세의 (reason: X)를 details[0].reason에 담는다
    public BusinessException(ErrorCode errorCode, String reason, String message) {
        this(errorCode, message, List.of(ErrorDetail.of(null, reason)), List.of());
    }

    private BusinessException(
            ErrorCode errorCode, String message, List<ErrorDetail> details, List<Map<String, Object>> warnings) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
        this.warnings = List.copyOf(warnings);
    }

    // 검증 규칙 0.1: confirmed=true 없이 요청하면 409와 경고 목록을 돌려준다
    public static BusinessException confirmationRequired(String message, List<Map<String, Object>> warnings) {
        return new BusinessException(ErrorCode.CONFIRMATION_REQUIRED, message, List.of(), warnings);
    }

    public static BusinessException invalidState(String message) {
        return new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", message);
    }

    // X-02: LOCKED 공연 예외 수정은 사유 필수
    public static BusinessException reasonRequired() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "REASON_REQUIRED", "확정된 공연에서는 사유를 입력해야 합니다.");
    }
}
