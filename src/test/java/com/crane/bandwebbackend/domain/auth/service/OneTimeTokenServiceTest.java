package com.crane.bandwebbackend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.auth.service.OneTimeTokenService.Consumed;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class OneTimeTokenServiceTest {

    @Autowired
    OneTimeTokenService service;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM users");
    }

    @Test
    @DisplayName("0.5 원문은 43자 base64url이고 DB에는 SHA-256 해시 32바이트만 있다")
    void storesOnlyHash() {
        String raw = service.issue(OneTimeTokenPurpose.OAUTH_STATE, null, "/a", Duration.ofMinutes(10));

        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM one_time_token", byte[].class);
        assertThat(stored).hasSize(32).isEqualTo(TokenCodec.hash(raw));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM one_time_token WHERE token_hash = ?", Integer.class, raw.getBytes()))
                .isZero();
    }

    @Test
    @DisplayName("0.5 한 번만 소비되고, 소비한 토큰이 가리키던 사용자·returnTo를 돌려준다")
    void consumedOnce() {
        User user = userRepository.save(User.createFromProvider("테스터", null));
        String raw = service.issue(OneTimeTokenPurpose.LOGIN_EXCHANGE, user, "/join/X", Duration.ofSeconds(60));

        Optional<Consumed> first = service.consume(raw, OneTimeTokenPurpose.LOGIN_EXCHANGE);

        assertThat(first).contains(new Consumed(user.getId(), "/join/X"));
        assertThat(service.consume(raw, OneTimeTokenPurpose.LOGIN_EXCHANGE)).isEmpty();
    }

    @Test
    @DisplayName("0.5 용도가 다르면 소비되지 않고, 이후 올바른 용도로는 소비할 수 있다")
    void purposeMustMatch() {
        String raw = service.issue(OneTimeTokenPurpose.OAUTH_STATE, null, null, Duration.ofMinutes(10));

        assertThat(service.consume(raw, OneTimeTokenPurpose.LOGIN_EXCHANGE)).isEmpty();
        assertThat(service.consume(raw, OneTimeTokenPurpose.OAUTH_STATE)).contains(new Consumed(null, null));
    }

    @Test
    @DisplayName("0.5 만료된 토큰·없는 토큰·비어 있거나 너무 긴 값은 소비되지 않는다")
    void invalidInputs() {
        String raw = service.issue(OneTimeTokenPurpose.OAUTH_STATE, null, null, Duration.ofMinutes(10));
        jdbc.update("UPDATE one_time_token SET expires_at = '2000-01-01 00:00:00'");

        assertThat(service.consume(raw, OneTimeTokenPurpose.OAUTH_STATE)).isEmpty();
        assertThat(service.consume("never-issued", OneTimeTokenPurpose.OAUTH_STATE))
                .isEmpty();
        assertThat(service.consume(null, OneTimeTokenPurpose.OAUTH_STATE)).isEmpty();
        assertThat(service.consume("  ", OneTimeTokenPurpose.OAUTH_STATE)).isEmpty();
        assertThat(service.consume("a".repeat(101), OneTimeTokenPurpose.OAUTH_STATE))
                .isEmpty();
    }

    @Test
    @DisplayName("0.5 동시에 여러 요청이 와도 정확히 한 요청만 소비에 성공한다")
    void concurrentConsumeSucceedsOnce() throws Exception {
        String raw = service.issue(OneTimeTokenPurpose.LOGIN_EXCHANGE, null, null, Duration.ofSeconds(60));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.consume(raw, OneTimeTokenPurpose.LOGIN_EXCHANGE).isPresent();
            }));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
    }
}
