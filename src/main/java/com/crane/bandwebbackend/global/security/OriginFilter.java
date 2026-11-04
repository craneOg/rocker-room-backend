package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

// API 3·4: 쿠키만으로 토큰을 새로 받는 두 요청은 Origin을 검증한다. 헤더가 있는데 허용 출처와 다르면 403.
// CorsFilter보다 앞에 두어, 다른 출처에 Spring 기본(본문 없는 403)이 아니라 오류 본문 형식으로 응답한다.
// 브라우저가 아닌 클라이언트는 Origin을 보내지 않으므로 통과시킨다(X-Requested-With는 RequestedWithFilter가 요구)
public class OriginFilter extends OncePerRequestFilter {

    static final String REASON = "ORIGIN_NOT_ALLOWED";
    private static final Set<String> PATHS = Set.of("/api/v1/auth/exchange", "/api/v1/auth/refresh");

    private final ObjectMapper objectMapper;
    private final String allowedOrigin;

    public OriginFilter(ObjectMapper objectMapper, String allowedOrigin) {
        this.objectMapper = objectMapper;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.equals(allowedOrigin)) {
            SecurityErrorWriter.write(
                    objectMapper,
                    response,
                    ErrorCode.FORBIDDEN,
                    SecurityErrorWriter.FORBIDDEN_MESSAGE,
                    List.of(ErrorDetail.of("Origin", REASON)));
            return;
        }
        chain.doFilter(request, response);
    }
}
