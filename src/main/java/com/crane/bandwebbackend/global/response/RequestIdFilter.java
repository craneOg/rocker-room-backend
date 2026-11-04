package com.crane.bandwebbackend.global.response;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API v2 본문과 X-Request-Id 헤더의 동일성을 보장한다. 외부에서 받은 ID를 그대로 신뢰하지 않고
 * 서버에서 발급하며 인증 필터 순서와 무관하게 사용자 정보를 읽지 않는다.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        if (request.getRequestURI().startsWith("/api/v1/")
                || request.getRequestURI().startsWith("/api/v2/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    public static String require(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String id) {
            return id;
        }
        throw new IllegalStateException("RequestIdFilter가 요청에 적용되지 않았습니다.");
    }
}
