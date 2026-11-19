package com.crane.bandwebbackend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 카카오 연결 해제 웹훅. 시드: 사용자 1(임원)·2·3(부원), 동아리 1
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class KakaoUnlinkWebhookTest {

    private static final String WEBHOOK = "/api/v1/auth/kakao/unlink-webhook";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    KakaoOAuthClient kakao;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM user_withdrawal_archive");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM auth_identity");
    }

    // 카카오 서버 호출: X-Requested-With·Origin 없이 form + KakaoAK 헤더
    private ResultActions call(String auth, String appId, String kakaoUserId) throws Exception {
        return mockMvc.perform(post(WEBHOOK)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", auth)
                .param("app_id", appId)
                .param("user_id", kakaoUserId)
                .param("referrer_type", "UNLINK_FROM_PARTNERS"));
    }

    private void identity(long userId, String uid) {
        jdbc.update("INSERT INTO auth_identity (user_id, provider, provider_uid) VALUES (?, 'KAKAO', ?)", userId, uid);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    @Test
    @DisplayName("올바른 키·앱 ID면 사용자를 탈퇴 처리하고 임원은 자동 승계하며 연결 끊기는 다시 호출하지 않는다")
    void withdrawsUser() throws Exception {
        identity(1, "k-1");
        call("KakaoAK test-admin-key", "test-app-id", "k-1").andExpect(status().isOk());

        assertThat(userStatus(1)).isEqualTo("WITHDRAWN");
        // 임원 1이 빠지고 다른 부원이 임원을 이어받는다
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM membership WHERE club_id = 1 AND role = 'CLUB_ADMIN' AND status = 'ACTIVE'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_WITHDRAWN' AND target_id = 1",
                        Integer.class))
                .isEqualTo(1);
        verify(kakao, never()).unlink(anyString());
    }

    @Test
    @DisplayName("어드민 키나 앱 ID가 다르면 401이고 아무것도 바뀌지 않는다")
    void rejectsBadCredentials() throws Exception {
        identity(2, "k-2");
        call("KakaoAK wrong", "test-app-id", "k-2").andExpect(status().isUnauthorized());
        call("test-admin-key", "test-app-id", "k-2").andExpect(status().isUnauthorized());
        call("KakaoAK test-admin-key", "other-app", "k-2").andExpect(status().isUnauthorized());

        assertThat(userStatus(2)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("모르는 카카오 회원번호나 이미 탈퇴한 사용자는 200이고 변화가 없다")
    void noOp() throws Exception {
        call("KakaoAK test-admin-key", "test-app-id", "nobody").andExpect(status().isOk());

        identity(3, "k-3");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = 3");
        call("KakaoAK test-admin-key", "test-app-id", "k-3").andExpect(status().isOk());

        assertThat(userStatus(3)).isEqualTo("WITHDRAWN");
        assertThat(userStatus(2)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_WITHDRAWN'", Integer.class))
                .isZero();
        verify(kakao, never()).unlink(anyString());
    }
}
