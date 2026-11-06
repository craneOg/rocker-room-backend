package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.dto.HandleAvailabilityResponse;
import com.crane.bandwebbackend.domain.user.dto.UpdateProfileRequest;
import com.crane.bandwebbackend.domain.user.dto.UserProfileResponse;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 7·8·128: 본인 프로필. 인증된 요청만 도달하므로 사용자는 항상 존재한다(필터가 확인)
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(long userId) {
        return UserProfileResponse.withOnboarding(load(userId));
    }

    // U-02 handle, U-12 사진. 서비스 프로필은 카카오 프로필과 독립적으로 관리한다
    @Transactional
    public UserProfileResponse update(long userId, UpdateProfileRequest request) {
        rejectExplicitNulls(request);
        // 같은 사용자의 동시 수정이 서로의 변경을 덮어쓰지 않도록 행을 잠그고 읽는다(User에는 버전 컬럼이 없다)
        User user = loadForUpdate(userId);
        if (request.getName() != null) {
            String name = request.getName().strip();
            if (name.isEmpty() || name.length() > 50) {
                throw invalid("name");
            }
            user.rename(name);
        }
        if (request.getHandle() != null && !request.getHandle().equals(user.getHandle())) {
            // 서비스가 먼저 검사하고 DB 유니크 제약은 동시 요청의 안전망으로만 쓴다
            if (userRepository.findByHandle(request.getHandle()).isPresent()) {
                throw handleTaken();
            }
            user.changeHandle(request.getHandle());
        }
        if (request.getProfileImagePublic() != null) {
            user.changeProfileImagePublic(request.getProfileImagePublic());
        }
        if (request.isProfileImageUrlSpecified()) {
            applyProfileImage(user, request.getProfileImageUrl());
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw handleTaken();
        }
        return UserProfileResponse.of(user);
    }

    // 본인의 현재 handle은 사용 가능으로 답한다
    @Transactional(readOnly = true)
    public HandleAvailabilityResponse checkHandle(long userId, String handle) {
        boolean available = userRepository
                .findByHandle(handle)
                .map(found -> found.getId().equals(userId))
                .orElse(true);
        return new HandleAvailabilityResponse(available);
    }

    // ponytail: 사진 저장소(Cloudflare R2)와 업로드 API(129)가 아직 없어 유효한 임시 키가 존재할 수 없다.
    // 그래서 키를 보내면 모두 IMAGE_KEY_NOT_FOUND이고, null("사진 삭제")만 처리한다.
    // 저장소 연동 단계에서 키 검증·영구 위치 이동·이전 사진 삭제로 바꾼다
    private void applyProfileImage(User user, String key) {
        if (key == null) {
            user.clearProfileImage();
            return;
        }
        throw new BusinessException(
                ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", List.of(ErrorDetail.of(null, "IMAGE_KEY_NOT_FOUND")));
    }

    // 명세상 null 불가인 필드를 null로 보내면 400(보내지 않은 것과 구분한다)
    private static void rejectExplicitNulls(UpdateProfileRequest request) {
        if (request.isNameSpecified() && request.getName() == null) {
            throw invalid("name");
        }
        if (request.isHandleSpecified() && request.getHandle() == null) {
            throw invalid("handle");
        }
        if (request.isProfileImagePublicSpecified() && request.getProfileImagePublic() == null) {
            throw invalid("profileImagePublic");
        }
    }

    private User loadForUpdate(long userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(UserProfileService::unauthenticated);
    }

    private User load(long userId) {
        return userRepository.findById(userId).orElseThrow(UserProfileService::unauthenticated);
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
    }

    private static BusinessException handleTaken() {
        return new BusinessException(
                ErrorCode.DUPLICATE, "현재 리소스 상태와 요청이 충돌합니다.", List.of(ErrorDetail.of(null, "HANDLE_TAKEN")));
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
