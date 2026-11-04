package com.crane.bandwebbackend.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 1회용·Refresh 토큰 공통: 원문은 무작위 32바이트(base64url), DB에는 SHA-256 해시 32바이트만 둔다
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_BYTES = 32;

    public static String newRaw() {
        byte[] bytes = new byte[RAW_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
