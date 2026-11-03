package com.crane.bandwebbackend.domain.auth.client;

// 카카오 호출 실패. 응답 본문·code·토큰이 메시지에 들어가지 않도록 사유만 담는다
public class KakaoOAuthException extends RuntimeException {

    public KakaoOAuthException(String message) {
        super(message);
    }

    public KakaoOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
