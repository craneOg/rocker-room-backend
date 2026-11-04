package com.crane.bandwebbackend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class AuthTokenCleanupServiceTest {

    @Autowired
    AuthTokenCleanupService cleanup;

    @Autowired
    JdbcTemplate jdbc;

    long userId;
    int seq;

    @BeforeEach
    void setUp() {
        clean();
        jdbc.update("INSERT INTO users (name, status) VALUES ('tester', 'ACTIVE')");
        userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("UPDATE refresh_token SET replaced_by_id = NULL");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM users");
    }

    // expiresAgo: 만료된 지 며칠·몇 시간 전인지. 음수면 아직 유효
    private long oneTime(String expiresExpr, String profileName) {
        jdbc.update(
                "INSERT INTO one_time_token (token_hash, purpose, expires_at, profile_name, profile_image_url)"
                        + " VALUES (UNHEX(SHA2(?, 256)), 'ACCOUNT_RESTORATION', " + expiresExpr + ", ?, ?)",
                "ott-" + (++seq),
                profileName,
                profileName == null ? null : "https://img.example/x.png");
        return jdbc.queryForObject("SELECT MAX(id) FROM one_time_token", Long.class);
    }

    private long refresh(String expiresExpr) {
        jdbc.update(
                "INSERT INTO refresh_token (user_id, family_id, token_hash, expires_at, family_expires_at)"
                        + " VALUES (?, UUID(), UNHEX(SHA2(?, 256)), " + expiresExpr
                        + ", DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY))",
                userId,
                "rt-" + (++seq));
        return jdbc.queryForObject("SELECT MAX(id) FROM refresh_token", Long.class);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static final String AGO_10M = "DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)";
    private static final String AFTER_10M = "DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)";

    @Test
    @DisplayName("만료된 토큰의 카카오 프로필 값만 지우고, 유효한 토큰의 값은 그대로 둔다(행은 남는다)")
    void clearsOnlyExpiredProfiles() {
        long expired = oneTime(AGO_10M, "만료닉네임");
        long valid = oneTime(AFTER_10M, "유효닉네임");
        long noProfile = oneTime(AGO_10M, null);

        int cleared = cleanup.clearExpiredProfiles();

        assertThat(cleared).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT profile_name, profile_image_url FROM one_time_token WHERE id = ?", expired)
                        .values())
                .containsOnlyNulls();
        assertThat(jdbc.queryForObject("SELECT profile_name FROM one_time_token WHERE id = ?", String.class, valid))
                .isEqualTo("유효닉네임");
        assertThat(count("SELECT COUNT(*) FROM one_time_token")).isEqualTo(3);
        assertThat(noProfile).isPositive();
        assertThat(cleanup.clearExpiredProfiles()).isZero();
    }

    @Test
    @DisplayName("§16 1회용 토큰은 만료 + 1일이 지나야 삭제한다")
    void deletesOneTimeTokensAfterOneDay() {
        long old = oneTime("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 25 HOUR)", null);
        long recent = oneTime("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 23 HOUR)", null);
        long valid = oneTime(AFTER_10M, null);

        AuthTokenCleanupService.Deleted deleted = cleanup.deleteExpired();

        assertThat(deleted.getOneTimeTokens()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM one_time_token WHERE id = ?", old))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM one_time_token WHERE id IN (?, ?)", recent, valid))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("§16 Refresh Token은 만료 + 7일이 지나야 삭제한다(폐기된 토큰 포함)")
    void deletesRefreshTokensAfterSevenDays() {
        long old = refresh("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 8 DAY)");
        jdbc.update(
                "UPDATE refresh_token SET revoked_at = UTC_TIMESTAMP(6), revoke_reason = 'LOGOUT' WHERE id = ?", old);
        long recent = refresh("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 6 DAY)");
        long valid = refresh("DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 20 DAY)");

        AuthTokenCleanupService.Deleted deleted = cleanup.deleteExpired();

        assertThat(deleted.getRefreshTokens()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE id = ?", old))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE id IN (?, ?)", recent, valid))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("회전으로 서로 가리키는 만료 토큰 묶음도 FK 위반 없이 함께 삭제한다")
    void deletesRotatedChain() {
        long first = refresh("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 20 DAY)");
        long second = refresh("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 DAY)");
        long third = refresh("DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 9 DAY)");
        jdbc.update("UPDATE refresh_token SET replaced_by_id = ? WHERE id = ?", second, first);
        jdbc.update("UPDATE refresh_token SET replaced_by_id = ? WHERE id = ?", third, second);

        AuthTokenCleanupService.Deleted deleted = cleanup.deleteExpired();

        assertThat(deleted.getRefreshTokens()).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM refresh_token")).isZero();
    }

    @Test
    @DisplayName("아직 살아 있는 토큰이 가리키는 만료 토큰이 없으면 체인은 그대로 둔다")
    void keepsLiveChain() {
        long old = refresh("DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 5 DAY)");
        long live = refresh("DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY)");
        jdbc.update("UPDATE refresh_token SET replaced_by_id = ? WHERE id = ?", live, old);

        assertThat(cleanup.deleteExpired().getRefreshTokens()).isZero();
        assertThat(jdbc.queryForObject("SELECT replaced_by_id FROM refresh_token WHERE id = ?", Long.class, old))
                .isEqualTo(live);
    }

    @Test
    @DisplayName("Access 블랙리스트는 원래 토큰 만료 시각이 지나면 삭제한다")
    void deletesExpiredBlacklistEntries() {
        jdbc.update(
                "INSERT INTO access_token_blacklist (jti, user_id, expires_at) VALUES (UUID(), ?, " + AGO_10M + ")",
                userId);
        jdbc.update(
                "INSERT INTO access_token_blacklist (jti, user_id, expires_at) VALUES (UUID(), ?, " + AFTER_10M + ")",
                userId);

        AuthTokenCleanupService.Deleted deleted = cleanup.deleteExpired();

        assertThat(deleted.getBlacklistEntries()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM access_token_blacklist")).isEqualTo(1);
    }

    @Test
    @DisplayName("정리할 것이 없어도, 여러 번 실행해도 안전하다")
    void idempotent() {
        refresh("DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 5 DAY)");

        AuthTokenCleanupService.Deleted first = cleanup.deleteExpired();
        AuthTokenCleanupService.Deleted second = cleanup.deleteExpired();

        assertThat(first).isEqualTo(new AuthTokenCleanupService.Deleted(0, 0, 0));
        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT COUNT(*) FROM refresh_token")).isEqualTo(1);
    }
}
