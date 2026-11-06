package com.crane.bandwebbackend.global.response;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;

/** 경로와 JSON의 문자열 ID에 같은 양수 Long 검증을 적용한다. */
public final class ApiIds {
    private ApiIds() {}

    public static Long positive(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw BusinessException.of(ErrorCode.VALIDATION_ERROR, field + " 형식이 유효하지 않습니다.");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw BusinessException.of(ErrorCode.VALIDATION_ERROR, field + " 범위를 벗어났습니다.");
        }
    }
}
