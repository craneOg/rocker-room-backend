package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자의 모든 로그인을 끊는다. 다른 도메인(운영 강제 로그아웃)이 쓰는 진입점이다
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    // users.token_version +1(모든 Access Token 무효, G-02)과 전체 Refresh Token 폐기. 호출한 쪽의 트랜잭션에 합류한다.
    // 반환값은 올리기 전 토큰 버전이다(감사 기록용). 대상이 없으면 404
    @Transactional
    public int revokeAll(long userId, RevokeReason reason) {
        int before = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."))
                .getTokenVersion();
        userRepository.incrementTokenVersion(userId);
        refreshTokens.revokeAllByUser(userId, LocalDateTime.now(clock), reason);
        return before;
    }
}
