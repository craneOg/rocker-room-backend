package com.crane.bandwebbackend.domain.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ClubDiscoveryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    long me;
    int ipSeq;

    @BeforeEach
    void setUp() {
        clean();
        jdbc.update("INSERT INTO users (name, status) VALUES ('나', 'ACTIVE')");
        me = jdbc.queryForObject("SELECT id FROM users", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private long club(String name, String status, String joinCode, boolean searchable) {
        jdbc.update(
                "INSERT INTO club (name, description, club_type, status, join_code, profile_image_url, theme_color,"
                        + " is_searchable, created_by) VALUES (?, '소개', 'BAND', ?, ?, 'https://cdn.example/c.png', '#1A2B3C', ?, ?)",
                name,
                status,
                joinCode,
                searchable,
                me);
        return jdbc.queryForObject("SELECT id FROM club WHERE name = ?", Long.class, name);
    }

    private void membership(long clubId, String status) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, status, join_source, invited_at)"
                        + " VALUES (?, ?, ?, 'JOIN_CODE', CASE WHEN ? = 'INVITED' THEN UTC_TIMESTAMP(6) END)",
                clubId,
                me,
                status,
                status);
    }

    private Cookie access() {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(me, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest");
    }

    // 요청 제한(IP별)이 다른 테스트에 번지지 않도록 테스트마다 다른 IP를 쓴다
    private MockHttpServletRequestBuilder preview(String body) {
        String ip = "10.0.0." + (++ipSeq + (int) (System.nanoTime() % 200));
        return jsonPost("/api/v1/invitations/preview", body).with(request -> {
            request.setRemoteAddr(ip + "-" + System.nanoTime());
            return request;
        });
    }

    // ---- API 12 ----

    @Test
    @DisplayName("API 12: 공개이고 ACTIVE인 동아리만 이름순으로 검색하고, 가입 코드·소속자 정보는 싣지 않는다")
    void search() throws Exception {
        club("Crane Band", "ACTIVE", "ABCD234XYZ", true);
        club("Crane Jazz", "ACTIVE", null, true);
        club("Crane Hidden", "ACTIVE", null, false);
        club("Crane Closed", "INACTIVE", null, true);
        club("Other", "ACTIVE", null, true);

        String body = mockMvc.perform(
                        get("/api/v1/clubs/search").param("q", "crane").cookie(access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].club.name").value("Crane Band"))
                .andExpect(jsonPath("$.data.items[1].club.name").value("Crane Jazz"))
                .andExpect(jsonPath("$.data.items[0].club.id").isString())
                .andExpect(jsonPath("$.data.items[0].club.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.items[0].club.description").value("소개"))
                .andExpect(jsonPath("$.data.items[0].club.profileImageUrl").value("https://cdn.example/c.png"))
                .andExpect(jsonPath("$.data.items[0].club.themeColor").value("#1A2B3C"))
                .andExpect(jsonPath("$.data.items[0].existingMembershipStatus").isEmpty())
                .andExpect(jsonPath("$.data.items[0].canApply").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern("req-[a-z0-9]{12}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("ABCD234XYZ").doesNotContain("joinCode").doesNotContain("createdBy");
    }

    @Test
    @DisplayName("API 12: 내 소속 상태와 신청 가능 여부(C-06): 소속이 있으면 신청 불가, 거절·탈퇴·거부는 재신청 가능")
    void searchShowsMyMembership() throws Exception {
        String[][] cases = {
            {"A-active", "ACTIVE", "false"},
            {"B-onleave", "ON_LEAVE", "false"},
            {"C-graduated", "GRADUATED", "false"},
            {"D-pending", "PENDING", "false"},
            {"E-invited", "INVITED", "false"},
            {"F-rejected", "REJECTED", "true"},
            {"G-declined", "DECLINED", "true"},
            {"H-withdrawn", "WITHDRAWN", "true"}
        };
        for (String[] c : cases) {
            membership(club("Band " + c[0], "ACTIVE", null, true), c[1]);
        }
        club("Band I-none", "ACTIVE", null, true);

        var result = mockMvc.perform(
                        get("/api/v1/clubs/search").param("q", "Band").cookie(access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(9)));
        for (int i = 0; i < cases.length; i++) {
            result.andExpect(jsonPath("$.data.items[" + i + "].existingMembershipStatus")
                            .value(cases[i][1]))
                    .andExpect(jsonPath("$.data.items[" + i + "].canApply").value(Boolean.parseBoolean(cases[i][2])));
        }
        result.andExpect(jsonPath("$.data.items[8].existingMembershipStatus").isEmpty())
                .andExpect(jsonPath("$.data.items[8].canApply").value(true));
    }

    @Test
    @DisplayName("API 12: 페이지 번호는 0부터이고, 이름 순서가 페이지 사이에서 이어진다")
    void searchPaging() throws Exception {
        for (int i = 1; i <= 5; i++) {
            club("Paging " + i, "ACTIVE", null, true);
        }

        mockMvc.perform(get("/api/v1/clubs/search")
                        .param("q", "Paging")
                        .param("size", "2")
                        .cookie(access()))
                .andExpect(jsonPath("$.data.items[0].club.name").value("Paging 1"))
                .andExpect(jsonPath("$.data.items[1].club.name").value("Paging 2"))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        mockMvc.perform(get("/api/v1/clubs/search")
                        .param("q", "Paging")
                        .param("size", "2")
                        .param("page", "2")
                        .cookie(access()))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].club.name").value("Paging 5"));
    }

    @Test
    @DisplayName("API 12: like 와일드카드(%, _)는 문자 그대로 검색한다")
    void searchEscapesWildcards() throws Exception {
        club("100% Band", "ACTIVE", null, true);
        club("Plain Band", "ACTIVE", null, true);
        club("A_B Band", "ACTIVE", null, true);

        mockMvc.perform(get("/api/v1/clubs/search").param("q", "0%").cookie(access()))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].club.name").value("100% Band"));
        mockMvc.perform(get("/api/v1/clubs/search").param("q", "A_").cookie(access()))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mockMvc.perform(get("/api/v1/clubs/search").param("q", "%%").cookie(access()))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("API 12: 검색어가 없거나 공백 제외 2자 미만, 앞뒤 공백 제외 100자 초과, 잘못된 page·size는 400")
    void searchValidation() throws Exception {
        club("Crane Band", "ACTIVE", null, true);

        for (String q : new String[] {"", " ", "a", " a ", "a  ", "a".repeat(101)}) {
            mockMvc.perform(get("/api/v1/clubs/search").param("q", q).cookie(access()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/v1/clubs/search").cookie(access())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clubs/search")
                        .param("q", "cr")
                        .param("size", "0")
                        .cookie(access()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clubs/search")
                        .param("q", "cr")
                        .param("size", "101")
                        .cookie(access()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clubs/search")
                        .param("q", "cr")
                        .param("page", "-1")
                        .cookie(access()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clubs/search").param("q", "  cr  ").cookie(access()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API 12·13: 인증이 없으면 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/search").param("q", "cr")).andExpect(status().isUnauthorized());
        mockMvc.perform(jsonPost("/api/v1/clubs/join-code/resolve", "{\"joinCode\":\"ABCD234XYZ\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clubs/name-availability").param("name", "x"))
                .andExpect(status().isUnauthorized());
    }

    // ---- API 13 ----

    @Test
    @DisplayName("API 13: 가입 코드로 동아리와 내 가입 상태를 돌려준다")
    void resolve() throws Exception {
        long id = club("Crane Band", "ACTIVE", "ABCD234XYZ", false);

        mockMvc.perform(jsonPost("/api/v1/clubs/join-code/resolve", "{\"joinCode\":\"ABCD234XYZ\"}")
                        .cookie(access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.club.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.club.name").value("Crane Band"))
                .andExpect(jsonPath("$.data.club.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.existingMembershipStatus").isEmpty())
                .andExpect(jsonPath("$.data.canApply").value(true));

        membership(id, "PENDING");
        mockMvc.perform(jsonPost("/api/v1/clubs/join-code/resolve", "{\"joinCode\":\"ABCD234XYZ\"}")
                        .cookie(access()))
                .andExpect(jsonPath("$.data.existingMembershipStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.canApply").value(false));
    }

    @Test
    @DisplayName("API 13: 형식 오류·없는 코드·소문자·비활성 동아리·본문 없는 코드는 모두 같은 404")
    void resolveNotFound() throws Exception {
        club("Crane Band", "ACTIVE", "ABCD234XYZ", false);
        club("Closed Band", "INACTIVE", "WXYZ234ABC", false);

        for (String body : new String[] {
            "{\"joinCode\":\"ZZZZ234ABC\"}",
            "{\"joinCode\":\"abcd234xyz\"}",
            "{\"joinCode\":\"ABCD234XY\"}",
            "{\"joinCode\":\"ABCD234XYZ1\"}",
            "{\"joinCode\":\"ABCD1O0XYZ\"}",
            "{\"joinCode\":\"WXYZ234ABC\"}",
            "{\"joinCode\":null}",
            "{}"
        }) {
            mockMvc.perform(jsonPost("/api/v1/clubs/join-code/resolve", body).cookie(access()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.details").isEmpty());
        }
    }

    // ---- API 126 ----

    @Test
    @DisplayName("API 126: 대소문자·공백을 무시하고 비활성 동아리까지 포함해 비교한다")
    void nameAvailability() throws Exception {
        long id = club("Crane Band", "ACTIVE", null, true);
        club("Closed Club", "INACTIVE", null, false);

        for (String taken : new String[] {
            "Crane Band", "crane band", "  CRANE   BAND ", "CraneBand", "closed club", "Crane\u3000Band"
        }) {
            mockMvc.perform(get("/api/v1/clubs/name-availability")
                            .param("name", taken)
                            .cookie(access()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.available").value(false));
        }
        mockMvc.perform(get("/api/v1/clubs/name-availability")
                        .param("name", "Crane Bands")
                        .cookie(access()))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.meta.requestId").exists());

        // 이름 수정 중인 동아리는 자기 이름을 그대로 써도 사용 가능, 다른 동아리 이름은 불가
        mockMvc.perform(get("/api/v1/clubs/name-availability")
                        .param("name", "crane band")
                        .param("excludeClubId", String.valueOf(id))
                        .cookie(access()))
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(get("/api/v1/clubs/name-availability")
                        .param("name", "Closed Club")
                        .param("excludeClubId", String.valueOf(id))
                        .cookie(access()))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("API 126: 이름이 비었거나 100자를 넘거나 excludeClubId가 숫자가 아니면 400")
    void nameAvailabilityValidation() throws Exception {
        for (String name : new String[] {"", "   ", "a".repeat(101)}) {
            mockMvc.perform(get("/api/v1/clubs/name-availability")
                            .param("name", name)
                            .cookie(access()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/v1/clubs/name-availability").cookie(access())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/clubs/name-availability")
                        .param("name", "ok")
                        .param("excludeClubId", "abc")
                        .cookie(access()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("excludeClubId"));
        mockMvc.perform(get("/api/v1/clubs/name-availability")
                        .param("name", "a".repeat(100))
                        .cookie(access()))
                .andExpect(status().isOk());
    }

    // ---- API 127 ----

    @Test
    @DisplayName("API 127: 로그인 없이 이름·유형·소개·사진·대표 색상만 돌려준다(id·가입 코드·소속 정보 없음)")
    void invitationPreview() throws Exception {
        club("Crane Band", "ACTIVE", "K7M2Q9XA4B", false);

        String body = mockMvc.perform(preview("{\"code\":\"K7M2Q9XA4B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Crane Band"))
                .andExpect(jsonPath("$.data.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.description").value("소개"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example/c.png"))
                .andExpect(jsonPath("$.data.themeColor").value("#1A2B3C"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("K7M2Q9XA4B").doesNotContain("joinCode").doesNotContain("createdBy");
    }

    @Test
    @DisplayName("API 127: 형식 오류·없는 코드·소문자·사용 끔·비활성 동아리는 모두 같은 404")
    void invitationPreviewNotFound() throws Exception {
        club("Crane Band", "ACTIVE", "K7M2Q9XA4B", false);
        club("No Code", "ACTIVE", null, false);
        club("Closed Band", "INACTIVE", "WXYZ234ABC", false);

        for (String body : new String[] {
            "{\"code\":\"ZZZZ234ABC\"}",
            "{\"code\":\"k7m2q9xa4b\"}",
            "{\"code\":\"K7M2Q9\"}",
            "{\"code\":\"WXYZ234ABC\"}",
            "{\"code\":null}",
            "{}"
        }) {
            mockMvc.perform(preview(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.details").isEmpty());
        }
    }

    @Test
    @DisplayName("API 127: 같은 IP가 한도를 넘으면 429 RULE_VIOLATION(RATE_LIMITED)과 Retry-After, 다른 IP는 영향 없음")
    void invitationPreviewRateLimited() throws Exception {
        club("Crane Band", "ACTIVE", "K7M2Q9XA4B", false);
        String body = "{\"code\":\"K7M2Q9XA4B\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(sameIp("203.0.113.9", body)).andExpect(status().isOk());
        }
        mockMvc.perform(sameIp("203.0.113.9", body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].field").value("request"))
                .andExpect(jsonPath("$.error.details[0].reason").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.requestId").value(matchesPattern("req-[a-z0-9]{12}")));
        // 없는 코드로 요청해도 같은 한도에 센다
        mockMvc.perform(sameIp("203.0.113.9", "{\"code\":\"ZZZZ234ABC\"}")).andExpect(status().isTooManyRequests());
        long retryAfter = Long.parseLong(mockMvc.perform(sameIp("203.0.113.9", body))
                .andReturn()
                .getResponse()
                .getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);

        mockMvc.perform(sameIp("203.0.113.10", body)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder sameIp(String ip, String body) {
        return jsonPost("/api/v1/invitations/preview", body).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @Test
    @DisplayName("API 127: X-Requested-With 없음 403, JSON이 아닌 Content-Type 400")
    void invitationPreviewGuards() throws Exception {
        club("Crane Band", "ACTIVE", "K7M2Q9XA4B", false);

        mockMvc.perform(post("/api/v1/invitations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"K7M2Q9XA4B\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/invitations/preview")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
