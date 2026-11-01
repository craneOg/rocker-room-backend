package com.crane.bandwebbackend.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 본문 모양은 API 명세의 공통 규칙에 맞춰 조정한다. 지금은 코드·메시지에 확인 필요 경고 목록만 더했다
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    private final String code;
    private final String message;

    // CONFIRMATION_REQUIRED일 때만 값이 있다
    private final List<String> warnings;
}
