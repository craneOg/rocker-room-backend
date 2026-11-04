package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.config.AuthLoginProperties;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

// API 1·2: 카카오 로그인 시작과 콜백. 콜백은 어떤 경우에도 JSON 오류 없이 FE 인증 완료 페이지로 이동시킨다
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    static final Duration STATE_TTL = Duration.ofMinutes(10);
    static final Duration EXCHANGE_TTL = Duration.ofSeconds(60);
    static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    // 이메일 동의는 요청하지 않는다
    static final String SCOPE = "profile_nickname,profile_image";

    static final String CONSENT_CANCELED = "CONSENT_CANCELED";
    static final String OAUTH_FAILED = "OAUTH_FAILED";

    private final AuthLoginProperties properties;
    private final OneTimeTokenService tokens;
    private final KakaoAccountService accounts;
    private final KakaoOAuthClient kakao;

    // 카카오 인증 URL과 쿠키에 담을 state 원문
    @Getter
    @RequiredArgsConstructor
    public static class AuthorizeRedirect {

        private final String location;
        private final String stateToken;
    }

    // location은 FE 인증 완료 페이지. exchangeToken은 성공일 때만 있고 쿠키에 담는다
    @Getter
    @RequiredArgsConstructor
    public static class CallbackResult {

        private final String location;
        private final String exchangeToken;
    }

    public AuthorizeRedirect authorize(String returnTo) {
        if (returnTo != null && !ReturnToValidator.isValid(returnTo)) {
            throw invalidReturnTo();
        }
        String state = tokens.issue(OneTimeTokenPurpose.OAUTH_STATE, null, returnTo, STATE_TTL);
        String location = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.kakaoClientId())
                .queryParam("redirect_uri", properties.kakaoRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", SCOPE)
                .build()
                .encode()
                .toUriString();
        return new AuthorizeRedirect(location, state);
    }

    // code·state·error가 모두 없으면 카카오를 거치지 않은 요청이라 400
    public CallbackResult callback(String code, String state, String error, String stateCookie) {
        if (isBlank(code) && isBlank(state) && isBlank(error)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "요청 값이 올바르지 않습니다.",
                    List.of(ErrorDetail.of("request", "INVALID_VALUE")));
        }
        Optional<OneTimeTokenService.Consumed> consumed = consumeState(state, stateCookie);
        if (consumed.isEmpty()) {
            return fail(OAUTH_FAILED, "OAUTH_STATE_INVALID");
        }
        if (!isBlank(error)) {
            return "access_denied".equals(error)
                    ? fail(CONSENT_CANCELED, "KAKAO_ACCESS_DENIED")
                    : fail(OAUTH_FAILED, "KAKAO_ERROR");
        }
        if (isBlank(code)) {
            return fail(OAUTH_FAILED, "OAUTH_CODE_MISSING");
        }
        try {
            KakaoProfile profile = kakao.fetchProfile(code);
            User user = findOrRegister(profile);
            // 탈퇴 후 90일 이내 계정만 "새로 시작"에 쓸 카카오 프로필을 잠깐 들고 간다(그 밖에는 저장하지 않음)
            boolean withdrawn = user.getStatus() == UserStatus.WITHDRAWN;
            String exchange = tokens.issue(
                    OneTimeTokenPurpose.LOGIN_EXCHANGE,
                    user,
                    consumed.get().getReturnTo(),
                    withdrawn ? KakaoAccountService.name(profile.getNickname()) : null,
                    withdrawn ? KakaoAccountService.imageUrl(profile.getImageUrl()) : null,
                    EXCHANGE_TTL);
            return new CallbackResult(properties.completeUrl(), exchange);
        } catch (RuntimeException e) {
            // 카카오 응답·code는 남기지 않는다. 예외 종류와 스택만 기록
            log.error("카카오 로그인 처리 실패 reason=OAUTH_EXCHANGE_FAILED", e);
            return new CallbackResult(failureLocation(OAUTH_FAILED), null);
        }
    }

    // 쿼리 state와 쿠키 state가 같을 때만 소비한다(불일치·만료·재사용은 구분하지 않음)
    private Optional<OneTimeTokenService.Consumed> consumeState(String state, String stateCookie) {
        if (isBlank(state) || isBlank(stateCookie) || !constantTimeEquals(state, stateCookie)) {
            return Optional.empty();
        }
        return tokens.consume(state, OneTimeTokenPurpose.OAUTH_STATE);
    }

    // 같은 사람이 동시에 처음 로그인하면 한쪽이 uk_auth_identity_provider_uid에 걸린다. 한 번 다시 조회한다
    private User findOrRegister(KakaoProfile profile) {
        try {
            return accounts.findOrRegister(profile);
        } catch (DataIntegrityViolationException e) {
            return accounts.findOrRegister(profile);
        }
    }

    private CallbackResult fail(String reason, String internalReason) {
        log.warn("카카오 로그인 실패 reason={}", internalReason);
        return new CallbackResult(failureLocation(reason), null);
    }

    private String failureLocation(String reason) {
        return UriComponentsBuilder.fromUriString(properties.completeUrl())
                .queryParam("error", reason)
                .build()
                .toUriString();
    }

    private static BusinessException invalidReturnTo() {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of("returnTo", "INVALID_VALUE")));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
