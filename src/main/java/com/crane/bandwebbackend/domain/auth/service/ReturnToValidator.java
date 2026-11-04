package com.crane.bandwebbackend.domain.auth.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 로그인 뒤 FE가 이동할 경로. 서버는 값을 저장해 돌려주기만 하지만, 오픈 리다이렉트로 쓰일 수 없는 상대 경로만 받는다
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReturnToValidator {

    private static final int MAX_LENGTH = 200;

    // "/"로 시작, "//"로 시작하지 않음, 역슬래시·공백·제어문자 없음, 200자 이하
    public static boolean isValid(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        if (value.charAt(0) != '/' || (value.length() > 1 && value.charAt(1) == '/')) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= ' ' || c == 0x7f || c == '\\') {
                return false;
            }
        }
        return true;
    }
}
