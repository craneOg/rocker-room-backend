package com.crane.bandwebbackend.global.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditRecorder {

    // 개인정보 금지 키(U-09, 소문자 비교). "token"으로 시작하는 키도 금지
    private static final Set<String> FORBIDDEN_KEYS =
            Set.of("name", "displayname", "handle", "email", "provideruid", "ip");
    private static final String FORBIDDEN_PREFIX = "token";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // 호출한 서비스의 트랜잭션에 합류, 없으면 실패(U10). 기록 실패 시 본 작업도 롤백
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            Long clubId,
            Long actorUserId,
            String action,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> detail) {
        require(action, 50, "action");
        require(targetType, 30, "targetType");
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("reason은 500자 이하여야 합니다");
        }
        String detailJson = null;
        if (detail != null && !detail.isEmpty()) {
            detailJson = toJson(detail);
            checkKeys(readTree(detailJson));
        }
        auditLogRepository.save(AuditLog.create(clubId, actorUserId, action, targetType, targetId, reason, detailJson));
    }

    private static void require(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + "은(는) 필수이며 " + max + "자 이하여야 합니다");
        }
    }

    // 저장될 JSON 트리를 검사한다. Map·List·배열·JsonNode·POJO 어느 형태로 넘겨도 같은 결과가 나온다
    private static void checkKeys(JsonNode node) {
        node.fields().forEachRemaining(entry -> {
            String k = entry.getKey();
            String lower = k.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_KEYS.contains(lower) || lower.startsWith(FORBIDDEN_PREFIX)) {
                throw new IllegalArgumentException("감사 detail에 사용할 수 없는 키: " + k);
            }
            checkKeys(entry.getValue());
        });
        node.elements().forEachRemaining(AuditRecorder::checkKeys);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("detail을 JSON으로 변환할 수 없습니다", e);
        }
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("detail을 JSON으로 변환할 수 없습니다", e);
        }
    }
}
