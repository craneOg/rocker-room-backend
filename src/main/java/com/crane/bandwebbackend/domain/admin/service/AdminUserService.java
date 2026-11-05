package com.crane.bandwebbackend.domain.admin.service;

import com.crane.bandwebbackend.domain.admin.dto.AdminUserResponse;
import com.crane.bandwebbackend.domain.admin.dto.ForceLogoutResponse;
import com.crane.bandwebbackend.domain.admin.repository.AdminUserQueryRepository;
import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import com.crane.bandwebbackend.domain.auth.service.SessionRevocationService;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.service.UserLookupService;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.KstTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 122·123: Super Admin의 운영 계정 조회와 강제 로그아웃
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Pattern HANDLE = Pattern.compile("^[a-z0-9_]{5,30}$");
    private static final Pattern POSITIVE_ID = Pattern.compile("^[1-9][0-9]*$");

    private final UserLookupService users;
    private final AdminUserQueryRepository queryRepository;
    private final SessionRevocationService sessionRevocation;
    private final AuditRecorder auditRecorder;

    // userId 또는 handle 중 정확히 하나로 찾는다(부분 검색은 허용하지 않는다)
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String userId, String handle, int page, int size) {
        boolean hasUserId = userId != null && !userId.isBlank();
        boolean hasHandle = handle != null && !handle.isBlank();
        if (hasUserId == hasHandle) {
            throw invalid(hasUserId ? "handle" : "userId");
        }
        Optional<User> found;
        if (hasUserId) {
            found = users.findById(parseId(userId, "userId"));
        } else {
            if (!HANDLE.matcher(handle.strip()).matches()) {
                throw invalid("handle");
            }
            found = users.findByHandle(handle.strip());
        }
        List<User> matched = found.map(List::of).orElseGet(List::of);
        // 정확 검색이라 결과는 최대 1건이다. 0페이지가 아니면 항목은 비고 전체 건수만 남는다
        List<User> pageItems = page == 0 ? matched : List.of();
        List<AdminUserResponse> items = toResponses(pageItems);
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(page, size), matched.size()));
    }

    // 대상의 모든 로그인을 끊고(Refresh 전체 폐기 + token_version +1) 사유와 함께 감사 기록을 남긴다
    @Transactional
    public ForceLogoutResponse forceLogout(long adminUserId, long targetUserId, String reason) {
        int before = sessionRevocation.revokeAll(targetUserId, RevokeReason.FORCE_LOGOUT);
        // detail에는 ID와 변경 전·후 값만 둔다. "token"으로 시작하는 키는 금지라 sessionVersion으로 적는다
        auditRecorder.record(
                null,
                adminUserId,
                "FORCE_LOGOUT",
                "USER",
                targetUserId,
                reason.strip(),
                Map.of("before", Map.of("sessionVersion", before), "after", Map.of("sessionVersion", before + 1)));
        return new ForceLogoutResponse(targetUserId, true);
    }

    private List<AdminUserResponse> toResponses(List<User> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(User::getId).toList();
        Map<Long, List<AdminUserResponse.ClubEntry>> clubs = queryRepository.findClubs(ids).stream()
                .collect(Collectors.groupingBy(
                        AdminUserQueryRepository.UserClubRow::getUserId,
                        Collectors.mapping(
                                row -> AdminUserResponse.ClubEntry.builder()
                                        .clubId(row.getClubId())
                                        .name(row.getClubName())
                                        .role(row.getRole().name())
                                        .membershipStatus(row.getStatus().name())
                                        .joinSource(row.getJoinSource().name())
                                        .build(),
                                Collectors.toList())));
        Set<Long> linked = new HashSet<>(queryRepository.findKakaoLinkedUserIds(ids));
        return found.stream()
                .map(user -> AdminUserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .handle(user.getHandle())
                        // 비공개 사진은 Super Admin에게도 보이지 않는다(U-13)
                        .profileImageUrl(user.isProfileImagePublic() ? user.getProfileImageUrl() : null)
                        .status(user.getStatus().name())
                        .superAdmin(user.isSuperAdmin())
                        .lastActiveAt(KstTime.toKst(user.getLastActiveAt()))
                        .kakaoLinked(linked.contains(user.getId()))
                        .clubs(clubs.getOrDefault(user.getId(), List.of()))
                        .createdAt(KstTime.toKst(user.getCreatedAt()))
                        .updatedAt(KstTime.toKst(user.getUpdatedAt()))
                        .build())
                .toList();
    }

    // 숫자 형식이어도 Long 범위를 넘으면 500이 아니라 400으로 처리한다
    public static long parseId(String value, String field) {
        if (!POSITIVE_ID.matcher(value.strip()).matches()) {
            throw invalid(field);
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw invalid(field);
        }
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
