package com.crane.bandwebbackend.domain.song;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 곡·팀·세트리스트·가불·투표 API 통합 테스트 공통. 공유 시드(1 = 임원, 2·3 = 부원, 참여자 2 = APPROVED)를 읽기만 하고
 * 추가 데이터는 id 100 이상으로 직접 넣는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class SongApiTestSupport {

    protected static final String BASE = "/api/v1/clubs/1/performances/1";
    protected static final long ADMIN = 1L;
    protected static final long MEMBER = 2L;
    protected static final long MEMBER_B = 3L;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ResultActions send(MockHttpServletRequestBuilder req, long userId, String body) throws Exception {
        req.with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(userId, "sid", "jti", false), null, List.of())))
                .with(csrf())
                .header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    // 참여자(소속 membershipId). 소속 3 = 부원B
    protected void participant(long id, long membershipId, String status, String source) {
        jdbc.update(
                "INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                        + " VALUES (?, 1, 1, ?, ?, ?)",
                id,
                membershipId,
                status,
                source);
    }

    protected void song(long id, String title, String artist, String status) {
        jdbc.update(
                "INSERT INTO song (id, performance_id, title, artist, status, proposed_by_membership_id)"
                        + " VALUES (?, 1, ?, ?, ?, 1)",
                id,
                title,
                artist,
                status);
    }

    // 승인 팀. 팀원은 [참여자 id], 팀장은 첫 팀원
    protected void team(long id, String name, String status, long... memberIds) {
        jdbc.update(
                "INSERT INTO team (id, performance_id, name, status, source, created_by_user_id)"
                        + " VALUES (?, 1, ?, ?, 'ADMIN', 1)",
                id,
                name,
                status);
        for (long pmId : memberIds) {
            jdbc.update(
                    "INSERT INTO team_member (id, team_id, performance_id, performance_member_id)"
                            + " VALUES (?, ?, 1, ?)",
                    id * 10 + pmId,
                    id,
                    pmId);
        }
        jdbc.update("UPDATE team SET leader_member_id = ? WHERE id = ?", memberIds[0], id);
    }

    protected void session(long songId, long pmId, long partId) {
        jdbc.update(
                "INSERT INTO song_session_member (song_id, performance_id, performance_member_id, part_id)"
                        + " VALUES (?, 1, ?, ?)",
                songId,
                pmId,
                partId);
    }

    // 선정곡 = 순서·담당 팀·연주자를 모두 갖춘 곡(L-01~L-03)
    protected void confirmedSong(long id, String title, int order, long teamId, long pmId) {
        song(id, title, null, "CONFIRMED");
        jdbc.update("UPDATE song SET setlist_order = ?, assigned_team_id = ? WHERE id = ?", order, teamId, id);
        session(id, pmId, 1);
    }

    protected void lockPerformance() {
        jdbc.update("UPDATE performance SET status = 'LOCKED' WHERE id = 1");
    }

    protected long version(String table, long id) {
        return jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Long.class, id);
    }

    protected String lockedAuditDetail(String targetType, long targetId) {
        return jdbc.queryForObject(
                "SELECT detail FROM audit_log WHERE action = 'LOCKED_EXCEPTION_EDIT' AND target_type = ?"
                        + " AND target_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                targetType,
                targetId);
    }
}
