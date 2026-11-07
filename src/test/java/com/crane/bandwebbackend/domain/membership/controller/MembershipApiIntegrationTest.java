package com.crane.bandwebbackend.domain.membership.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "rocker.api-v1.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipApiIntegrationTest {
    @Autowired
    MockMvc mvc;

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
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
