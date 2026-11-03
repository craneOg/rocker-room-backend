package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.crane.bandwebbackend.domain.user.service.UserAuthQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

// 0.5 / G-01: 무상태 쿠키 JWT. CSRF 토큰 대신 X-Requested-With 헤더 검사(RequestedWithFilter).
// 필터 순서: RequestIdFilter(서블릿, Security보다 앞) -> CORS -> RequestedWithFilter -> JwtAuthenticationFilter.
// 두 커스텀 필터는 @Bean/@Component로 등록하지 않는다(서블릿 컨테이너에 중복 등록되는 것을 막기 위해)
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppProperties properties,
            ObjectMapper objectMapper,
            JwtProvider jwtProvider,
            CookieFactory cookieFactory,
            AccessTokenBlacklistRepository blacklistRepository,
            UserAuthQueryService userAuthQueryService,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        RequestedWithFilter requestedWithFilter = new RequestedWithFilter(objectMapper);
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtProvider, cookieFactory, blacklistRepository, userAuthQueryService);

        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(c -> c.disable())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource(properties)))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(a -> a
                        // 오류 디스패치(예: 404)가 401로 바뀌지 않도록
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        // G-01 비인증 허용 목록
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/kakao/authorize")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/kakao/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/exchange")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/restoration")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/restoration/restore")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/restoration/start-new")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/preview")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterAfter(requestedWithFilter, CorsFilter.class)
                .addFilterAfter(jwtFilter, RequestedWithFilter.class);
        return http.build();
    }

    // U16: 출처 하나만 정확히 허용(와일드카드·패턴 금지)
    private CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.cors().origin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Requested-With", "Accept"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
