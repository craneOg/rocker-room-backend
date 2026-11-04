package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.dto.ExchangeResponse;
import com.crane.bandwebbackend.domain.auth.dto.LoginResult;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 3: 교환 토큰을 한 번 소비해 Access·Refresh를 발급한다. 소비와 발급은 한 트랜잭션이라 발급이 실패하면 토큰도 되살아난다
@Service
@RequiredArgsConstructor
public class AuthExchangeService {

    static final Duration RESTORATION_TTL = Duration.ofMinutes(10);
    static final String INVALID_REASON = "LOGIN_EXCHANGE_INVALID";

    private final OneTimeTokenService oneTimeTokens;
    private final UserRepository userRepository;
    private final AuthSessionService sessions;

    // 쿠키 값은 항상 원문. accessToken·refreshToken·restorationToken 중 loginResult에 맞는 것만 채워진다
    @Getter
    @RequiredArgsConstructor
    public static class Result {

        private final ExchangeResponse body;
        private final String accessToken;
        private final String refreshToken;
        private final String restorationToken;
    }

    // 클라이언트 정보는 기록용이며 차단 기준이 아니다
    @Transactional
    public Result exchange(String exchangeToken, String ip, String userAgent) {
        OneTimeTokenService.Consumed consumed = oneTimeTokens
                .consume(exchangeToken, OneTimeTokenPurpose.LOGIN_EXCHANGE)
                .orElseThrow(AuthExchangeService::invalid);
        if (consumed.getUserId() == null) {
            throw invalid();
        }
        User user = userRepository.findById(consumed.getUserId()).orElseThrow(AuthExchangeService::invalid);

        // 탈퇴 후 90일 이내(파기 전) 계정: 로그인시키지 않고 복구 선택 쿠키만 발급한다(G-03 예외, U-10)
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            String restoration = oneTimeTokens.issue(
                    OneTimeTokenPurpose.ACCOUNT_RESTORATION,
                    user,
                    null,
                    consumed.getProfileName(),
                    consumed.getProfileImageUrl(),
                    RESTORATION_TTL);
            return new Result(ExchangeResponse.restorationRequired(consumed.getReturnTo()), null, null, restoration);
        }

        AuthSessionService.Tokens tokens = sessions.start(user, ip, userAgent);
        LoginResult loginResult = user.getHandle() == null ? LoginResult.NEW_USER : LoginResult.EXISTING_USER;
        ExchangeResponse body = ExchangeResponse.of(user, loginResult, consumed.getReturnTo());
        return new Result(body, tokens.getAccessToken(), tokens.getRefreshToken(), null);
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ErrorCode.UNAUTHENTICATED,
                "인증이 필요하거나 인증 정보가 유효하지 않습니다.",
                java.util.List.of(ErrorDetail.of(null, INVALID_REASON)));
    }
}
