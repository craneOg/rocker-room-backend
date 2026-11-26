package com.crane.bandwebbackend.domain.performance.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 감사 detail용 변경 전·후 값 비교 도우미. */
public final class AuditDiff {
    /** 정적 도우미이므로 인스턴스 생성을 막는다. */
    private AuditDiff() {}

    /** 값이 바뀐 항목만 변경 전·후 맵에 담는다(null은 JSON null, 목록은 그대로, 나머지는 문자열). */
    public static void diff(Map<String, Object> from, Map<String, Object> to, String key, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            from.put(key, value(before));
            to.put(key, value(after));
        }
    }

    private static Object value(Object value) {
        return value == null || value instanceof List ? value : String.valueOf(value);
    }
}
