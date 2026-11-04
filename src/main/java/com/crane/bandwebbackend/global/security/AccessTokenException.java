package com.crane.bandwebbackend.global.security;

// Access Token 검증 실패(내부용). 사유는 로그에만 쓰고 응답에는 노출하지 않는다
public class AccessTokenException extends RuntimeException {

    public enum Reason {
        EXPIRED,
        INVALID_SIGNATURE,
        MALFORMED
    }

    private final Reason reason;

    public AccessTokenException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
