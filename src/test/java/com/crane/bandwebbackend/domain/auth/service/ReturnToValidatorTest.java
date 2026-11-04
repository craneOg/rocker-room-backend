package com.crane.bandwebbackend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnToValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/join/K7M2Q9XA4B", "/clubs/1/performances?tab=songs#top", "/a%20b"})
    @DisplayName("D6 상대 경로는 허용한다")
    void valid(String value) {
        assertThat(ReturnToValidator.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "join/abc",
                "//evil.com",
                "///evil.com",
                "/\\evil.com",
                "/a\\b",
                "https://evil.com",
                "javascript:alert(1)",
                "/a b",
                "/a\tb",
                "/a\nb",
                "/a\u007fb"
            })
    @DisplayName("D6 절대 URL·스킴 상대 URL·역슬래시·공백·제어문자는 거부한다")
    void invalid(String value) {
        assertThat(ReturnToValidator.isValid(value)).isFalse();
    }

    @Test
    @DisplayName("D6 null은 거부하고 200자는 허용, 201자는 거부한다")
    void nullAndLength() {
        assertThat(ReturnToValidator.isValid(null)).isFalse();
        assertThat(ReturnToValidator.isValid("/" + "a".repeat(199))).isTrue();
        assertThat(ReturnToValidator.isValid("/" + "a".repeat(200))).isFalse();
    }
}
