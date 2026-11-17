package com.crane.bandwebbackend.domain.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.storage.InMemoryImageStorage;
import com.crane.bandwebbackend.global.storage.InMemoryStorageConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@Import({TestcontainersConfiguration.class, InMemoryStorageConfig.class})
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ClubCreateApiTest {

    private static final String CDN = InMemoryImageStorage.PUBLIC_BASE;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    @Autowired
    InMemoryImageStorage storage;

    long me;
    long other;

    @BeforeEach
    void setUp() {
        clean();
        me = user("나", "me_user_x");
        other = user("남", "other_user_x");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM part");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM users");
        storage.clear();
    }

    // ---- 도우미 ----

    private long user(String name, String handle) {
        jdbc.update("INSERT INTO users (name, handle, status) VALUES (?, ?, 'ACTIVE')", name, handle);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder create(String body) {
        return post("/api/v1/clubs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(me));
    }

    private static String json(String name, String extra) {
        return "{\"name\":\"" + name + "\",\"clubType\":\"BAND\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String tmpKey(long userId, String purposePrefix) {
        String key = "tmp/" + purposePrefix + "/" + userId + "/" + UUID.randomUUID() + ".png";
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        return key;
    }

    private boolean hasClubObject() {
        return !storage.list("clubs/").isEmpty();
    }

    // ---- 성공 ----

    @Test
    @DisplayName("API 11: 최소 요청이면 201, ACTIVE, isSearchable=false, joinCode=null로 생성한다")
    void minimal() throws Exception {
        mockMvc.perform(create(json("  크레인 밴드  ", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.name").value("크레인 밴드"))
                .andExpect(jsonPath("$.data.description").isEmpty())
                .andExpect(jsonPath("$.data.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.isSearchable").value(false))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.themeColor").isEmpty())
                .andExpect(jsonPath("$.data.joinCode").isEmpty())
                .andExpect(jsonPath("$.data.createdByUserId").value(String.valueOf(me)))
                .andExpect(jsonPath("$.data.createdAt").value(containsString("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt").value(containsString("+09:00")))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern("req-[a-z0-9]{12}")));
        assertThat(count("club")).isEqualTo(1);
    }

    @Test
    @DisplayName("API 11: 요청의 isSearchable=true가 DB와 응답(isSearchable 키)에 그대로 반영된다")
    void searchableTrue() throws Exception {
        mockMvc.perform(create(
                        json("공개 밴드", "\"isSearchable\":true,\"themeColor\":\"#1A2B3C\",\"description\":\"소개\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isSearchable").value(true))
                .andExpect(jsonPath("$.data.searchable").doesNotExist())
                .andExpect(jsonPath("$.data.themeColor").value("#1A2B3C"))
                .andExpect(jsonPath("$.data.description").value("소개"));
        assertThat(jdbc.queryForObject("SELECT is_searchable FROM club", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT theme_color FROM club", String.class))
                .isEqualTo("#1A2B3C");
    }

    @Test
    @DisplayName("API 11: 생성자는 ACTIVE·REGULAR·CLUB_ADMIN·CLUB_CREATOR 소속이 되고 기본 파트 6개(순서 0~5)가 생긴다")
    void membershipAndParts() throws Exception {
        mockMvc.perform(create(json("밴드", ""))).andExpect(status().isCreated());

        Map<String, Object> m = jdbc.queryForMap("SELECT * FROM membership");
        assertThat(m.get("user_id")).isEqualTo(me);
        assertThat(m.get("status")).isEqualTo("ACTIVE");
        assertThat(m.get("member_type")).isEqualTo("REGULAR");
        assertThat(m.get("role")).isEqualTo("CLUB_ADMIN");
        assertThat(m.get("join_source")).isEqualTo("CLUB_CREATOR");
        assertThat(m.get("approved_at")).isNotNull();

        List<Map<String, Object>> parts =
                jdbc.queryForList("SELECT name, display_order FROM part ORDER BY display_order");
        assertThat(parts).extracting(p -> p.get("name")).containsExactly("보컬", "기타", "베이스", "키보드", "드럼", "그 외");
        assertThat(parts).extracting(p -> p.get("display_order")).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("API 11: description이 공백뿐이면 null로 저장한다")
    void blankDescription() throws Exception {
        mockMvc.perform(create(json("밴드", "\"description\":\"   \"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").isEmpty());
        assertThat(jdbc.queryForObject("SELECT description FROM club", String.class))
                .isNull();
    }

    @Test
    @DisplayName("API 11: issueJoinCode=true면 ^[A-Z2-9]{10}$ 가입 코드를 함께 발급해 저장한다")
    void issueJoinCode() throws Exception {
        String body = mockMvc.perform(create(json("밴드", "\"issueJoinCode\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.joinCode").value(matchesPattern("^[A-Z2-9]{10}$")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String code = com.jayway.jsonpath.JsonPath.read(body, "$.data.joinCode");
        assertThat(jdbc.queryForObject("SELECT join_code FROM club", String.class))
                .isEqualTo(code);
    }

    // ---- 이름 중복 ----

    @Test
    @DisplayName("C-15 API 11: 대소문자·공백만 다른 이름은 409 DUPLICATE / CLUB_NAME_TAKEN이다")
    void duplicateName() throws Exception {
        mockMvc.perform(create(json("Crane Band", ""))).andExpect(status().isCreated());
        mockMvc.perform(create(json("  cRaNe  bAnD ", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_NAME_TAKEN"));
        assertThat(count("club")).isEqualTo(1);
        assertThat(count("part")).isEqualTo(6);
    }

    @Test
    @DisplayName("C-15 API 11: 비활성(INACTIVE) 동아리의 이름도 중복이다")
    void duplicateAgainstInactive() throws Exception {
        jdbc.update(
                "INSERT INTO club (name, club_type, status, is_searchable, created_by)"
                        + " VALUES ('Old Band', 'BAND', 'INACTIVE', false, ?)",
                other);
        mockMvc.perform(create(json("old band", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_NAME_TAKEN"));
        assertThat(count("club")).isEqualTo(1);
    }

    // ---- 사진 ----

    @Test
    @DisplayName("API 11: 본인의 CLUB_PROFILE 임시 키는 clubs/{id}/로 복사해 연결하고 임시 객체를 지운다")
    void profileImageKey() throws Exception {
        String key = tmpKey(me, "club-profile");
        String fileName = key.substring(key.lastIndexOf('/') + 1);

        String body = mockMvc.perform(create(json("밴드", "\"profileImageKey\":\"" + key + "\"")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
        String url = com.jayway.jsonpath.JsonPath.read(body, "$.data.profileImageUrl");

        assertThat(url).isEqualTo(CDN + "/clubs/" + id + "/" + fileName);
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM club", String.class))
                .isEqualTo(url);
        assertThat(storage.exists("clubs/" + id + "/" + fileName)).isTrue();
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    @DisplayName("API 11: USER_PROFILE 키·남의 키·형식 오류·만료 키는 404 IMAGE_KEY_NOT_FOUND이고 아무것도 만들지 않는다")
    void rejectBadKeys() throws Exception {
        String userProfileKey = tmpKey(me, "users");
        String othersKey = tmpKey(other, "club-profile");
        String malformed = "tmp/club-profile/" + me + "/not-a-uuid.png";
        String expired = tmpKey(me, "club-profile");
        storage.get(expired).modifiedAt = Instant.now().minus(61, ChronoUnit.MINUTES);

        for (String bad : new String[] {userProfileKey, othersKey, malformed, expired}) {
            mockMvc.perform(create(json("밴드", "\"profileImageKey\":\"" + bad + "\"")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));
            assertThat(count("club")).isZero();
            assertThat(count("membership")).isZero();
            assertThat(count("part")).isZero();
            assertThat(hasClubObject()).isFalse();
        }
        assertThat(storage.exists(othersKey)).isTrue();
    }

    // ---- 검증 실패 ----

    @Test
    @DisplayName("API 11: 이름 없음·공백·101자, clubType 오류, 소문자 themeColor, 501자 description은 400 VALIDATION_ERROR")
    void validation() throws Exception {
        String[][] cases = {
            {"{\"clubType\":\"BAND\"}", "name"},
            {json("   ", ""), "name"},
            {json("a".repeat(101), ""), "name"},
            {"{\"name\":\"밴드\",\"clubType\":\"ORCHESTRA\"}", "clubType"},
            {"{\"name\":\"밴드\"}", "clubType"},
            {json("밴드", "\"themeColor\":\"#1a2b3c\""), "themeColor"},
            {json("밴드", "\"description\":\"" + "a".repeat(501) + "\""), "description"},
        };
        for (String[] c : cases) {
            mockMvc.perform(create(c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value(c[1]));
        }
        assertThat(count("club")).isZero();
        // 경계값: 100자 이름과 500자 설명은 통과한다
        mockMvc.perform(create(json("a".repeat(100), "\"description\":\"" + "b".repeat(500) + "\"")))
                .andExpect(status().isCreated());
    }

    // ---- 인증 ----

    @Test
    @DisplayName("API 11: 비로그인은 401, X-Requested-With가 없으면 403")
    void authGuards() throws Exception {
        mockMvc.perform(post("/api/v1/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("밴드", ""))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("밴드", ""))
                        .cookie(access(me)))
                .andExpect(status().isForbidden());
        assertThat(count("club")).isZero();
    }
}
