package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.user.dto.MyMembershipsResponse;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.PartRow;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.Row;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 10: 본인 소속·대기 목록. PENDING·INVITED·거절·탈퇴 상태도 본인에게는 보인다(status 필터가 없으면 전부)
@Service
@RequiredArgsConstructor
public class MyMembershipService {

    private final MyMembershipQueryRepository repository;

    @Transactional(readOnly = true)
    public MyMembershipsResponse list(long userId, String status, int page, int size) {
        List<String> statuses = parseStatuses(status);
        long total = repository.count(userId, statuses);
        List<Row> rows = repository.find(userId, statuses, (long) page * size, size);
        Map<Long, List<MyMembershipsResponse.PartInfo>> parts =
                repository.findParts(rows.stream().map(Row::getId).toList()).stream()
                        .collect(Collectors.groupingBy(
                                PartRow::getMembershipId,
                                Collectors.mapping(MyMembershipService::toPart, Collectors.toList())));
        Map<String, Long> counts = repository.pendingAndInvitedCounts(userId);
        return MyMembershipsResponse.builder()
                .items(rows.stream()
                        .map(r -> toItem(r, parts.getOrDefault(r.getId(), List.of())))
                        .toList())
                .pendingCount(counts.getOrDefault("PENDING", 0L))
                .invitedCount(counts.getOrDefault("INVITED", 0L))
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages((int) ((total + size - 1) / size))
                .build();
    }

    private static MyMembershipsResponse.Item toItem(Row r, List<MyMembershipsResponse.PartInfo> parts) {
        boolean invited = "INVITED".equals(r.getStatus());
        boolean pending = "PENDING".equals(r.getStatus());
        return MyMembershipsResponse.Item.builder()
                .id(r.getId())
                .clubId(r.getClubId())
                .club(MyMembershipsResponse.ClubInfo.builder()
                        .id(r.getClubId())
                        .name(r.getClubName())
                        .clubType(r.getClubType())
                        .status(r.getClubStatus())
                        .profileImageUrl(r.getClubProfileImageUrl())
                        .themeColor(r.getClubThemeColor())
                        .build())
                .userId(r.getUserId())
                .displayName(r.getDisplayName())
                .memberType(r.getMemberType())
                .role(r.getRole())
                .status(r.getStatus())
                .joinSource(r.getJoinSource())
                .approvedAt(KstTime.toKst(r.getApprovedAt()))
                .leftAt(KstTime.toKst(r.getLeftAt()))
                .lastViewedAt(KstTime.toKst(r.getLastViewedAt()))
                .invitationExpiresAt(invited ? invitationExpiresAt(r) : null)
                // requestedAt은 PENDING인 동안 membership.updated_at과 같다(명세)
                .requestedAt(pending ? KstTime.toKst(r.getUpdatedAt()) : null)
                .parts(parts)
                .createdAt(KstTime.toKst(r.getCreatedAt()))
                .updatedAt(KstTime.toKst(r.getUpdatedAt()))
                .build();
    }

    // invitedAt + 1개월(KST 달력 기준). INVITED는 invited_at이 필수(CHECK)
    private static OffsetDateTime invitationExpiresAt(Row r) {
        OffsetDateTime invitedAt = KstTime.toKst(r.getInvitedAt());
        return invitedAt == null ? null : invitedAt.plusMonths(1);
    }

    private static MyMembershipsResponse.PartInfo toPart(PartRow p) {
        return MyMembershipsResponse.PartInfo.builder()
                .id(p.getId())
                .name(p.getName())
                .displayOrder(p.getDisplayOrder())
                .active(p.isActive())
                .build();
    }

    // 쉼표 구분 다중 값. enum에 없는 값은 400
    private static List<String> parseStatuses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .map(v -> {
                    try {
                        return MembershipStatus.valueOf(v).name();
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "요청 값이 올바르지 않습니다.",
                                List.of(ErrorDetail.of("status", "INVALID_VALUE")));
                    }
                })
                .distinct()
                .toList();
    }
}
