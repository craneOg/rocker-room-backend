package com.crane.bandwebbackend.global.response;

import lombok.Getter;

// 공통 성공 본문: {data, meta:{requestId}}. 컨트롤러는 data만 반환하고 ApiResponseAdvice가 감쌈
@Getter
public class ApiResponse<T> {

    private final T data;
    private final Meta meta;

    public ApiResponse(T data, String requestId) {
        this.data = data;
        this.meta = new Meta(requestId);
    }

    @Getter
    public static class Meta {

        private final String requestId;

        Meta(String requestId) {
            this.requestId = requestId;
        }
    }
}
