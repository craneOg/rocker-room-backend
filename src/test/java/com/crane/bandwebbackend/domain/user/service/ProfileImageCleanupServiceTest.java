package com.crane.bandwebbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.storage.InMemoryImageStorage;
import com.crane.bandwebbackend.global.storage.InMemoryStorageConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, InMemoryStorageConfig.class})
@ActiveProfiles("test")
@SpringBootTest
class ProfileImageCleanupServiceTest {

    @Autowired
    ProfileImageCleanupService cleanup;

    @Autowired
    InMemoryImageStorage storage;

    @Autowired
    JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void setUp() {
        clean();
        jdbc.update("INSERT INTO users (name, handle, status) VALUES ('tester', 'cleanup_user', 'ACTIVE')");
        userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM user_withdrawal_archive");
        jdbc.update("DELETE FROM users");
        storage.clear();
    }

    // ---- 도우미 ----

    // minutesAgo분 전에 올라온 객체를 만든다
    private String object(String key, long minutesAgo) {
        storage.put(key, new byte[] {1}, "image/png");
        storage.get(key).modifiedAt = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
        return key;
    }

    private void profileUses(String key) {
        jdbc.update("UPDATE users SET profile_image_url = ? WHERE id = ?", storage.publicUrl(key), userId);
    }

    private void archiveUses(String key) {
        jdbc.update(
                "INSERT INTO user_withdrawal_archive (user_id, name, profile_image_url, withdrawn_at) VALUES (?, 'n', ?,"
                        + " UTC_TIMESTAMP(6))",
                userId,
                storage.publicUrl(key));
    }

    // ---- 임시 객체 ----

