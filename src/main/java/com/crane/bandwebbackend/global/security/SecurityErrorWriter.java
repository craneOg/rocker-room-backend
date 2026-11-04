package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;

// 필터 단계의 401·403은 GlobalExceptionHandler를 거치지 않으므로 오류 봉투를 직접 쓴다
final class SecurityErrorWriter {

    static final String UNAUTHENTICATED_MESSAGE = "인증이 필요하거나 인증 정보가 유효하지 않습니다.";
    static final String FORBIDDEN_MESSAGE = "요청을 수행할 권한이 없습니다.";

    private SecurityErrorWriter() {}

    static void write(ObjectMapper objectMapper, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        write(objectMapper, response, code, message, List.of());
    }

    static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            ErrorCode code,
            String message,
            List<ErrorDetail> details)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message, details, List.of()));
    }
}
