package com.crane.bandwebbackend.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 설정 바인딩 전용 불변 객체(app.*). 비밀값은 환경 변수로만 주입한다
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Cookie cookie) {

    public record Jwt(String secret, @DefaultValue("PT15M") Duration accessTtl) {

        // 로그나 예외 메시지에 키가 그대로 찍히지 않도록 가린다
        @Override
        public String toString() {
            return "Jwt[secret=***, accessTtl=" + accessTtl + "]";
        }
    }

    public record Cors(
            @DefaultValue("http://localhost:5173") String origin) {}

    public record Cookie(
            @DefaultValue("true") boolean hostPrefix,
            @DefaultValue("true") boolean secure) {}
}
