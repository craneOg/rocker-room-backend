package com.crane.bandwebbackend.global.config;

import com.crane.bandwebbackend.global.security.CookieFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// U7: local 프로필에서만 등록. 쿠키 이름은 CookieFactory에서 읽는다(local은 rr_at)
@Profile("local")
@Configuration
public class OpenApiConfig {

    private static final String COOKIE_SCHEME = "accessCookie";

    private static final String DESCRIPTION = """
            - 성공 응답: `{data, meta:{requestId}}`, 오류 응답: `{error:{code,message,details,requestId}}`
            - 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)은 `X-Requested-With` 헤더가 필수입니다. 없으면 403.
            - 인증은 HttpOnly 쿠키(Access Token)로 합니다. 로그인 후 브라우저가 자동으로 전송합니다.
            """;

    @Bean
    OpenAPI openApi(CookieFactory cookieFactory) {
        return new OpenAPI()
                .info(new Info().title("rocker-room API").description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(
                                COOKIE_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(cookieFactory.accessName())))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }
}
