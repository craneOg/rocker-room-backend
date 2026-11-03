package com.crane.bandwebbackend.global.response;

import lombok.Getter;

// 성공 응답 봉투: { "data": ..., "meta": { "requestId": "req-..." } }
@Getter
public class ApiResponse<T> {

    private final T data;
    private final Meta meta;

    private ApiResponse(T data, Meta meta) {
        this.data = data;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, Meta.current());
    }
}
