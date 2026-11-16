package com.crane.bandwebbackend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.audition.controller.AuditionController;
import com.crane.bandwebbackend.domain.audition.controller.MyAuditionController;
import com.crane.bandwebbackend.domain.membership.controller.JoinBulkDecisionController;
import com.crane.bandwebbackend.domain.membership.controller.MemberLookupController;
import com.crane.bandwebbackend.domain.membership.controller.MembershipBulkController;
import com.crane.bandwebbackend.domain.membership.controller.MembershipController;
import com.crane.bandwebbackend.domain.participant.controller.PerformanceMemberController;
import com.crane.bandwebbackend.domain.performance.controller.PerformanceController;
import com.crane.bandwebbackend.domain.performance.controller.PerformanceLockController;
import com.crane.bandwebbackend.domain.performance.controller.PerformanceStatusController;
import com.crane.bandwebbackend.domain.schedule.controller.CalendarController;
import com.crane.bandwebbackend.domain.schedule.controller.PracticeScheduleController;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ApiV1ControllerRegistrationIntegrationTest {
    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProvider jwt;

    @Autowired
    private CookieFactory cookies;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("API-v1 기본 설정에서 플래그 없이 담당 12개 컨트롤러의 라우트를 모두 등록한다")
    void registersAllControllersWithoutFlag() {
        assertThat(environment.getProperty("rocker.api-v1.enabled")).isNull();
        var handlers = mappings.getHandlerMethods().values().stream()
                .<Class<?>>map(handler -> handler.getBeanType())
                .collect(Collectors.toSet());
        assertThat(handlers)
                .containsAll(List.of(
                        MembershipController.class,
                        MembershipBulkController.class,
                        MemberLookupController.class,
                        JoinBulkDecisionController.class,
                        PerformanceController.class,
                        PerformanceLockController.class,
                        PerformanceStatusController.class,
                        PerformanceMemberController.class,
                        CalendarController.class,
                        PracticeScheduleController.class,
                        AuditionController.class,
                        MyAuditionController.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/clubs/1/memberships", "/api/v1/clubs/1/performances", "/api/v1/clubs/1/performances/1/members",
                "/api/v1/clubs/1/performances/1/auditions", "/api/v1/me/auditions", "/api/v1/me/calendar",
                "/api/v1/clubs/1/performances/1/schedules"
            })
    @DisplayName("G-01/API-v1 플래그 없는 기본 설정에서 실제 JWT로 도메인 조회가 성공한다")
    void authenticatedReadRoutesAreAvailable(String path) throws Exception {
        mvc.perform(get(path)
                        .cookie(adminCookie())
                        .param("from", "2099-01-01T00:00:00+09:00")
                        .param("to", "2099-01-02T00:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("G-01/U14 플래그를 제거해도 비인증 401과 CSRF 헤더 누락 403을 유지한다")
    void securityFiltersRemainEffective() throws Exception {
        String path = "/api/v1/clubs/1/performances/1/auditions";
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post(path)
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post(path)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path)
                        .cookie(adminCookie())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private Cookie adminCookie() {
        return new Cookie(
                cookies.accessName(),
                jwt.createAccessToken(1L, "route-registration-test", 0).getToken());
    }
}
