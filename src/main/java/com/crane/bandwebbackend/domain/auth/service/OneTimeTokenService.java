package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.entity.OneTimeToken;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.auth.repository.OneTimeTokenRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 0.5: 원문은 HttpOnly 쿠키에만 두고 DB에는 SHA-256 해시만 저장한다. 사용은 조건부 UPDATE로 한 번만
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    // 정상 원문은 43자(base64url). 이보다 긴 값은 해시 계산 전에 거른다
    private static final int MAX_RAW_LENGTH = 100;

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    // 소비한 토큰이 가리키던 값. userId는 사용자 없는 용도(OAUTH_STATE)에서 null
    public record Consumed(Long userId, String returnTo, String profileName, String profileImageUrl) {

        public Consumed(Long userId, String returnTo) {
            this(userId, returnTo, null, null);
        }
    }

    // user·returnTo는 필요한 용도에서만 넘기고 아니면 null. 반환값은 쿠키에 담을 원문
    @Transactional
    public String issue(OneTimeTokenPurpose purpose, User user, String returnTo, Duration ttl) {
        return issue(purpose, user, returnTo, null, null, ttl);
    }

    // 카카오 프로필을 함께 들고 가는 토큰(탈퇴 계정의 로그인 교환·복구 선택)
    @Transactional
    public String issue(
            OneTimeTokenPurpose purpose,
            User user,
            String returnTo,
            String profileName,
            String profileImageUrl,
            Duration ttl) {
        String raw = TokenCodec.newRaw();
        repository.save(OneTimeToken.create(
                TokenCodec.hash(raw), purpose, user, returnTo, profileName, profileImageUrl, now().plus(ttl)));
        return raw;
    }

    // 미사용·미만료·용도 일치일 때만 성공. 실패 사유(없음·만료·재사용·용도 불일치)는 구분하지 않는다.
    // 값을 먼저 읽는 이유: 소비하면서 카카오 프로필을 지우기 때문이다. 소비 선점은 조건부 UPDATE가 결정한다
    @Transactional
    public Optional<Consumed> consume(String raw, OneTimeTokenPurpose purpose) {
        if (isUnusable(raw)) {
            return Optional.empty();
        }
        byte[] hash = TokenCodec.hash(raw);
        Optional<OneTimeToken> token = repository.findByTokenHash(hash);
        if (token.isEmpty() || repository.consume(hash, purpose, now()) != 1) {
            return Optional.empty();
        }
        return token.map(OneTimeTokenService::toConsumed);
    }

    // 소비하지 않고 유효한지만 본다(복구 선택 조회 API 131)
    @Transactional(readOnly = true)
    public Optional<Consumed> peek(String raw, OneTimeTokenPurpose purpose) {
        if (isUnusable(raw)) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        return repository
                .findByTokenHash(TokenCodec.hash(raw))
                .filter(t -> t.getPurpose() == purpose
                        && t.getUsedAt() == null
                        && t.getExpiresAt().isAfter(now))
                .map(OneTimeTokenService::toConsumed);
    }

    private static boolean isUnusable(String raw) {
        return raw == null || raw.isBlank() || raw.length() > MAX_RAW_LENGTH;
    }

    private static Consumed toConsumed(OneTimeToken t) {
        return new Consumed(userIdOf(t), t.getReturnTo(), t.getProfileName(), t.getProfileImageUrl());
    }

    private static Long userIdOf(OneTimeToken token) {
        return token.getUser() == null ? null : token.getUser().getId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
