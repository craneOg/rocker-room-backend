package com.crane.bandwebbackend.domain.club.service;

import com.crane.bandwebbackend.domain.club.dto.ClubCreateRequest;
import com.crane.bandwebbackend.domain.club.dto.ClubDetailResponse;
import com.crane.bandwebbackend.domain.club.dto.ClubResponse;
import com.crane.bandwebbackend.domain.club.dto.ClubSummary;
import com.crane.bandwebbackend.domain.club.dto.ClubUpdateRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinCodeRegenerateRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinCodeResponse;
import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.club.entity.ClubType;
import com.crane.bandwebbackend.domain.club.repository.ClubRepository;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.service.PartService;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.service.ProfileImageService;
import com.crane.bandwebbackend.domain.user.service.UserLookupService;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// API 11: 동아리 생성. API 15~19: 상세·설정·가입 코드. 생성자 소속과 기본 파트 6개를 같은 트랜잭션에서 만든다
@Service
@RequiredArgsConstructor
public class ClubService {

    private static final Pattern THEME_COLOR = Pattern.compile("^#[0-9A-F]{6}$");
    // 가입 코드: 10자, A-Z와 2-9(ck_club_join_code_format)
    private static final String JOIN_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 10;
    private static final String NAME_UNIQUE_CONSTRAINT = "uk_club_name_key";

    private final ClubRepository clubRepository;
    private final UserLookupService userLookupService;
    private final MembershipService membershipService;
    private final PartService partService;
    private final ProfileImageService profileImages;
    private final AuditRecorder auditRecorder;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ClubResponse create(long userId, ClubCreateRequest request) {
        User creator = userLookupService.findById(userId).orElseThrow(ClubService::unauthenticated);

        String name = request.getName() == null ? "" : request.getName().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw invalid("name");
        }
        String description =
                request.getDescription() == null || request.getDescription().isBlank()
                        ? null
                        : request.getDescription().strip();
        if (description != null && description.length() > 500) {
            throw invalid("description");
        }
        ClubType clubType = parseClubType(request.getClubType());
        String themeColor = request.getThemeColor();
        if (themeColor != null && !THEME_COLOR.matcher(themeColor).matches()) {
            throw invalid("themeColor");
        }

        // C-15: 서비스가 먼저 검사하고 DB 유니크 제약은 동시 요청의 안전망으로만 쓴다
        if (clubRepository.existsByNameKey(ClubDiscoveryService.nameKeyOf(name))) {
            throw nameTaken();
        }
        String joinCode = request.isIssueJoinCode() ? newJoinCode() : null;

