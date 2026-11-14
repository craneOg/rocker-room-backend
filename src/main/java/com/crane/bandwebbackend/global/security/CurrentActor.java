package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 검증된 Spring Security 인증 컨텍스트에서 현재 사용자 ID를 읽는다.
 * 요청 본문·경로·임의 헤더의 사용자 ID는 신뢰하지 않는다.
 */
@Component
public class CurrentActor {

    /** 인증 주체의 숫자 사용자 ID를 반환하고 인증이 없으면 401로 거절한다. */
    public Long requireUserId() throws BusinessException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw unauthenticated();
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthPrincipal actor)) {
            throw unauthenticated();
        }
        return positive(actor.userId());
    }

    /** 사용자 식별자가 양수인지 확인한다. */
    private static Long positive(long userId) {
        if (userId <= 0) {
            throw unauthenticated();
        }
        return userId;
    }

    /** 인증 정보가 유효하지 않을 때 사용할 공통 API 예외를 생성한다. */
    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "인증이 필요합니다.");
    }
}
