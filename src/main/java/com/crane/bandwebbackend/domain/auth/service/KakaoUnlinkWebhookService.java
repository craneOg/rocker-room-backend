package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.config.AuthLoginProperties;
import com.crane.bandwebbackend.domain.auth.entity.AuthProvider;
import com.crane.bandwebbackend.domain.auth.repository.AuthIdentityRepository;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.service.AccountWithdrawalService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 카카오 연결 해제 웹훅: 사용자가 카카오 쪽에서 연결을 끊으면 우리 계정도 탈퇴 처리한다
@Service
@RequiredArgsConstructor
public class KakaoUnlinkWebhookService {

    private final AuthLoginProperties properties;
    private final AuthIdentityRepository identities;
    private final AccountWithdrawalService withdrawals;

    public void handle(String authorization, String appId, String kakaoUserId) {
        verify(authorization, appId);
        // 모르는 사용자·이미 탈퇴한 사용자는 카카오가 재시도하지 않도록 성공으로 끝낸다
        identities.findWithUser(AuthProvider.KAKAO, kakaoUserId).ifPresent(identity -> {
            if (identity.getUser().getStatus() == UserStatus.ACTIVE) {
                withdrawals.withdrawByKakaoUnlink(identity.getUser().getId());
            }
        });
    }

    private void verify(String authorization, String appId) {
        String adminKey = properties.kakaoAdminKey();
        String configuredAppId = properties.kakaoAppId();
        if (isBlank(adminKey) || isBlank(configuredAppId) || authorization == null || appId == null) {
            throw unauthorized();
        }
        // 비밀값 비교는 상수 시간으로
        boolean keyOk = MessageDigest.isEqual(
                authorization.getBytes(StandardCharsets.UTF_8),
                ("KakaoAK " + adminKey).getBytes(StandardCharsets.UTF_8));
        if (!keyOk || !configuredAppId.equals(appId)) {
            throw unauthorized();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
    }
}
