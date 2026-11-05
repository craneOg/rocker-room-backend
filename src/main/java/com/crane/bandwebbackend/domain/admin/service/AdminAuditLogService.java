package com.crane.bandwebbackend.domain.admin.service;

import com.crane.bandwebbackend.domain.admin.dto.AuditLogResponse;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.audit.AuditLogRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 125: 클럽 이벤트를 포함한 전체 감사 로그(Super Admin). 최근 기록부터 보여 준다
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private static final Pattern POSITIVE_ID = Pattern.compile("^[1-9][0-9]*$");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(
            String clubId,
            String action,
            String targetType,
            String targetId,
            String actorUserId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {
        Long club = parseId(clubId, "clubId");
        Long target = parseId(targetId, "targetId");
        Long actor = parseId(actorUserId, "actorUserId");
        List<String> actions = parseActions(action);
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from");
        }

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (club != null) {
                where.add(cb.equal(root.get("clubId"), club));
            }
            if (!actions.isEmpty()) {
                where.add(root.get("action").in(actions));
            }
            if (targetType != null && !targetType.isBlank()) {
                where.add(cb.equal(root.get("targetType"), targetType.strip()));
            }
            if (target != null) {
                where.add(cb.equal(root.get("targetId"), target));
            }
            if (actor != null) {
                where.add(cb.equal(root.get("actorUserId"), actor));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), KstTime.toUtc(from)));
            }
            if (to != null) {
                where.add(cb.lessThanOrEqualTo(root.get("createdAt"), KstTime.toUtc(to)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.from(auditLogRepository
                .findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(log -> AuditLogResponse.from(log, objectMapper)));
    }

    private static List<String> parseActions(String action) {
        if (action == null || action.isBlank()) {
            return List.of();
        }
        return Arrays.stream(action.split(","))
                .map(String::strip)
                .filter(a -> !a.isEmpty())
                .toList();
    }

    private static Long parseId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!POSITIVE_ID.matcher(value.strip()).matches()) {
            throw invalid(field);
        }
        return Long.valueOf(value.strip());
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
