package com.crane.bandwebbackend.domain.performance.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** API-050·052에서 공유하는 점검 항목. type은 HTTP 오류 코드와 별개다. */
public record LockChecklistItem(
        String checkId, Result result, String type, String message, String targetType, String targetId) {
    public enum Result {
        SATISFIED,
        UNMET,
        WARNING
    }

    /** 확인 경고의 공통 Map 계약에 맞추며 기존 JSON 필드를 그대로 보존한다. */
    public Map<String, Object> asWarning() {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("checkId", checkId);
        warning.put("result", result);
        warning.put("type", type);
        warning.put("message", message);
        warning.put("targetType", targetType);
        warning.put("targetId", targetId);
        return warning;
    }
}
