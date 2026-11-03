package com.crane.bandwebbackend.global.response;

import com.crane.bandwebbackend.global.config.RequestIdFilter;
import lombok.Getter;
import org.slf4j.MDC;

@Getter
public class Meta {

    private final String requestId;

    private Meta(String requestId) {
        this.requestId = requestId;
    }

    public static Meta current() {
        return new Meta(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
