package com.crane.bandwebbackend.domain.home.controller;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 홈 API 테스트 공통: 동아리 하나에 임원 1명(adminUser)·일반 부원 1명(memberUser)을 만들고, 쓴 테이블을 모두 비운다
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
abstract class HomeApiTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    long adminUser;
    long memberUser;
    long adminMid;
    long memberMid;
    long clubId;
    int seq;

    @BeforeEach
    void setUpBase() {
        clean();
        adminUser = user();
        memberUser = user();
        jdbc.update(
                "INSERT INTO club (name, club_type, status, created_by) VALUES ('홈밴드', 'BAND', 'ACTIVE', ?)",
                adminUser);
        clubId = jdbc.queryForObject("SELECT id FROM club WHERE name = '홈밴드'", Long.class);
        adminMid = member(adminUser, "CLUB_ADMIN", "ACTIVE", "REGULAR", "CLUB_CREATOR", null);
        memberMid = member(memberUser, "MEMBER", "ACTIVE", "REGULAR", "JOIN_CODE", null);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM practice_schedule");
        jdbc.update("DELETE FROM available_slot");
        jdbc.update("DELETE FROM song_vote");
        jdbc.update("DELETE FROM song_playability");
        jdbc.update("DELETE FROM song_session_member");
        jdbc.update("DELETE FROM audition_entry");
        jdbc.update("DELETE FROM song");
        jdbc.update("UPDATE team SET leader_member_id = NULL");
        jdbc.update("DELETE FROM team_member");
        jdbc.update("DELETE FROM team");
        jdbc.update("DELETE FROM performance_policy");
        jdbc.update("DELETE FROM performance_member");
        jdbc.update("DELETE FROM performance");
        jdbc.update("DELETE FROM part");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    long user() {
        int n = ++seq;
        jdbc.update("INSERT INTO users (name, handle) VALUES (?, ?)", "유저" + n, "home_user_" + n);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, "home_user_" + n);
    }

    // invitedAt은 INVITED 소속의 초대 시각(UTC 문자열). 아니면 null
    long member(long userId, String role, String status, String type, String source, String invitedAt) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, role, status, member_type, join_source, invited_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                clubId,
                userId,
                role,
                status,
                type,
                source,
                invitedAt);
        return jdbc.queryForObject(
                "SELECT id FROM membership WHERE club_id = ? AND user_id = ?", Long.class, clubId, userId);
    }

    // 새 사용자로 소속 하나를 만든다
    long extraMember(String role, String status, String type) {
        return member(user(), role, status, type, "JOIN_CODE", null);
    }

    long performance(String status) {
        jdbc.update(
                "INSERT INTO performance (club_id, title, preset, status, created_by_user_id) VALUES (?, ?, 'BAND', ?, ?)",
                clubId,
                "공연" + (++seq),
                status,
                adminUser);
        return jdbc.queryForObject("SELECT MAX(id) FROM performance", Long.class);
    }

    long team(long performanceId, String status) {
        jdbc.update(
                "INSERT INTO team (performance_id, name, status, source, created_by_user_id) VALUES (?, ?, ?, 'SELF', ?)",
                performanceId,
                "팀" + (++seq),
                status,
                adminUser);
        return jdbc.queryForObject("SELECT MAX(id) FROM team", Long.class);
    }

    long participation(long performanceId, long membershipId, String status) {
        jdbc.update(
                "INSERT INTO performance_member (performance_id, club_id, membership_id, status, source)"
                        + " VALUES (?, ?, ?, ?, 'APPLICATION')",
                performanceId,
                clubId,
                membershipId,
                status);
        return jdbc.queryForObject(
                "SELECT id FROM performance_member WHERE performance_id = ? AND membership_id = ?",
                Long.class,
                performanceId,
                membershipId);
    }

    // 정책 행이 없으면 만들고 set절(예: "vote_state = 'OPEN'")을 적용한다
    void policy(long performanceId, String set) {
        jdbc.update("INSERT IGNORE INTO performance_policy (performance_id) VALUES (?)", performanceId);
        jdbc.update("UPDATE performance_policy SET " + set + " WHERE performance_id = ?", performanceId);
    }

    void teamMember(long teamId, long performanceId, long pmId) {
        jdbc.update(
                "INSERT INTO team_member (team_id, performance_id, performance_member_id) VALUES (?, ?, ?)",
                teamId,
                performanceId,
                pmId);
    }

    void leader(long teamId, long pmId) {
        jdbc.update("UPDATE team SET leader_member_id = ? WHERE id = ?", pmId, teamId);
    }

    long part() {
        jdbc.update("INSERT INTO part (club_id, name) VALUES (?, ?)", clubId, "파트" + (++seq));
        return jdbc.queryForObject("SELECT MAX(id) FROM part", Long.class);
    }

    // candidateTeamId는 팀 범위 후보곡일 때만
    long song(long performanceId, String status, long proposerMid, Long candidateTeamId) {
        jdbc.update(
                "INSERT INTO song (performance_id, candidate_team_id, title, status, withdrawal_type,"
                        + " proposed_by_membership_id) VALUES (?, ?, ?, ?, ?, ?)",
                performanceId,
                candidateTeamId,
                "곡" + (++seq),
                status,
                "WITHDRAWN".equals(status) ? "PROPOSER" : null,
                proposerMid);
        return jdbc.queryForObject("SELECT MAX(id) FROM song", Long.class);
    }

    void playability(long songId, long performanceId, long pmId, long partId) {
        jdbc.update(
                "INSERT INTO song_playability (song_id, performance_id, performance_member_id, part_id, answer,"
                        + " submitted_by_user_id) VALUES (?, ?, ?, ?, 'POSSIBLE', ?)",
                songId,
                performanceId,
                pmId,
                partId,
                adminUser);
    }

    void vote(long songId, long performanceId, long pmId) {
        jdbc.update(
                "INSERT INTO song_vote (song_id, performance_id, performance_member_id, score, submitted_by_user_id)"
                        + " VALUES (?, ?, ?, 1, ?)",
                songId,
                performanceId,
                pmId,
                adminUser);
    }

    void slot(long pmId) {
        jdbc.update(
                "INSERT INTO available_slot (performance_member_id, slot_start) VALUES (?, '2030-01-01 00:00:00')",
                pmId);
    }

    // result가 null이면 심사 대기
    void audition(long performanceId, long membershipId, String result) {
        jdbc.update(
                "INSERT INTO song (performance_id, title, proposed_by_membership_id) VALUES (?, ?, ?)",
                performanceId,
                "곡" + (++seq),
                membershipId);
        long songId = jdbc.queryForObject("SELECT MAX(id) FROM song", Long.class);
        jdbc.update(
                "INSERT INTO audition_entry (performance_id, song_id, result, reviewed_by_user_id, reviewed_at,"
                        + " created_by_user_id) VALUES (?, ?, ?, ?, ?, ?)",
                performanceId,
                songId,
                result,
                result == null ? null : adminUser,
                result == null ? null : utcAgo(0),
                adminUser);
    }

    Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    // UTC 기준 현재에서 days일 뒤(음수면 이전). 마감 시각 문자열용
    static String utcAfter(int days) {
        return utcAgo(-days);
    }

    // UTC 기준 현재에서 days일 전 시각 문자열
    static String utcAgo(int days) {
        return java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .minusDays(days)
                .toString()
                .replace('T', ' ');
    }

    String path(String suffix) {
        return "/api/v1/clubs/" + clubId + suffix;
    }
}
