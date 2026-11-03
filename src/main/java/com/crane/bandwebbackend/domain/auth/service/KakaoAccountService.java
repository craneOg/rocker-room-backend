package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.domain.auth.entity.AuthIdentity;
import com.crane.bandwebbackend.domain.auth.entity.AuthProvider;
import com.crane.bandwebbackend.domain.auth.repository.AuthIdentityRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카카오 회원번호로 사용자를 찾고, 없으면 users + auth_identity를 한 트랜잭션에 만든다
@Service
@RequiredArgsConstructor
public class KakaoAccountService {

    static final String DEFAULT_NAME = "사용자";
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    private final AuthIdentityRepository identityRepository;
    private final UserRepository userRepository;

    // 기존 사용자의 이름·사진은 카카오 값으로 덮어쓰지 않는다(사용자가 바꿨을 수 있음)
    @Transactional
    public User findOrRegister(KakaoProfile profile) {
        return identityRepository
                .findWithUser(AuthProvider.KAKAO, profile.id())
                .map(AuthIdentity::getUser)
                .orElseGet(() -> register(profile));
    }

    private User register(KakaoProfile profile) {
        User user =
                userRepository.save(User.createFromProvider(name(profile.nickname()), imageUrl(profile.imageUrl())));
        identityRepository.save(AuthIdentity.create(user, AuthProvider.KAKAO, profile.id()));
        return user;
    }

    // 닉네임이 없으면 기본 이름, 50자(코드포인트)를 넘으면 자른다
    static String name(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NAME;
        }
        String trimmed = nickname.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_NAME_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }

    // 컬럼 길이(500)를 넘으면 저장하지 않는다
    static String imageUrl(String url) {
        return url == null || url.isBlank() || url.length() > MAX_IMAGE_URL_LENGTH ? null : url;
    }
}
