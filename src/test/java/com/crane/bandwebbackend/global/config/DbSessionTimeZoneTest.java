package com.crane.bandwebbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
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
class DbSessionTimeZoneTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("저장은 UTC: MySQL 세션 시간대가 UTC(+00:00)로 맞춰진다")
    void sessionTimeZoneIsUtc() {
        String zone = jdbc.queryForObject("SELECT @@session.time_zone", String.class);

        assertThat(zone).isEqualTo("+00:00");
    }
}
