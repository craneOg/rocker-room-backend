package com.crane.bandwebbackend.domain.home.dto;

import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 116 항목. 동아리 임원용이라 reason·detail은 내리지 않는다
@Getter
@Builder
public class ClubAuditLogItem {

    @IdString
    private final Long id;

    private final String action;
    private final String targetType;

    @IdString
    private final Long targetId;

    @IdString
    private final Long actorUserId;

    private final OffsetDateTime createdAt;

    public static ClubAuditLogItem from(AuditLog log) {
        return ClubAuditLogItem.builder()
                .id(log.getId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .actorUserId(log.getActorUserId())
                .createdAt(KstTime.toKst(log.getCreatedAt()))
                .build();
    }
}
