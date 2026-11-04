package com.crane.bandwebbackend.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

// field가 없는 오류(예: 지원하지 않는 Content-Type)는 JSON에서 field를 생략한다
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    private final String field;
    private final String reason;

    private ErrorDetail(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }

    public static ErrorDetail of(String field, String reason) {
        return new ErrorDetail(field, reason);
    }
}
