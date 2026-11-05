package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 125 항목. detail은 ID와 변경 전·후 값만 담긴 객체이고 기록이 없으면 빈 객체다
@Getter
@Builder
public class AuditLogResponse {

    @IdString
    private final Long id;

    private final String action;
    private final String targetType;

    @IdString
    private final Long targetId;

    @IdString
    private final Long actorUserId;

    @IdString
    private final Long clubId;

    private final String reason;
    private final JsonNode detail;
    private final OffsetDateTime createdAt;

    public static AuditLogResponse from(AuditLog log, ObjectMapper objectMapper) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .actorUserId(log.getActorUserId())
                .clubId(log.getClubId())
                .reason(log.getReason())
                .detail(parse(log.getDetail(), objectMapper))
                .createdAt(KstTime.toKst(log.getCreatedAt()))
                .build();
    }

    private static JsonNode parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
