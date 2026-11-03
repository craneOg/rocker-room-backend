package com.crane.bandwebbackend.domain.auth.controller;

import com.crane.bandwebbackend.domain.auth.dto.ExchangeResponse;
import com.crane.bandwebbackend.domain.auth.service.AuthExchangeService;
import com.crane.bandwebbackend.domain.auth.service.KakaoLoginService;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CookieFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoLoginService kakaoLoginService;
    private final AuthExchangeService authExchangeService;
    private final CookieFactory cookies;

    // API 1
    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> authorize(@RequestParam(required = false) String returnTo) {
        KakaoLoginService.AuthorizeRedirect redirect = kakaoLoginService.authorize(returnTo);
        return redirect(redirect.location())
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.state(redirect.stateToken()).toString())
                .build();
    }

    // API 2: 성공·실패 모두 FE 인증 완료 페이지로 302. 사용한 state 쿠키는 항상 지운다
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        Cookie stateCookie = WebUtils.getCookie(request, cookies.stateName());
        KakaoLoginService.CallbackResult result =
                kakaoLoginService.callback(code, state, error, stateCookie == null ? null : stateCookie.getValue());
        ResponseEntity.BodyBuilder response = redirect(result.location());
        response.header(HttpHeaders.SET_COOKIE, cookies.deleteState().toString());
        if (result.exchangeToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookies.exchange(result.exchangeToken()).toString());
        }
        return response.build();
    }

    // API 3: 본문 필드는 없다(빈 JSON 객체). 교환 쿠키는 소비 후 지우고, 토큰은 본문이 아니라 Set-Cookie로만 준다
    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ExchangeResponse>> exchange(HttpServletRequest request) {
        Cookie exchangeCookie = WebUtils.getCookie(request, cookies.exchangeName());
        AuthExchangeService.Result result = authExchangeService.exchange(
                exchangeCookie == null ? null : exchangeCookie.getValue(),
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookies.deleteExchange().toString());
        if (result.accessToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE, cookies.access(result.accessToken()).toString());
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookies.refresh(result.refreshToken()).toString());
        }
        if (result.restorationToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookies.restore(result.restorationToken()).toString());
        }
        return response.body(ApiResponse.ok(result.body()));
    }

    // 인증 URL·콜백 URL이 Referer로 새지 않게 하고 캐시하지 않는다
    private static ResponseEntity.BodyBuilder redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
