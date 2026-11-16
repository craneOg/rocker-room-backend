package com.crane.bandwebbackend.domain.membership.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwt;

    @Autowired
    CookieFactory cookies;

    @Test
    @DisplayName("API-020/SADM-003: 소속 없는 SA는 실제 JWT로 ACTIVE 정회원 후임 후보를 조회한다")
    void superAdminWithoutMembershipCanListCandidates() throws Exception {
        Cookie cookie = superAdminCookie();
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        mvc.perform(get("/api/v1/clubs/1/memberships")
                        .cookie(cookie)
                        .param("status", "ACTIVE")
                        .param("memberType", "REGULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
        mvc.perform(get("/api/v1/clubs/1/memberships").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/clubs/1/memberships").cookie(cookie).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("API-020/G-04: SA도 없는 동아리는 404이며 일반 외부인에게 조회 권한을 주지 않는다")
    void directoryAccessStillChecksClubAndOrdinaryMembership() throws Exception {
        mvc.perform(get("/api/v1/clubs/999/memberships").cookie(superAdminCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/clubs/1/memberships").with(authentication(actor(99L))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clubs/1/memberships")
                        .with(authentication(actor(2L)))
                        .param("status", "ACTIVE"))
                .andExpect(status().isForbidden());
    }

    private Cookie superAdminCookie() {
        jdbc.update("INSERT INTO users (id, name, handle, is_super_admin) VALUES (99, '운영자', 'super_admin', true)");
        return new Cookie(
                cookies.accessName(),
                jwt.createAccessToken(99L, "sa-directory-test", 0).getToken());
    }

    @Test
    @DisplayName("API-21: 정회원은 타인의 공개 상세만 보고 외부인은 동아리 소속을 볼 수 없다")
    void regularMemberSeesPublicDetailAndOutsiderIsHidden() throws Exception {
        mvc.perform(get("/api/v1/clubs/1/memberships/3").with(authentication(actor(2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("부원B"))
                .andExpect(jsonPath("$.data.status").doesNotExist());

        mvc.perform(get("/api/v1/clubs/1/memberships/3").with(authentication(actor(99L))))
                .andExpect(status().isNotFound());
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
