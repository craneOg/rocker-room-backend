package com.crane.bandwebbackend.domain.auth.client;

import com.crane.bandwebbackend.domain.auth.config.AuthLoginProperties;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 카카오 인가 코드 → 토큰 → 프로필. 타임아웃은 spring.http.client.*, 재시도는 하지 않는다
@Component
public class KakaoOAuthClient {

    static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    static final String PROFILE_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final AuthLoginProperties properties;

    public KakaoOAuthClient(RestClient.Builder builder, AuthLoginProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public KakaoProfile fetchProfile(String code) {
        try {
            return fetchProfileWith(exchangeCode(code));
        } catch (RestClientException e) {
            // 원인 예외 메시지에 응답 본문이 들어 있을 수 있어 종류만 남긴다
            throw new KakaoOAuthException("카카오 호출 실패: " + e.getClass().getSimpleName());
        }
    }

    private String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.kakaoClientId());
        form.add("redirect_uri", properties.kakaoRedirectUri());
        form.add("code", code);
        if (properties.kakaoClientSecret() != null
                && !properties.kakaoClientSecret().isBlank()) {
            form.add("client_secret", properties.kakaoClientSecret());
        }
        TokenResponse token = restClient
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null
                || token.getAccessToken() == null
                || token.getAccessToken().isBlank()) {
            throw new KakaoOAuthException("카카오 토큰 응답에 access_token이 없습니다");
        }
        return token.getAccessToken();
    }

    private KakaoProfile fetchProfileWith(String accessToken) {
        MeResponse me = restClient
                .get()
                .uri(PROFILE_URL)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(MeResponse.class);
        if (me == null || me.getId() == null) {
            throw new KakaoOAuthException("카카오 프로필 응답에 회원번호가 없습니다");
        }
        MeResponse.Profile profile =
                me.getKakaoAccount() == null ? null : me.getKakaoAccount().getProfile();
        return new KakaoProfile(
                String.valueOf(me.getId()),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getProfileImageUrl());
    }

    // 카카오 응답 JSON 매핑용. 필드에 @JsonProperty를 붙여 setter 없이 역직렬화한다
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {

        @JsonProperty("access_token")
        private String accessToken;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MeResponse {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("kakao_account")
        private Account kakaoAccount;

        @Getter
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Account {

            @JsonProperty("profile")
            private Profile profile;
        }

        @Getter
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Profile {

            @JsonProperty("nickname")
            private String nickname;

            @JsonProperty("profile_image_url")
            private String profileImageUrl;
        }
    }
}
