package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

// 0.5 / U14·U15: 상태 변경 요청은 X-Requested-With 헤더가 있어야 한다(값은 검사하지 않음). 인증 여부·경로와 무관
public class RequestedWithFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Requested-With";
    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper;

    public RequestedWithFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (STATE_CHANGING.contains(request.getMethod())) {
            String value = request.getHeader(HEADER);
            if (value == null || value.isBlank()) {
                SecurityErrorWriter.write(
                        objectMapper, response, ErrorCode.FORBIDDEN, SecurityErrorWriter.FORBIDDEN_MESSAGE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
