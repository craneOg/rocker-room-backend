package com.crane.bandwebbackend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 검증 규칙 0.2의 9종만 사용
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    STATE_CONFLICT(HttpStatus.CONFLICT),
    DUPLICATE(HttpStatus.CONFLICT),
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    CONFIRMATION_REQUIRED(HttpStatus.CONFLICT),
    RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;
}
