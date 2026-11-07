package com.crane.bandwebbackend.domain.admin.service;

import com.crane.bandwebbackend.domain.admin.dto.AdminClubDetailResponse;
import com.crane.bandwebbackend.domain.admin.dto.AdminClubResponse;
import com.crane.bandwebbackend.domain.admin.repository.AdminClubQueryRepository;
import com.crane.bandwebbackend.domain.admin.repository.AdminClubQueryRepository.ClubRow;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.club.entity.ClubType;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 118·119: Super Admin의 동아리 목록·운영 상세. 동아리 내부 개인정보는 읽지 않는다
@Service
@RequiredArgsConstructor
public class AdminClubService {

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,18}$");
    private static final long INACTIVE_DAYS = 180;
    // API 명세의 허용 정렬 키 → SQL 컬럼. 이 목록 밖의 값은 400
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "name",
            "createdAt", "created_at",
            "activeMemberCount", "active_member_count",
            "lastActiveAt", "last_active_at");

    private final AdminClubQueryRepository clubs;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminClubResponse> search(
            String status,
            String clubType,
            String q,
            boolean noAdmin,
            boolean inactiveOver180Days,
            String sort,
            int page,
            int size) {
        String keyword = q == null ? "" : q.strip();
        if (keyword.length() > 100) {
            throw invalid("q");
        }
        String[] sortParts = parseSort(sort);
        AdminClubQueryRepository.Filter filter = AdminClubQueryRepository.Filter.builder()
                .statuses(enumList(status, "status", ClubStatus.class))
                .clubTypes(enumList(clubType, "clubType", ClubType.class))
                .likePattern(keyword.isEmpty() ? null : "%" + escapeLike(keyword) + "%")
                .idMatch(DIGITS.matcher(keyword).matches() ? Long.valueOf(keyword) : null)
                .noAdmin(noAdmin)
                .inactiveOver180Days(inactiveOver180Days)
                .inactiveCutoff(inactiveCutoff())
                .build();
        long total = clubs.count(filter);
        List<AdminClubResponse> items = clubs
                .search(filter, SORT_COLUMNS.get(sortParts[0]), "desc".equals(sortParts[1]), (long) page * size, size)
                .stream()
                .map(AdminClubService::toResponse)
                .toList();
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(page, size), total));
    }

    // 조회 사실만 CLUB_DETAIL_VIEWED로 남긴다(조회 사유는 받지 않는다). 감사 기록이 쓰기라 읽기 전용이 아니다
    @Transactional
    public AdminClubDetailResponse detail(long actorUserId, long clubId) {
        ClubRow club = clubs.findClub(clubId).orElseThrow(AdminClubService::notFound);
        AdminClubQueryRepository.CreatorRow creator =
                clubs.findCreator(club.getCreatedBy()).orElse(null);
        Map<String, Long> members = clubs.memberStatusCounts(clubId);
        Map<String, Long> performances = clubs.performanceStatusCounts(clubId);
        auditRecorder.record(clubId, actorUserId, "CLUB_DETAIL_VIEWED", "CLUB", clubId, null, null);
        return AdminClubDetailResponse.builder()
                .club(toClubInfo(club))
                .creator(AdminClubDetailResponse.Creator.builder()
                        .userId(club.getCreatedBy())
                        .name(
                                creator == null || "WITHDRAWN".equals(creator.getStatus())
                                        ? "탈퇴한 사용자"
                                        : creator.getName())
                        .build())
                .joinCodeEnabled(clubs.joinCodeEnabled(clubId))
                .admins(clubs.findAdmins(clubId).stream()
                        .map(a -> AdminClubDetailResponse.AdminEntry.builder()
                                .membershipId(a.getMembershipId())
                                .userId(a.getUserId())
                                .displayName(a.getDisplayName())
                                .handle(a.getHandle())
                                .status(a.getStatus())
                                .lastActiveAt(KstTime.toKst(a.getLastActiveAt()))
                                .build())
                        .toList())
                .counts(AdminClubDetailResponse.Counts.builder()
                        .members(club.getActiveMemberCount())
                        .performances(performances.values().stream()
                                .mapToLong(Long::longValue)
                                .sum())
                        .memberStatus(AdminClubDetailResponse.MemberStatus.builder()
                                .active(members.getOrDefault("ACTIVE", 0L))
                                .onLeave(members.getOrDefault("ON_LEAVE", 0L))
                                .graduated(members.getOrDefault("GRADUATED", 0L))
                                .guests(members.getOrDefault("GUEST", 0L))
                                .build())
                        .performanceStatus(AdminClubDetailResponse.PerformanceStatus.builder()
                                .draft(performances.getOrDefault("DRAFT", 0L))
                                .active(performances.getOrDefault("ACTIVE", 0L))
                                .locked(performances.getOrDefault("LOCKED", 0L))
                                .finished(performances.getOrDefault("FINISHED", 0L))
                                .canceled(performances.getOrDefault("CANCELED", 0L))
                                .build())
                        .build())
                .lastActiveAt(KstTime.toKst(club.getLastActiveAt()))
                .build();
    }

    // API 120: 이미 비활성이면 200으로 그대로 돌려주고 감사 기록도 남기지 않는다
    @Transactional
    public AdminClubDetailResponse.ClubInfo deactivate(long actorUserId, long clubId, String reason) {
        return changeStatus(actorUserId, clubId, ClubStatus.INACTIVE, "CLUB_DEACTIVATED", reason);
    }

    // API 159: 이미 활성이면 200으로 그대로 돌려주고 감사 기록도 남기지 않는다
    @Transactional
    public AdminClubDetailResponse.ClubInfo reactivate(long actorUserId, long clubId, String reason) {
        return changeStatus(actorUserId, clubId, ClubStatus.ACTIVE, "CLUB_REACTIVATED", reason);
    }

    // 조건부 UPDATE(`status <> :status`)가 행을 잠그므로 두 운영자가 동시에 같은 전환을 해도 한 번만 바뀐다.
    // 없는 동아리는 UPDATE가 0행이고 아래 조회가 비어 404가 된다. 조회는 UPDATE 뒤라 최신 값을 읽는다
    private AdminClubDetailResponse.ClubInfo changeStatus(
            long actorUserId, long clubId, ClubStatus target, String action, String reason) {
        if (clubs.updateStatus(clubId, target.name()) == 1) {
            String from = target == ClubStatus.ACTIVE ? ClubStatus.INACTIVE.name() : ClubStatus.ACTIVE.name();
            auditRecorder.record(
                    clubId,
                    actorUserId,
                    action,
                    "CLUB",
                    clubId,
                    reason.strip(),
                    Map.of("from", from, "to", target.name()));
        }
        return toClubInfo(clubs.findClub(clubId).orElseThrow(AdminClubService::notFound));
    }

    private LocalDateTime inactiveCutoff() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).minus(Duration.ofDays(INACTIVE_DAYS));
    }

    private static AdminClubDetailResponse.ClubInfo toClubInfo(ClubRow club) {
        return AdminClubDetailResponse.ClubInfo.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .clubType(club.getClubType())
                .status(club.getStatus())
                .searchable(club.isSearchable())
                .createdByUserId(club.getCreatedBy())
                .createdAt(KstTime.toKst(club.getCreatedAt()))
                .updatedAt(KstTime.toKst(club.getUpdatedAt()))
                .build();
    }

    private static AdminClubResponse toResponse(ClubRow c) {
        return AdminClubResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .clubType(c.getClubType())
                .status(c.getStatus())
                .searchable(c.isSearchable())
                .createdByUserId(c.getCreatedBy())
                .activeMemberCount(c.getActiveMemberCount())
                .adminCount(c.getAdminCount())
                .lastActiveAt(KstTime.toKst(c.getLastActiveAt()))
                .ongoingPerformanceCount(c.getOngoingPerformanceCount())
                .createdAt(KstTime.toKst(c.getCreatedAt()))
                .updatedAt(KstTime.toKst(c.getUpdatedAt()))
                .build();
    }

    // "key" 또는 "key,asc|desc". 기본 createdAt,desc
    private static String[] parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return new String[] {"createdAt", "desc"};
        }
        String[] parts = sort.split(",", -1);
        String key = parts[0].strip();
        String dir = parts.length > 1 ? parts[1].strip().toLowerCase(java.util.Locale.ROOT) : "asc";
        if (parts.length > 2
                || !SORT_COLUMNS.containsKey(key)
                || !Set.of("asc", "desc").contains(dir)) {
            throw invalid("sort");
        }
        return new String[] {key, dir};
    }

    // 쉼표 구분 다중 값. 없는 값은 400
    private static <E extends Enum<E>> List<String> enumList(String value, String field, Class<E> type) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .map(v -> {
                    try {
                        return Enum.valueOf(type, v).name();
                    } catch (IllegalArgumentException e) {
                        throw invalid(field);
                    }
                })
                .distinct()
                .toList();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다.");
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
