package com.crane.bandwebbackend.global.error;

import java.util.List;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 확인 필요 규칙(CONFIRMATION_REQUIRED)에서 사용자에게 보여 줄 경고 목록. 없으면 빈 목록
    private final List<String> warnings;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<String> warnings) {
        super(message);
        this.errorCode = errorCode;
        this.warnings = List.copyOf(warnings);
    }

    // 검증 규칙 0.1: confirmed=true 없이 요청하면 409와 경고 목록을 돌려준다
    public static BusinessException confirmationRequired(String message, List<String> warnings) {
        return new BusinessException(ErrorCode.CONFIRMATION_REQUIRED, message, warnings);
    }
}
