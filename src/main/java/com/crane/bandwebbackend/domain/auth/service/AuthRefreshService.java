package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.dto.RefreshResponse;
import com.crane.bandwebbackend.domain.auth.entity.RefreshToken;
import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 4: Refresh Token 회전(0.5). 이미 교체된 토큰이 다시 오면 탈취로 보고 같은 로그인 묶음 전체를 폐기한다
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthRefreshService {

    static final String REFRESH_INVALID = "REFRESH_INVALID";
    static final String SESSION_REVOKED = "SESSION_REVOKED";
    static final String REUSE_DETECTED = "REUSE_DETECTED";
    // 정상 원문은 43자. 이보다 긴 값은 해시 계산 전에 거른다
    private static final int MAX_RAW_LENGTH = 100;

    private final RefreshTokenRepository refreshTokens;
    private final AuthSessionService sessions;

    public record Result(RefreshResponse body, AuthSessionService.Tokens tokens) {}

    // 재사용을 탐지해 401을 내려도 묶음 폐기는 커밋되어야 하므로 BusinessException에는 롤백하지 않는다
    @Transactional(noRollbackFor = BusinessException.class)
    public Result refresh(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_RAW_LENGTH) {
            throw reject(REFRESH_INVALID);
        }
        RefreshToken token = refreshTokens
                .findWithUserByTokenHash(TokenCodec.hash(rawToken))
                .orElseThrow(() -> reject(REFRESH_INVALID));
        LocalDateTime now = sessions.now();

        if (token.getRevokedAt() != null) {
            if (token.getRevokeReason() == RevokeReason.ROTATED) {
                return reuseDetected(token, now);
            }
            throw reject(SESSION_REVOKED);
        }
        if (!now.isBefore(token.getExpiresAt()) || !now.isBefore(token.getFamilyExpiresAt())) {
            throw reject(REFRESH_INVALID);
        }
        User user = token.getUser();
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw reject(SESSION_REVOKED);
        }
        // 같은 토큰으로 동시에 온 요청 중 선점하지 못한 쪽은 이미 교체된 토큰을 쓴 것과 같다
        if (refreshTokens.revoke(token.getId(), now, RevokeReason.ROTATED) != 1) {
            return reuseDetected(token, now);
        }

        String nextRaw = TokenCodec.newRaw();
        LocalDateTime nextExpiresAt = min(now.plus(AuthSessionService.REFRESH_TTL), token.getFamilyExpiresAt());
        RefreshToken next = refreshTokens.save(RefreshToken.issue(
                user,
                token.getFamilyId(),
                TokenCodec.hash(nextRaw),
                nextExpiresAt,
                token.getFamilyExpiresAt(),
                ip,
                userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255)));
        refreshTokens.linkReplacement(token.getId(), next);
        sessions.touchLastActive(user.getId(), now);

        AuthSessionService.Tokens tokens =
                new AuthSessionService.Tokens(sessions.accessToken(user, token.getFamilyId()), nextRaw);
        return new Result(RefreshResponse.of(user), tokens);
    }

    private Result reuseDetected(RefreshToken token, LocalDateTime now) {
        refreshTokens.revokeFamily(token.getFamilyId(), now, RevokeReason.REUSE_DETECTED);
        // 토큰 값은 남기지 않는다
        log.warn("Refresh Token 재사용 탐지 userId={}", token.getUser().getId());
        throw reject(REUSE_DETECTED);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static BusinessException reject(String reason) {
        return new BusinessException(
                ErrorCode.UNAUTHENTICATED, "인증이 필요하거나 인증 정보가 유효하지 않습니다.", List.of(ErrorDetail.of(null, reason)));
    }
}
