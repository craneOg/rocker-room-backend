package com.crane.bandwebbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileApiTest {

    private static final String REQUEST_ID = "req-[a-z0-9]{12}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private long insertUser(String name, String handle, String imageUrl, boolean imagePublic) {
        return new SimpleJdbcInsert(jdbc)
                .withTableName("users")
                .usingColumns("name", "handle", "profile_image_url", "profile_image_public", "status")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(java.util.Map.of(
                        "name",
                        name,
                        "profile_image_public",
                        imagePublic,
                        "status",
                        "ACTIVE",
                        "handle",
                        handle == null ? "" : handle,
                        "profile_image_url",
                        imageUrl == null ? "" : imageUrl))
                .longValue();
    }

    // 값이 없는 컬럼은 NULL이어야 해서(빈 문자열은 handle CHECK 위반) 삽입 뒤에 NULL로 맞춘다
    private long user(String name, String handle, String imageUrl, boolean imagePublic) {
        long id = insertUser(
                name, handle == null ? "tmp_" + System.nanoTime() % 100000000 : handle, imageUrl, imagePublic);
        if (handle == null) {
            jdbc.update("UPDATE users SET handle = NULL WHERE id = ?", id);
        }
        if (imageUrl == null) {
            jdbc.update("UPDATE users SET profile_image_url = NULL WHERE id = ?", id);
        }
        return id;
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder patchMe(long userId, String body) {
        return patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(userId));
    }

    private String column(long id, String col) {
        return jdbc.queryForObject("SELECT " + col + " FROM users WHERE id = ?", String.class, id);
    }

    // ---- API 7 ----

    @Test
    @DisplayName("API 7: 본인 프로필(비공개 사진 포함)과 onboardingRequired를 돌려준다")
    void getMe() throws Exception {
        long id = user("정규채", "gyuchae", "https://cdn.example/7.png", false);

        mockMvc.perform(get("/api/v1/me").cookie(access(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.name").value("정규채"))
                .andExpect(jsonPath("$.data.handle").value("gyuchae"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example/7.png"))
                .andExpect(jsonPath("$.data.profileImagePublic").value(false))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(false))
                .andExpect(jsonPath("$.data.superAdmin").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern(REQUEST_ID)));
    }

    @Test
    @DisplayName("API 7: handle이 없으면 handle은 null이고 onboardingRequired는 true")
    void getMeWithoutHandle() throws Exception {
        long id = user("신규", null, null, true);

        mockMvc.perform(get("/api/v1/me").cookie(access(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").isEmpty())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.profileImagePublic").value(true))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true));
    }

    @Test
    @DisplayName("API 7·8·128: 인증이 없으면 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/handle-availability").param("handle", "abcde"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    // ---- API 8 ----

    @Test
    @DisplayName("API 8: 이름·handle·사진 공개 여부를 수정하고 onboardingRequired 없이 사용자 정보를 돌려준다")
    void update() throws Exception {
        long id = user("이전이름", null, null, true);

        mockMvc.perform(patchMe(id, "{\"name\":\"  변경된 이름  \",\"handle\":\"gyuchae\",\"profileImagePublic\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("변경된 이름"))
                .andExpect(jsonPath("$.data.handle").value("gyuchae"))
                .andExpect(jsonPath("$.data.profileImagePublic").value(false))
                .andExpect(jsonPath("$.data.onboardingRequired").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").exists());

        assertThat(column(id, "name")).isEqualTo("변경된 이름");
        assertThat(column(id, "handle")).isEqualTo("gyuchae");
        assertThat(column(id, "profile_image_public")).isIn("0", "false");
    }

    @Test
    @DisplayName("API 8: 보내지 않은 필드는 바꾸지 않는다(빈 객체도 200)")
    void partialUpdate() throws Exception {
        long id = user("그대로", "keep_me", "https://cdn.example/a.png", true);

        mockMvc.perform(patchMe(id, "{}")).andExpect(status().isOk());
        mockMvc.perform(patchMe(id, "{\"profileImagePublic\":false}")).andExpect(status().isOk());

        assertThat(column(id, "name")).isEqualTo("그대로");
        assertThat(column(id, "handle")).isEqualTo("keep_me");
        assertThat(column(id, "profile_image_url")).isEqualTo("https://cdn.example/a.png");
    }

    @Test
    @DisplayName("API 8: 형식이 틀린 값은 400 VALIDATION_ERROR이고 아무것도 저장하지 않는다")
    void invalidValues() throws Exception {
        long id = user("원래", "valid_one", null, true);

        for (String body : new String[] {
            "{\"name\":\"\"}",
            "{\"name\":\"" + "가".repeat(51) + "\"}",
            "{\"handle\":\"ABCDE\"}",
            "{\"handle\":\"abcd\"}",
            "{\"handle\":\"" + "a".repeat(31) + "\"}",
            "{\"handle\":\"has-dash\"}",
            "{\"handle\":\"한글핸들입니다\"}"
        }) {
            mockMvc.perform(patchMe(id, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_VALUE"));
        }
        mockMvc.perform(patchMe(id, "{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
        assertThat(column(id, "name")).isEqualTo("원래");
        assertThat(column(id, "handle")).isEqualTo("valid_one");
    }

    @Test
    @DisplayName("API 8: 다른 사용자가 쓰는 handle은 409 DUPLICATE(HANDLE_TAKEN), 본인 handle을 그대로 보내면 성공")
    void handleTaken() throws Exception {
        long other = user("다른 사람", "taken_one", null, true);
        long me = user("나", "my_handle", null, true);

        mockMvc.perform(patchMe(me, "{\"handle\":\"taken_one\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("HANDLE_TAKEN"));
        mockMvc.perform(patchMe(me, "{\"name\":\"그대로 둔 handle\",\"handle\":\"my_handle\"}"))
                .andExpect(status().isOk());

        assertThat(column(me, "handle")).isEqualTo("my_handle");
        assertThat(column(other, "handle")).isEqualTo("taken_one");
    }

    @Test
    @DisplayName("API 8: 같은 handle을 동시에 요청하면 한 사람만 성공하고 나머지는 409 HANDLE_TAKEN")
    void concurrentHandle() throws Exception {
        int users = 4;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            ids.add(user("동시" + i, null, null, true));
        }
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (long id : ids) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(patchMe(id, "{\"handle\":\"race_handle\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 200) {
                ok++;
            } else if (code == 409) {
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(users - 1);
    }

    @Test
    @DisplayName("API 8: 사진은 null을 보내면 삭제되고, 키를 보내면 404 IMAGE_KEY_NOT_FOUND(업로드 API 전까지)")
    void profileImage() throws Exception {
        long id = user("사진", "photo_user", "https://cdn.example/old.png", true);

        mockMvc.perform(patchMe(id, "{\"profileImageUrl\":\"tmp/USER_PROFILE/1/abc\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));
        assertThat(column(id, "profile_image_url")).isEqualTo("https://cdn.example/old.png");

        mockMvc.perform(patchMe(id, "{\"profileImageUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
        assertThat(column(id, "profile_image_url")).isNull();
    }

    @Test
    @DisplayName("API 8: X-Requested-With 없음 403, JSON이 아닌 Content-Type 400")
    void requestGuards() throws Exception {
        long id = user("가드", "guard_user", null, true);

        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(access(id)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(access(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- API 128 ----

    @Test
    @DisplayName("API 128: 쓰는 사람이 없으면 true, 다른 사람이 쓰면 false, 본인 handle은 true")
    void handleAvailability() throws Exception {
        user("다른 사람", "taken_one", null, true);
        long me = user("나", "my_handle", null, true);

        mockMvc.perform(get("/api/v1/users/handle-availability")
                        .param("handle", "free_handle")
                        .cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.meta.requestId").exists());
        mockMvc.perform(get("/api/v1/users/handle-availability")
                        .param("handle", "taken_one")
                        .cookie(access(me)))
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/api/v1/users/handle-availability")
                        .param("handle", "my_handle")
                        .cookie(access(me)))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("API 128: 형식이 틀리거나 handle이 없으면 400 VALIDATION_ERROR")
    void handleAvailabilityValidation() throws Exception {
        long me = user("나", "my_handle", null, true);

        for (String bad : new String[] {"ABCDE", "abcd", "a".repeat(31), "has space", "한글핸들입니다"}) {
            mockMvc.perform(get("/api/v1/users/handle-availability")
                            .param("handle", bad)
                            .cookie(access(me)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/v1/users/handle-availability").cookie(access(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
