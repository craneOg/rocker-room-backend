package com.crane.bandwebbackend.domain.club.service;

import com.crane.bandwebbackend.domain.club.dto.ClubCreateRequest;
import com.crane.bandwebbackend.domain.club.dto.ClubResponse;
import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubType;
import com.crane.bandwebbackend.domain.club.repository.ClubRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.service.PartService;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.service.ProfileImageService;
import com.crane.bandwebbackend.domain.user.service.UserLookupService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// API 11: 동아리 생성. 생성자 소속과 기본 파트 6개를 같은 트랜잭션에서 만든다
@Service
@RequiredArgsConstructor
public class ClubService {

    private static final Pattern THEME_COLOR = Pattern.compile("^#[0-9A-F]{6}$");
    // 가입 코드: 10자, A-Z와 2-9(ck_club_join_code_format)
    private static final String JOIN_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 10;

    private final ClubRepository clubRepository;
    private final UserLookupService userLookupService;
    private final MembershipService membershipService;
    private final PartService partService;
    private final ProfileImageService profileImages;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ClubResponse create(long userId, ClubCreateRequest request) {
        User creator = userLookupService.findById(userId).orElseThrow(ClubService::unauthenticated);

        String name = request.getName() == null ? "" : request.getName().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw invalid("name");
        }
        String description = request.getDescription() == null
                ? null
                : request.getDescription().strip();
        if (description != null && description.isEmpty()) {
            description = null;
        }
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
            throw nameTaken();
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
