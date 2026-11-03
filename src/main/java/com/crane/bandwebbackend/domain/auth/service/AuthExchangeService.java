package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.dto.ExchangeResponse;
import com.crane.bandwebbackend.domain.auth.dto.LoginResult;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.auth.entity.RefreshToken;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 3: 교환 토큰을 한 번 소비해 Access·Refresh를 발급한다. 소비와 발급은 한 트랜잭션이라 발급이 실패하면 토큰도 되살아난다
@Service
@RequiredArgsConstructor
public class AuthExchangeService {

    static final Duration REFRESH_TTL = Duration.ofDays(30);
    static final Duration FAMILY_TTL = Duration.ofDays(90);
    static final Duration RESTORATION_TTL = Duration.ofMinutes(10);
    static final String INVALID_REASON = "LOGIN_EXCHANGE_INVALID";

    private final OneTimeTokenService oneTimeTokens;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    // 쿠키 값은 항상 원문. accessToken·refreshToken·restorationToken 중 loginResult에 맞는 것만 채워진다
    public record Result(ExchangeResponse body, String accessToken, String refreshToken, String restorationToken) {}

    // 클라이언트 정보는 기록용이며 차단 기준이 아니다
    @Transactional
    public Result exchange(String exchangeToken, String ip, String userAgent) {
        OneTimeTokenService.Consumed consumed = oneTimeTokens
                .consume(exchangeToken, OneTimeTokenPurpose.LOGIN_EXCHANGE)
                .orElseThrow(AuthExchangeService::invalid);
        if (consumed.userId() == null) {
            throw invalid();
        }
        User user = userRepository.findById(consumed.userId()).orElseThrow(AuthExchangeService::invalid);

        // 탈퇴 후 90일 이내(파기 전) 계정: 로그인시키지 않고 복구 선택 쿠키만 발급한다(G-03 예외, U-10)
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            String restoration =
                    oneTimeTokens.issue(OneTimeTokenPurpose.ACCOUNT_RESTORATION, user, null, RESTORATION_TTL);
            return new Result(ExchangeResponse.restorationRequired(consumed.returnTo()), null, null, restoration);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String familyId = UUID.randomUUID().toString();
        String refresh = TokenCodec.newRaw();
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                familyId,
                TokenCodec.hash(refresh),
                now.plus(REFRESH_TTL),
                now.plus(FAMILY_TTL),
                ip,
                truncate(userAgent, 255)));
        String access = jwtProvider
                .createAccessToken(user.getId(), familyId, user.getTokenVersion())
                .token();
        LoginResult loginResult = user.getHandle() == null ? LoginResult.NEW_USER : LoginResult.EXISTING_USER;
        ExchangeResponse body = ExchangeResponse.of(user, loginResult, consumed.returnTo());
        touchLastActive(user.getId(), now);
        return new Result(body, access, refresh, null);
    }

    // G-11: 오늘 0시(KST) 이후 기록이 없을 때만 갱신
    private void touchLastActive(long userId, LocalDateTime now) {
        LocalDate kstToday = KstTime.toKstDate(now);
        LocalDateTime dayStartUtc = KstTime.toUtc(kstToday.atStartOfDay().atOffset(KstTime.KST));
        userRepository.touchLastActive(userId, now, dayStartUtc);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ErrorCode.UNAUTHENTICATED,
                "인증이 필요하거나 인증 정보가 유효하지 않습니다.",
                java.util.List.of(ErrorDetail.of(null, INVALID_REASON)));
    }
}
