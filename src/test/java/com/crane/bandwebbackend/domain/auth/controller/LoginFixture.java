package com.crane.bandwebbackend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.global.security.CookieFactory;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 통합 테스트용: 실제 authorize → callback → exchange를 거쳐 로그인하고 쿠키 원문을 돌려준다(카카오만 목)
final class LoginFixture {

    record Session(String accessToken, String refreshToken) {}

    private final MockMvc mockMvc;
    private final CookieFactory cookies;

    LoginFixture(MockMvc mockMvc, CookieFactory cookies) {
        this.mockMvc = mockMvc;
        this.cookies = cookies;
    }

    // 카카오 회원번호별로 로그인한다. 새 로그인 묶음이 생긴다
    Session login(KakaoOAuthClient kakao, KakaoProfile profile) throws Exception {
        MvcResult exchange = exchange(loginUntilExchangeCookie(kakao, profile));
        return new Session(cookieValue(exchange, cookies.accessName()), cookieValue(exchange, cookies.refreshName()));
    }

    // 콜백까지만 진행하고 교환 쿠키 원문을 돌려준다
    String loginUntilExchangeCookie(KakaoOAuthClient kakao, KakaoProfile profile) throws Exception {
        when(kakao.fetchProfile(anyString())).thenReturn(profile);
        String state =
                cookieValue(mockMvc.perform(get("/api/v1/auth/kakao/authorize")).andReturn(), cookies.stateName());
        MvcResult callback = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "c")
                        .param("state", state)
                        .cookie(new Cookie(cookies.stateName(), state)))
                .andReturn();
        return cookieValue(callback, cookies.exchangeName());
    }

    MvcResult exchange(String exchangeCookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(cookies.exchangeName(), exchangeCookie)))
                .andReturn();
    }

    static MockHttpServletRequestBuilder jsonPost(String path) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Requested-With", "XMLHttpRequest");
    }

    static String setCookie(MvcResult result, String name) {
        List<String> all = result.getResponse().getHeaders("Set-Cookie");
        return all.stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 없습니다: " + all));
    }

    static boolean hasCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith(name + "="));
    }

    static String cookieValue(MvcResult result, String name) {
        return setCookie(result, name).split(";", 2)[0].substring(name.length() + 1);
    }

    static String sha256Hex(String raw) throws Exception {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
