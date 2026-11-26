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

// 0.5 / U14·U15: 상태 변경 요청은 X-Requested-With 헤더가 있어야 한다(값은 검사하지 않음). 인증 여부·경로와 무관
public class RequestedWithFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Requested-With";
    // 카카오 서버가 호출하는 웹훅은 이 헤더를 보내지 않는다(대신 어드민 키로 검증)
    static final String KAKAO_WEBHOOK = "/api/v1/auth/kakao/unlink-webhook";
    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper;

    public RequestedWithFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (STATE_CHANGING.contains(request.getMethod()) && !KAKAO_WEBHOOK.equals(request.getRequestURI())) {
            String value = request.getHeader(HEADER);
            if (value == null || value.isBlank()) {
                SecurityErrorWriter.write(
                        objectMapper,
                        response,
                        ErrorCode.FORBIDDEN,
                        SecurityErrorWriter.FORBIDDEN_MESSAGE,
                        List.of(ErrorDetail.of(HEADER, "REQUIRED_HEADER_MISSING")));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
