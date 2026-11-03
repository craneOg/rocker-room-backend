package com.crane.bandwebbackend.global.response;

import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

// 요청마다 하나. 성공 meta.requestId와 오류 error.requestId에 같은 값
public final class RequestIds {

    private static final String KEY = RequestIds.class.getName();

    private RequestIds() {}

    public static String current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        Object id = attrs.getAttribute(KEY, RequestAttributes.SCOPE_REQUEST);
        if (id == null) {
            id = UUID.randomUUID().toString();
            attrs.setAttribute(KEY, id, RequestAttributes.SCOPE_REQUEST);
        }
        return (String) id;
    }
}
