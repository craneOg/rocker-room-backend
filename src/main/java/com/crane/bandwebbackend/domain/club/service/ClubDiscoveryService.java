package com.crane.bandwebbackend.domain.club.service;

import com.crane.bandwebbackend.domain.club.dto.ClubSummary;
import com.crane.bandwebbackend.domain.club.dto.InvitationPreviewResponse;
import com.crane.bandwebbackend.domain.club.dto.JoinPreviewResponse;
import com.crane.bandwebbackend.domain.club.dto.NameAvailabilityResponse;
import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.club.repository.ClubApplicationStatusRepository;
import com.crane.bandwebbackend.domain.club.repository.ClubRepository;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 12·13·126·127: 가입 전 동아리 탐색. 가입 코드와 소속자 정보는 응답에 넣지 않는다
@Service
@RequiredArgsConstructor
public class ClubDiscoveryService {

    private static final Pattern JOIN_CODE = Pattern.compile("^[A-Z2-9]{10}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    // C-06: 이 상태의 소속 행은 재신청으로 되살릴 수 있다. 그 밖의 상태는 이미 소속이 있어 신청할 수 없다
    private static final Set<MembershipStatus> REAPPLICABLE =
            Set.of(MembershipStatus.WITHDRAWN, MembershipStatus.REJECTED, MembershipStatus.DECLINED);

    private final ClubRepository clubRepository;
    private final ClubApplicationStatusRepository statusRepository;

    // 검색어는 공백을 뺀 길이가 2자 이상, 앞뒤 공백을 뺀 길이가 100자 이하. 공개(isSearchable)·ACTIVE만, 이름순
    @Transactional(readOnly = true)
    public PageResponse<JoinPreviewResponse> search(long userId, String q, int page, int size) {
        String keyword = q == null ? "" : q.strip();
        if (WHITESPACE.matcher(keyword).replaceAll("").length() < 2 || keyword.length() > 100) {
            throw invalid("q");
        }
        Page<Club> clubs = clubRepository.searchByName(
                escapeLike(keyword),
                ClubStatus.ACTIVE,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"))));
        Map<Long, MembershipStatus> statuses = statusesOf(userId, clubs.getContent());
        return PageResponse.from(clubs.map(club -> preview(club, statuses.get(club.getId()))));
    }

    // 형식 오류·없는 코드·비활성 동아리는 모두 같은 404(C-08)
    @Transactional(readOnly = true)
    public JoinPreviewResponse resolve(long userId, String joinCode) {
        Club club = findActiveByCode(joinCode).orElseThrow(ClubDiscoveryService::notFound);
        return preview(club, statusesOf(userId, List.of(club)).get(club.getId()));
    }

    // 로그인 전: 코드 존재 여부를 숨기기 위해 실패 사유를 구분하지 않는다
    @Transactional(readOnly = true)
    public InvitationPreviewResponse invitationPreview(String code) {
        return findActiveByCode(code).map(InvitationPreviewResponse::from).orElseThrow(ClubDiscoveryService::notFound);
    }

    // C-15: 대소문자·공백을 무시하고 비활성 동아리까지 포함해 비교한다. 이름 수정 중이면 자기 자신은 제외
    @Transactional(readOnly = true)
    public NameAvailabilityResponse nameAvailability(String name, String excludeClubId) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > 100) {
            throw invalid("name");
        }
        String nameKey = WHITESPACE.matcher(stripped).replaceAll("").toLowerCase(Locale.ROOT);
        boolean taken;
        if (excludeClubId == null || excludeClubId.isBlank()) {
            taken = clubRepository.existsByNameKey(nameKey);
        } else {
            taken = clubRepository.existsByNameKeyAndIdNot(nameKey, parseId(excludeClubId));
        }
        return new NameAvailabilityResponse(!taken);
    }

    private java.util.Optional<Club> findActiveByCode(String code) {
        if (code == null || !JOIN_CODE.matcher(code).matches()) {
            return java.util.Optional.empty();
        }
        return clubRepository.findByJoinCodeAndStatus(code, ClubStatus.ACTIVE);
    }

    private Map<Long, MembershipStatus> statusesOf(long userId, List<Club> clubs) {
        Map<Long, MembershipStatus> statuses = new HashMap<>();
        if (clubs.isEmpty()) {
            return statuses;
        }
        statusRepository
                .findStatuses(userId, clubs.stream().map(Club::getId).toList())
                .forEach(s -> statuses.put(s.getClubId(), s.getStatus()));
        return statuses;
    }

    private static JoinPreviewResponse preview(Club club, MembershipStatus existing) {
        boolean canApply =
                club.getStatus() == ClubStatus.ACTIVE && (existing == null || REAPPLICABLE.contains(existing));
        return new JoinPreviewResponse(ClubSummary.from(club), existing == null ? null : existing.name(), canApply);
    }

    // like 와일드카드(% _)와 이스케이프 문자(\)를 문자 그대로 검색하도록 이스케이프한다
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long parseId(String id) {
        try {
            return Long.valueOf(id.strip());
        } catch (NumberFormatException e) {
            throw invalid("excludeClubId");
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
