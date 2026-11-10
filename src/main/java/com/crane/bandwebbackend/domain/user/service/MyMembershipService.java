package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.user.dto.MyMembershipsResponse;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.BadgeCounts;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.Row;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 10: 본인 소속·대기 목록. PENDING·INVITED·거절·탈퇴 상태도 본인에게는 보인다(status 필터가 없으면 전부)
@Service
@RequiredArgsConstructor
public class MyMembershipService {

    private final MyMembershipQueryRepository repository;

    @Transactional(readOnly = true)
    public MyMembershipsResponse list(long userId, Set<MembershipStatus> status, int page, int size) {
        List<String> statuses =
                status == null ? List.of() : status.stream().map(Enum::name).toList();
        long total = repository.count(userId, statuses);
        List<Row> rows = repository.find(userId, statuses, (long) page * size, size);
        Map<Long, List<MyMembershipsResponse.PartInfo>> parts =
                repository.findParts(rows.stream().map(Row::getId).toList());
        BadgeCounts counts = repository.badgeCounts(userId);
        List<MyMembershipsResponse.Item> items = rows.stream()
                .map(r -> toItem(r, parts.getOrDefault(r.getId(), List.of())))
                .toList();
        return MyMembershipsResponse.builder()
                .page(PageResponse.from(new PageImpl<>(items, PageRequest.of(page, size), total)))
                .pendingCount(counts.getPending())
                .invitedCount(counts.getInvited())
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
}
