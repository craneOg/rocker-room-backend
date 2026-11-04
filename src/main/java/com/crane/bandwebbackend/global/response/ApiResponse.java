package com.crane.bandwebbackend.global.response;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/** API최종 명세서의 성공 본문. requestId는 응답 헤더와 같다. */
@Getter
public class ApiResponse<T> {
    private final T data;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<Object> warnings;
    private final Map<String, String> meta;

    private ApiResponse(T data, String requestId, List<?> warnings) {
        this.data = data;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.meta = Map.of("requestId", requestId);
    }

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, requestId, List.of());
    }

    public static <T> ApiResponse<T> of(T data, String requestId, List<?> warnings) {
        return new ApiResponse<>(data, requestId, warnings);
    }
}
