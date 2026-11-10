package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.user.dto.LastViewedResponse;
import com.crane.bandwebbackend.domain.user.dto.MyMembershipsResponse;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.BadgeCounts;
import com.crane.bandwebbackend.domain.user.repository.MyMembershipQueryRepository.Row;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.InvitationExpiry;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyMembershipsResponse list(long userId, Set<MembershipStatus> status, int page, int size) {
        List<String> statuses =
                status == null ? List.of() : status.stream().map(Enum::name).toList();
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        long total = repository.count(userId, statuses, now);
        List<Row> rows = repository.find(userId, statuses, now, (long) page * size, size);
        Map<Long, List<MyMembershipsResponse.PartInfo>> parts =
                repository.findParts(rows.stream().map(Row::getId).toList());
        BadgeCounts counts = repository.badgeCounts(userId, now);
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
                .invitationExpiresAt(invited ? InvitationExpiry.expiresAt(r.getInvitedAt()) : null)
                // requestedAt은 PENDING인 동안 membership.updated_at과 같다(명세)
                .requestedAt(pending ? KstTime.toKst(r.getUpdatedAt()) : null)
                .parts(parts)
                .createdAt(KstTime.toKst(r.getCreatedAt()))
                .updatedAt(KstTime.toKst(r.getUpdatedAt()))
                .build();
    }

    // API 140: 타인·없는 소속은 모두 404로 같게 보여 존재를 숨긴다(G-04)
    @Transactional
    public LastViewedResponse touchLastViewed(long userId, long membershipId) {
        // DATETIME(6)에 맞춰 마이크로초로 자른 값을 저장한다
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).truncatedTo(ChronoUnit.MICROS);
        if (repository.touchLastViewed(membershipId, userId, now) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
        }
        // GREATEST로 더 늦은 기존 값이 유지됐을 수 있어 요청 시각이 아니라 저장된 값을 응답한다
        return LastViewedResponse.builder()
                .membershipId(membershipId)
                .lastViewedAt(KstTime.toKst(repository.findLastViewedAt(membershipId, userId)))
                .build();
    }
}
