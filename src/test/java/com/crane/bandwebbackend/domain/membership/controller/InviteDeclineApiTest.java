package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API-027 초대 거절: reason은 선택이라 body 없이도 호출할 수 있다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InviteDeclineApiTest {
    private static final String URL = "/api/v1/clubs/1/memberships/3/decline";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void invite() {
        jdbc.update("UPDATE membership SET status = 'INVITED', invited_at = UTC_TIMESTAMP(6) WHERE id = 3");
    }

    @Test
    @DisplayName("API-027: body 없이 거절하면 200 DECLINED")
    void declineWithoutBody() throws Exception {
        mvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
        assertStatus("DECLINED");
    }

    @Test
    @DisplayName("API-027: reason과 함께 거절하면 200 DECLINED")
    void declineWithReason() throws Exception {
        mvc.perform(request().contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"일정이 안 맞아요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
        assertStatus("DECLINED");
    }

    @Test
    @DisplayName("API-027: reason이 500자를 넘으면 400 VALIDATION_ERROR")
    void reasonTooLong() throws Exception {
        mvc.perform(request()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertStatus("INVITED");
    }

    private MockHttpServletRequestBuilder request() {
        return post(URL)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(3, "sid", "jti", false), null, List.of())))
                .header("X-Requested-With", "XMLHttpRequest");
    }

    private void assertStatus(String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM membership WHERE id = 3", String.class))
                .isEqualTo(expected);
    }
}
