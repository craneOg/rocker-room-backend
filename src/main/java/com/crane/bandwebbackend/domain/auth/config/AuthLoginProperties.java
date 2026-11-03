package com.crane.bandwebbackend.domain.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 카카오 로그인 설정(app.auth.*). 값은 환경 변수로만 주입하고 저장소에 두지 않는다
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthLoginProperties(
        @NotBlank String kakaoClientId,
        // 카카오 앱에서 Client Secret을 사용 설정한 경우에만 값이 있다
        String kakaoClientSecret,
        // 카카오 개발자 콘솔에 등록한 콜백 URL과 정확히 같아야 한다
        @NotBlank String kakaoRedirectUri,
        // 콜백이 항상 이동하는 FE 인증 완료 페이지
        @NotBlank String completeUrl) {

    // 로그나 예외 메시지에 비밀값이 그대로 찍히지 않도록 가린다
    @Override
    public String toString() {
        return "AuthLoginProperties[kakaoClientId=***, kakaoClientSecret=***, kakaoRedirectUri=" + kakaoRedirectUri
                + ", completeUrl=" + completeUrl + "]";
    }
}
