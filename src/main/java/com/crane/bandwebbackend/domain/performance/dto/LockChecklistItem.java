package com.crane.bandwebbackend.domain.performance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-050·052에서 공유하는 점검 항목. type은 HTTP 오류 코드와 별개다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class LockChecklistItem {
    @JsonProperty("checkId")
    private String checkId;

    @JsonProperty("result")
    private Result result;

    @JsonProperty("type")
    private String type;

    @JsonProperty("message")
    private String message;

    @JsonProperty("targetType")
    private String targetType;

    @JsonProperty("targetId")
    private String targetId;

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
