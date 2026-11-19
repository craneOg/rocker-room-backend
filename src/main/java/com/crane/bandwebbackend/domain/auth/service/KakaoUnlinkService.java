package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.config.AuthLoginProperties;
import com.crane.bandwebbackend.domain.auth.entity.AuthProvider;
import com.crane.bandwebbackend.domain.auth.repository.AuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// U-07: 탈퇴 커밋 뒤 카카오 연결을 끊는다. 실패해도 탈퇴는 이미 끝났으므로 던지지 않고 경고만 남긴다
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkService {

    private final AuthIdentityRepository identities;
    private final KakaoOAuthClient kakao;
    private final AuthLoginProperties properties;

    public void unlink(Long userId) {
        try {
            if (properties.kakaoAdminKey() == null || properties.kakaoAdminKey().isBlank()) {
                log.warn("카카오 연결 끊기 생략: 어드민 키 미설정 userId={}", userId);
                return;
            }
            identities
                    .findByUserIdAndProvider(userId, AuthProvider.KAKAO)
                    .ifPresent(identity -> kakao.unlink(identity.getProviderUid()));
        } catch (RuntimeException e) {
            log.warn("카카오 연결 끊기 실패 userId={} reason={}", userId, e.getClass().getSimpleName());
        }
    }
}
