package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.dto.ImageUploadResponse;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.storage.ImagePurpose;
import com.crane.bandwebbackend.global.storage.ImageStorage;
import com.crane.bandwebbackend.global.storage.StorageProperties;
import com.crane.bandwebbackend.global.storage.UploadedImage;
import com.crane.bandwebbackend.global.time.KstTime;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// API 129·8: 프로필 사진. 사진은 URL이 아니라 업로드 키로만 연결한다(U-12).
// 업로드는 임시 위치(tmp/users/{userId}/)에 두고, 프로필에 연결할 때 영구 위치(users/{userId}/)로 옮긴다
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final String PERMANENT_PREFIX = "users/";
    private static final String TMP_KEY_FORMAT =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)";

    private final ImageStorage storage;
    private final StorageProperties properties;
    private final Clock clock;

    // purpose가 없으면 USER_PROFILE. 파일이 없거나 검사에 실패하면 400
    public ImageUploadResponse upload(long userId, MultipartFile file, String purposeValue) {
        ImagePurpose purpose = parsePurpose(purposeValue);
        if (file == null) {
            throw invalid("file", "INVALID_VALUE");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw invalid("file", "INVALID_IMAGE");
        }
        UploadedImage image = UploadedImage.validate(bytes, file.getContentType());
        String key = purpose.tmpPrefixFor(userId) + UUID.randomUUID() + "." + image.getExtension();
        storage.put(key, bytes, image.getContentType());
        return ImageUploadResponse.builder()
                .imageKey(key)
                .previewUrl(storage.publicUrl(key))
                .purpose(purpose.name())
                .expiresAt(KstTime.toKst(Instant.now(clock)
                        .plus(properties.tmpTtl())
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDateTime()))
                .build();
    }

    // 본인이 올린 USER_PROFILE 임시 키만 연결할 수 있다. 형식이 다르거나 없거나 만료됐으면 모두 같은 404(IMAGE_KEY_NOT_FOUND).
    // 영구 위치로 복사하고 임시 객체를 지운 뒤 영구 공개 주소를 돌려준다
    // ponytail: DB 커밋 실패 시 복사본이 고아로 남는다. 사진 수가 늘면 정리 배치로 users/ 접두사의 미참조 객체를 지운다
    public String promote(long userId, String key) {
        String prefix = ImagePurpose.USER_PROFILE.tmpPrefixFor(userId);
        if (key.length() > 200
                || !key.startsWith(prefix)
                || !Pattern.matches(TMP_KEY_FORMAT, key.substring(prefix.length()))) {
            throw keyNotFound();
        }
        Optional<Instant> uploadedAt = storage.lastModified(key);
        if (uploadedAt.isEmpty()
                || !Instant.now(clock).isBefore(uploadedAt.get().plus(properties.tmpTtl()))) {
            throw keyNotFound();
        }
        String permanentKey = PERMANENT_PREFIX + userId + "/" + key.substring(prefix.length());
        storage.copy(key, permanentKey);
        deleteQuietly(key);
        return storage.publicUrl(permanentKey);
    }

    // 이전 사진이 이 저장소의 본인 영구 위치에 있을 때만 지운다(카카오 프로필 같은 외부 주소는 건드리지 않는다)
    public void deletePrevious(long userId, String previousUrl) {
        if (previousUrl == null) {
            return;
        }
        String base = storage.publicUrl(PERMANENT_PREFIX + userId + "/");
        if (previousUrl.startsWith(base) && previousUrl.length() > base.length()) {
            deleteQuietly(PERMANENT_PREFIX + userId + "/" + previousUrl.substring(base.length()));
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // 지우지 못해도 요청은 성공이다. 고아 객체는 버킷 정리에서 처리한다
            log.warn("사진 객체 삭제 실패 key={}", key, e);
        }
    }

    private static ImagePurpose parsePurpose(String value) {
        if (value == null || value.isBlank()) {
            return ImagePurpose.USER_PROFILE;
        }
        try {
            return ImagePurpose.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            throw invalid("purpose", "INVALID_VALUE");
        }
    }

    private static BusinessException keyNotFound() {
        return new BusinessException(
                ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", List.of(ErrorDetail.of(null, "IMAGE_KEY_NOT_FOUND")));
    }

    private static BusinessException invalid(String field, String reason) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, reason)));
    }
}