        Club club;
        try {
            club = clubRepository.saveAndFlush(
                    Club.create(name, description, clubType, request.isSearchable(), themeColor, joinCode, creator));
        } catch (DataIntegrityViolationException e) {
            // 이름 유니크 제약만 CLUB_NAME_TAKEN으로 변환하고, 다른 제약 위반은 전역 핸들러가 처리한다
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains(NAME_UNIQUE_CONSTRAINT)) {
                throw nameTaken();
            }
            throw e;
        }
        membershipService.createCreatorMembership(club, creator);
        partService.createDefaults(club);

        // 사진 복사는 외부 저장소 작업이라 실패할 수 있는 DB 작업이 모두 끝난 뒤 마지막에 한다
        if (request.getProfileImageKey() != null) {
            applyProfileImage(club, userId, request.getProfileImageKey());
        }
        clubRepository.flush();
        return ClubResponse.from(club);
    }

    // 소속이 있고 접근 가능하면 조회한다(비활성 동아리도 허용). 객원에게는 기본정보만 준다
    @Transactional(readOnly = true)
    public Object detail(long userId, long clubId) {
        Membership member = membershipService.requireMember(clubId, userId);
        Club club = requireClub(clubId);
        return member.getMemberType() == MemberType.GUEST ? ClubSummary.from(club) : ClubDetailResponse.from(club);
    }

    // 임원 전용. 보낸 필드만 바꾼다
    @Transactional
    public ClubDetailResponse update(long userId, long clubId, ClubUpdateRequest request) {
        Club club = lockForAdmin(clubId, userId);

        String name = club.getName();
        if (request.isSpecified("name")) {
            name = request.getName() == null ? "" : request.getName().strip();
            if (name.isEmpty() || name.length() > 100) {
                throw invalid("name");
            }
        }
        String description = club.getDescription();
        if (request.isSpecified("description")) {
            description =
                    request.getDescription() == null || request.getDescription().isBlank()
                            ? null
                            : request.getDescription().strip();
            if (description != null && description.length() > 500) {
                throw invalid("description");
            }
        }
        boolean searchable = club.isSearchable();
        if (request.isSpecified("isSearchable")) {
            if (request.getSearchable() == null) {
                throw invalid("isSearchable");
            }
            searchable = request.getSearchable();
        }
        ClubType clubType = club.getClubType();
        if (request.isSpecified("clubType")) {
            clubType = parseClubType(request.getClubType());
        }
        String themeColor = club.getThemeColor();
        if (request.isSpecified("themeColor")) {
            themeColor = request.getThemeColor();
            if (themeColor != null && !THEME_COLOR.matcher(themeColor).matches()) {
                throw invalid("themeColor");
            }
        }
        // 유형이 실제로 바뀔 때만 사유가 필요하다
        ClubType previousType = club.getClubType();
        String typeChangeReason = null;
        if (clubType != previousType) {
            typeChangeReason = requireReason(request.getClubTypeChangeReason(), "clubTypeChangeReason", true);
        }
        // C-15: 서비스가 먼저 검사하고 DB 유니크 제약은 동시 요청의 안전망으로만 쓴다
        if (request.isSpecified("name")
                && clubRepository.existsByNameKeyAndIdNot(ClubDiscoveryService.nameKeyOf(name), clubId)) {
            throw nameTaken();
        }

        club.changeSettings(name, description, clubType, searchable, themeColor);
        try {
            clubRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 이름 유니크 제약만 CLUB_NAME_TAKEN으로 변환하고, 다른 제약 위반은 전역 핸들러가 처리한다
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains(NAME_UNIQUE_CONSTRAINT)) {
                throw nameTaken();
            }
            throw e;
        }
        if (clubType != previousType) {
            auditRecorder.record(
                    clubId,
                    userId,
                    "CLUB_TYPE_CHANGED",
                    "CLUB",
                    clubId,
                    typeChangeReason,
                    Map.of("from", previousType.name(), "to", clubType.name()));
        }
        // 사진 복사는 외부 저장소 작업이라 실패할 수 있는 DB 작업이 모두 끝난 뒤 마지막에 한다
        if (request.isSpecified("profileImageKey")) {
            replaceProfileImage(club, userId, request.getProfileImageKey());
        }
        clubRepository.flush();
        return ClubDetailResponse.from(club);
    }

    // 임원 전용. 코드가 없으면 joinCode는 null
    @Transactional(readOnly = true)
    public JoinCodeResponse getJoinCode(long userId, long clubId) {
        membershipService.requireAdmin(clubId, userId);
        return new JoinCodeResponse(requireClub(clubId).getJoinCode());
    }

    // 새 코드로 교체한다(이전 코드는 즉시 무효). 코드 값은 감사 기록에 남기지 않는다
    @Transactional
    public JoinCodeResponse regenerateJoinCode(long userId, long clubId, JoinCodeRegenerateRequest request) {
        Club club = lockForAdmin(clubId, userId);
        String reason = null;
        if (request != null && request.isReasonSpecified()) {
            reason = requireReason(request.getReason(), "reason", false);
        }
        club.changeJoinCode(newJoinCode());
        auditRecorder.record(clubId, userId, "JOIN_CODE_REGENERATED", "CLUB", clubId, reason, null);
        return new JoinCodeResponse(club.getJoinCode());
    }

    // 이미 없으면 그대로 성공한다(멱등, 감사 없음)
    @Transactional
    public void disableJoinCode(long userId, long clubId) {
        Club club = lockForAdmin(clubId, userId);
        if (club.getJoinCode() == null) {
            return;
        }
        club.changeJoinCode(null);
        auditRecorder.record(clubId, userId, "JOIN_CODE_DISABLED", "CLUB", clubId, null, null);
    }

    // 쓰기 API 공통: 동아리 행 잠금(404) -> 임원 확인 -> 비활성이면 409(G-08). 잠금이 먼저여야 동시 임원 해제가 반영된다
    private Club lockForAdmin(long clubId, long userId) {
        Club club = clubRepository
                .findWithLockById(clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다."));
        membershipService.requireAdmin(clubId, userId);
        if (club.getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 사용할 수 없습니다.");
        }
        return club;
    }

    private Club requireClub(long clubId) {
        return clubRepository
                .findById(clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다."));
    }

    // strip 후 1..500자. null이면 required일 때 REASON_REQUIRED, 아니면 400
    private static String requireReason(String value, String field, boolean required) {
        if (value == null && required) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "사유를 입력해야 합니다.", List.of(ErrorDetail.of(field, "REASON_REQUIRED")));
        }
        String reason = value == null ? "" : value.strip();
        if (reason.isEmpty() || reason.length() > 500) {
            throw invalid(field);
        }
        return reason;
    }

    // null이면 사진 삭제, 키(API 129)면 영구 위치로 복사해 연결한다. 이전 사진과 임시 객체는 커밋 뒤에 지운다
    private void replaceProfileImage(Club club, long userId, String key) {
        String previousUrl = club.getProfileImageUrl();
        club.changeProfileImage(key == null ? null : profileImages.promoteClub(userId, club.getId(), key));
        long clubId = club.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                profileImages.deletePreviousClub(clubId, previousUrl);
                if (key != null) {
                    profileImages.deleteTmp(key);
                }
            }
        });
    }

    // 키(API 129)를 영구 위치로 복사해 연결한다. 임시 객체는 커밋 뒤에 지운다
    private void applyProfileImage(Club club, long userId, String key) {
        club.changeProfileImage(profileImages.promoteClub(userId, club.getId(), key));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                profileImages.deleteTmp(key);
            }
        });
    }

    // 저장 전에 코드를 정한다. 이미 쓰는 코드면 다시 뽑는다
    private String newJoinCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(JOIN_CODE_LENGTH);
            for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
                sb.append(JOIN_CODE_CHARS.charAt(random.nextInt(JOIN_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (clubRepository.existsByJoinCode(code));
        return code;
    }

    private static ClubType parseClubType(String value) {
        if (value == null) {
            throw invalid("clubType");
        }
        try {
            return ClubType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("clubType");
        }
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
    }

    private static BusinessException nameTaken() {
        return new BusinessException(
                ErrorCode.DUPLICATE, "현재 리소스 상태와 요청이 충돌합니다.", List.of(ErrorDetail.of(null, "CLUB_NAME_TAKEN")));
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
