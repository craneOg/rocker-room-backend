package com.crane.bandwebbackend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoAccountServiceTest {

    @Test
    @DisplayName("D2 닉네임이 없거나 공백이면 기본 이름")
    void defaultName() {
        assertThat(KakaoAccountService.name(null)).isEqualTo("사용자");
        assertThat(KakaoAccountService.name("   ")).isEqualTo("사용자");
    }

    @Test
    @DisplayName("D2 닉네임은 앞뒤 공백을 지우고 50자(코드포인트)를 넘으면 자른다")
    void truncatedByCodePoint() {
        assertThat(KakaoAccountService.name(" 홍길동 ")).isEqualTo("홍길동");
        assertThat(KakaoAccountService.name("가".repeat(50))).hasSize(50);
        assertThat(KakaoAccountService.name("가".repeat(60))).isEqualTo("가".repeat(50));
        // 이모지는 UTF-16 두 칸이지만 한 글자로 센다
        String emojis = "😀".repeat(60);
        assertThat(KakaoAccountService.name(emojis)
                        .codePointCount(0, KakaoAccountService.name(emojis).length()))
                .isEqualTo(50);
    }

    @Test
    @DisplayName("D2 프로필 이미지는 500자를 넘으면 저장하지 않는다")
    void imageUrlLimit() {
        assertThat(KakaoAccountService.imageUrl(null)).isNull();
        assertThat(KakaoAccountService.imageUrl("")).isNull();
        assertThat(KakaoAccountService.imageUrl("https://x.kr/a.png")).isEqualTo("https://x.kr/a.png");
        assertThat(KakaoAccountService.imageUrl("h".repeat(500))).hasSize(500);
        assertThat(KakaoAccountService.imageUrl("h".repeat(501))).isNull();
    }
}