    @Test
    @DisplayName("임시 객체는 연결 가능 시간(1시간)이 지난 것만 지운다(사용자·동아리 용도 모두)")
    void expiredTmp() {
        object("tmp/users/1/old.png", 61);
        object("tmp/club-profile/1/old.png", 120);
        object("tmp/users/1/fresh.png", 59);

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.getExpiredTmpDeleted()).isEqualTo(2);
        assertThat(storage.exists("tmp/users/1/old.png")).isFalse();
        assertThat(storage.exists("tmp/club-profile/1/old.png")).isFalse();
        assertThat(storage.exists("tmp/users/1/fresh.png")).isTrue();
    }

    // ---- 영구 객체 ----

    @Test
    @DisplayName("영구 객체는 프로필·탈퇴 보관 어디에도 없고 유예(1시간)가 지난 것만 지운다")
    void orphans() {
        String current = object("users/" + userId + "/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png", 600);
        String archived = object("users/" + userId + "/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.png", 600);
        String orphan = object("users/" + userId + "/cccccccc-cccc-cccc-cccc-cccccccccccc.png", 600);
        String justCopied = object("users/" + userId + "/dddddddd-dddd-dddd-dddd-dddddddddddd.png", 5);
        profileUses(current);
        archiveUses(archived);

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.getOrphansDeleted()).isEqualTo(1);
        assertThat(result.isOrphanScanAborted()).isFalse();
        assertThat(storage.exists(orphan)).isFalse();
        assertThat(storage.exists(current)).isTrue();
        assertThat(storage.exists(archived)).isTrue(); // 탈퇴 후 90일 안에 복구하면 되살아나는 사진
        assertThat(storage.exists(justCopied)).isTrue(); // 아직 커밋 전일 수 있는 방금 복사한 객체
    }

    @Test
    @DisplayName("공개 주소를 바꿔도(저장된 주소의 도메인이 달라도) 같은 키의 사진은 참조 중으로 본다")
    void publicBaseChangeDoesNotOrphan() {
        String current = object("users/" + userId + "/11111111-1111-1111-1111-111111111111.png", 600);
        String archived = object("users/" + userId + "/22222222-2222-2222-2222-222222222222.png", 600);
        object("users/" + userId + "/33333333-3333-3333-3333-333333333333.png", 600);
        jdbc.update(
                "UPDATE users SET profile_image_url = ? WHERE id = ?", "https://old.cdn.example/" + current, userId);
        jdbc.update(
                "INSERT INTO user_withdrawal_archive (user_id, name, profile_image_url, withdrawn_at) VALUES (?, 'n', ?,"
                        + " UTC_TIMESTAMP(6))",
                userId,
                "https://old.cdn.example/" + archived);

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.getOrphansDeleted()).isEqualTo(1);
        assertThat(storage.exists(current)).isTrue();
        assertThat(storage.exists(archived)).isTrue();
        assertThat(storage.exists("users/" + userId + "/33333333-3333-3333-3333-333333333333.png"))
                .isFalse();
    }

    @Test
    @DisplayName("관리 대상이 아닌 접두사(misc/ 등)와 users/·clubs/ 아래의 사진 키 형식이 아닌 객체는 건드리지 않는다")
    void otherPrefixesUntouched() {
        String current = object("users/" + userId + "/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png", 600);
        profileUses(current);
        object("misc/x.png", 6000);
        object("users/" + userId + "/note.png", 6000);
        object("clubs/1/poster/" + uuid(1), 6000);

        cleanup.cleanup();

        assertThat(storage.exists("misc/x.png")).isTrue();
        assertThat(storage.exists("users/" + userId + "/note.png")).isTrue();
        assertThat(storage.exists("clubs/1/poster/" + uuid(1))).isTrue();
    }

    // 사진 키 형식({uuid}.png)의 파일 이름. i마다 다르다
    private static String uuid(int i) {
        return String.format("%08d-0000-0000-0000-000000000000.png", i);
    }

    // ---- 동아리 사진(clubs/) ----

    private String clubPhoto(long clubId, char c, long minutesAgo) {
        return object(
                "clubs/" + clubId + "/" + String.valueOf(c).repeat(8) + "-cccc-cccc-cccc-cccccccccccc.png", minutesAgo);
    }

    private long club(String name, String status, String photoKey) {
        jdbc.update(
                "INSERT INTO club (name, created_by, status, profile_image_url) VALUES (?, ?, ?, ?)",
                name,
                userId,
                status,
                photoKey == null ? null : storage.publicUrl(photoKey));
        return jdbc.queryForObject("SELECT MAX(id) FROM club", Long.class);
    }

    @Test
    @DisplayName("clubs/ 객체는 어떤 동아리도 참조하지 않고 유예가 지난 것만 지운다(비활성 동아리 사진은 유지)")
    void clubOrphans() {
        String active = clubPhoto(1, 'a', 600);
        String inactive = clubPhoto(2, 'b', 600);
        String orphan = clubPhoto(3, 'c', 600);
        String justCopied = clubPhoto(4, 'd', 5);
        club("활성", "ACTIVE", active);
        club("비활성", "INACTIVE", inactive);

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.getOrphansDeleted()).isEqualTo(1);
        assertThat(result.isOrphanScanAborted()).isFalse();
        assertThat(storage.exists(orphan)).isFalse();
        assertThat(storage.exists(active)).isTrue();
        assertThat(storage.exists(inactive)).isTrue();
        assertThat(storage.exists(justCopied)).isTrue();
    }

    @Test
    @DisplayName("clubs/ 안전장치가 걸려도 users/ 정리는 진행하고, 중단 여부는 결과에 true로 남는다")
    void clubGuardDoesNotBlockUsers() {
        String orphanUser = object("users/" + userId + "/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee.png", 600);
        for (int i = 0; i < ProfileImageCleanupService.MISCONFIG_GUARD_MIN_CANDIDATES; i++) {
            object("clubs/1/" + uuid(i), 600);
        }

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.isOrphanScanAborted()).isTrue();
        assertThat(result.getOrphansDeleted()).isEqualTo(1);
        assertThat(storage.exists(orphanUser)).isFalse();
        assertThat(storage.size()).isEqualTo(ProfileImageCleanupService.MISCONFIG_GUARD_MIN_CANDIDATES);
    }

    @Test
    @DisplayName("후보가 20건 이상인데 참조가 하나도 없으면(공개 주소 설정 불일치 의심) 영구 객체를 지우지 않는다")
    void misconfigurationGuard() {
        for (int i = 0; i < ProfileImageCleanupService.MISCONFIG_GUARD_MIN_CANDIDATES; i++) {
            object("users/" + userId + "/" + uuid(i), 600);
        }

        ProfileImageCleanupService.Result result = cleanup.cleanup();

        assertThat(result.isOrphanScanAborted()).isTrue();
        assertThat(result.getOrphansDeleted()).isZero();
        assertThat(storage.size()).isEqualTo(ProfileImageCleanupService.MISCONFIG_GUARD_MIN_CANDIDATES);
    }

    @Test
    @DisplayName("한 번 실행에 지우는 수를 제한하고 남은 것은 다음 실행에서 지우며, 다시 실행해도 안전하다")
    void deleteCapAndIdempotent() {
        String keep = object("users/" + userId + "/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee.png", 600);
        profileUses(keep);
        int total = ProfileImageCleanupService.MAX_DELETES_PER_RUN + 10;
        for (int i = 0; i < total; i++) {
            object("users/" + userId + "/" + uuid(i), 600);
        }

        assertThat(cleanup.cleanup().getOrphansDeleted()).isEqualTo(ProfileImageCleanupService.MAX_DELETES_PER_RUN);
        assertThat(storage.size()).isEqualTo(1 + 10);
        assertThat(cleanup.cleanup().getOrphansDeleted()).isEqualTo(10);
        assertThat(storage.size()).isEqualTo(1);
        assertThat(storage.exists(keep)).isTrue();
        assertThat(cleanup.cleanup().getOrphansDeleted()).isZero();
    }
}
