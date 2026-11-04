package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// 401: 사유(만료·위조·블랙리스트 등)는 응답에 노출하지 않는다
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        SecurityErrorWriter.write(
                objectMapper, response, ErrorCode.UNAUTHENTICATED, SecurityErrorWriter.UNAUTHENTICATED_MESSAGE);
    }
}
