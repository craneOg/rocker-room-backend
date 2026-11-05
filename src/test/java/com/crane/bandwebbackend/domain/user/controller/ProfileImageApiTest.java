package com.crane.bandwebbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.error.GlobalExceptionHandler;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.storage.InMemoryImageStorage;
import com.crane.bandwebbackend.global.storage.InMemoryStorageConfig;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@Import({TestcontainersConfiguration.class, InMemoryStorageConfig.class})
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ProfileImageApiTest {

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

    @Autowired
    GlobalExceptionHandler exceptionHandler;

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
        jdbc.update("DELETE FROM access_token_blacklist");
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

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    // RIFF/WEBP 무손실(VP8L) 최소 헤더. 가로·세로만 의미가 있다
    private static byte[] webp(int w, int h) {
        byte[] b = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        b[4] = (byte) (b.length - 8);
        System.arraycopy("WEBPVP8L".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 8);
        b[16] = 10;
        b[20] = 0x2F;
        int bits = (w - 1) | ((h - 1) << 14);
        for (int i = 0; i < 4; i++) {
            b[21 + i] = (byte) (bits >> (8 * i));
        }
        return b;
    }

    private MockMultipartHttpServletRequestBuilder upload(
            long userId, byte[] bytes, String contentType, String purpose) {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/me/profile-image");
        if (bytes != null) {
            req.file(new MockMultipartFile("file", "photo", contentType, bytes));
        }
        if (purpose != null) {
            req.param("purpose", purpose);
        }
        return (MockMultipartHttpServletRequestBuilder)
                req.header("X-Requested-With", "XMLHttpRequest").cookie(access(userId));
    }

    private MockHttpServletRequestBuilder patchMe(long userId, String body) {
        return patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(userId));
    }

    private String uploadKey(long userId, String purpose) throws Exception {
        String json = mockMvc.perform(upload(userId, image("png", 8, 8), "image/png", purpose))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.read(json, "$.data.imageKey");
    }

    private String column(long id, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM users WHERE id = ?", String.class, id);
    }

    // ---- API 129 ----

    @Test
    @DisplayName("API 129: JPG를 올리면 본인 임시 위치(tmp/users/{id}/)에 두고 키·미리보기 주소·만료 시각을 돌려준다")
    void uploadJpeg() throws Exception {
        mockMvc.perform(upload(me, image("jpg", 16, 16), "image/jpeg", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purpose").value("USER_PROFILE"))
                .andExpect(
                        jsonPath("$.data.imageKey").value(matchesPattern("tmp/users/" + me + "/[0-9a-f-]{36}\\.jpg")))
                .andExpect(jsonPath("$.data.previewUrl").value(startsWith(CDN + "/tmp/users/" + me + "/")))
                .andExpect(jsonPath("$.data.expiresAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern("req-[a-z0-9]{12}")));
        assertThat(storage.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("API 129: purpose=CLUB_PROFILE는 tmp/club-profile/{id}/ 접두사, 범위 밖 purpose는 400 INVALID_VALUE")
    void purposePrefix() throws Exception {
        mockMvc.perform(upload(me, image("png", 8, 8), "image/png", "CLUB_PROFILE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purpose").value("CLUB_PROFILE"))
                .andExpect(jsonPath("$.data.imageKey").value(startsWith("tmp/club-profile/" + me + "/")));
        mockMvc.perform(upload(me, image("png", 8, 8), "image/png", "BANNER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("purpose"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_VALUE"));
    }

    @Test
    @DisplayName(
            "API 129: PNG·WEBP도 허용하고, 검사에 실패하면 400(FILE_TOO_LARGE·UNSUPPORTED_MEDIA_TYPE·UNSUPPORTED_FORMAT·INVALID_IMAGE)")
    void validation() throws Exception {
        mockMvc.perform(upload(me, image("png", 8, 8), "image/png", null)).andExpect(status().isCreated());
        mockMvc.perform(upload(me, webp(100, 200), "image/webp", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageKey").value(endsWith(".webp")));
        int stored = storage.size();

        // 2MiB 초과
        mockMvc.perform(upload(me, new byte[2 * 1024 * 1024 + 1], "image/png", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("FILE_TOO_LARGE"));
        // 허용하지 않는 Content-Type
        mockMvc.perform(upload(me, image("png", 8, 8), "image/gif", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
        // 선언은 PNG인데 실제 내용이 아닌 경우, 그리고 JPEG 내용을 PNG로 선언한 경우
        mockMvc.perform(upload(me, "hello".getBytes(StandardCharsets.UTF_8), "image/png", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_FORMAT"));
        mockMvc.perform(upload(me, image("jpg", 8, 8), "image/png", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_FORMAT"));
        // 시그니처만 맞고 디코딩되지 않는 PNG, 빈 파일, 상한을 넘는 WebP
        byte[] truncated = image("png", 64, 64);
        mockMvc.perform(upload(me, java.util.Arrays.copyOf(truncated, 20), "image/png", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_IMAGE"));
        mockMvc.perform(upload(me, new byte[0], "image/png", null))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_IMAGE"));
        mockMvc.perform(upload(me, webp(1, 1), "image/webp", null)).andExpect(status().isCreated());
        mockMvc.perform(upload(me, webp(10_001, 10), "image/webp", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_IMAGE"));
        assertThat(storage.size()).isEqualTo(stored + 1); // 실패한 요청은 저장하지 않는다
    }

    @Test
    @DisplayName("API 129: 파일이 없으면 400, multipart가 아니면 400 UNSUPPORTED_MEDIA_TYPE, X-Requested-With 없으면 403, 비로그인 401")
    void requestGuards() throws Exception {
        mockMvc.perform(upload(me, null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
        mockMvc.perform(post("/api/v1/me/profile-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(access(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(multipart("/api/v1/me/profile-image")
                        .file(new MockMultipartFile("file", "p", "image/png", image("png", 8, 8)))
                        .cookie(access(me)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/me/profile-image")
                        .file(new MockMultipartFile("file", "p", "image/png", image("png", 8, 8)))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        assertThat(storage.size()).isZero();
    }

    // ---- API 8: 키로 연결 ----

    @Test
    @DisplayName("API 8: 임시 키를 보내면 영구 위치(users/{id}/)로 옮겨 연결하고 임시 객체를 지운다")
    void linkKey() throws Exception {
        String key = uploadKey(me, null);
        String fileName = key.substring(key.lastIndexOf('/') + 1);

        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(CDN + "/users/" + me + "/" + fileName));
        assertThat(column(me, "profile_image_url")).isEqualTo(CDN + "/users/" + me + "/" + fileName);
        assertThat(storage.exists(key)).isFalse();
        assertThat(storage.exists("users/" + me + "/" + fileName)).isTrue();
        assertThat(storage.get("users/" + me + "/" + fileName).contentType).isEqualTo("image/png");
    }

    @Test
    @DisplayName("API 8: 사진을 바꾸거나 삭제하면 이전 영구 객체를 커밋 뒤에 지우고, 외부(카카오) 주소는 건드리지 않는다")
    void replaceAndClearDeletePrevious() throws Exception {
        jdbc.update("UPDATE users SET profile_image_url = 'https://k.kakaocdn.net/p.jpg' WHERE id = ?", me);
        String first = uploadKey(me, null);
        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + first + "\"}")).andExpect(status().isOk());
        String firstPermanent = "users/" + me + "/" + first.substring(first.lastIndexOf('/') + 1);
        assertThat(storage.exists(firstPermanent)).isTrue();

        String second = uploadKey(me, null);
        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + second + "\"}")).andExpect(status().isOk());
        String secondPermanent = "users/" + me + "/" + second.substring(second.lastIndexOf('/') + 1);
        assertThat(storage.exists(firstPermanent)).isFalse();
        assertThat(storage.exists(secondPermanent)).isTrue();

        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
        assertThat(storage.exists(secondPermanent)).isFalse();
        assertThat(column(me, "profile_image_url")).isNull();
    }

    @Test
    @DisplayName("API 8: 남의 키·다른 용도 키·없는 키·형식이 다른 키는 모두 404 IMAGE_KEY_NOT_FOUND이고 프로필은 그대로다")
    void rejectForeignKeys() throws Exception {
        String othersKey = uploadKey(other, null);
        String clubKey = uploadKey(me, "CLUB_PROFILE");
        String mine = uploadKey(me, null);
        String fileName = mine.substring(mine.lastIndexOf('/') + 1);
        for (String bad : new String[] {
            othersKey,
            clubKey,
            "tmp/users/" + me + "/00000000-0000-0000-0000-000000000000.png",
            "tmp/users/" + me + "/../" + other + "/" + fileName,
            "users/" + me + "/" + fileName,
            "https://evil.example/x.png"
        }) {
            mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));
        }
        assertThat(column(me, "profile_image_url")).isNull();
        assertThat(storage.exists(othersKey)).isTrue();
    }

    @Test
    @DisplayName("API 8: 임시 키는 만료(1시간) 뒤에는 연결할 수 없다")
    void expiredKey() throws Exception {
        String key = uploadKey(me, null);
        storage.get(key).modifiedAt = Instant.now().minus(61, ChronoUnit.MINUTES);
        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + key + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));

        String fresh = uploadKey(me, null);
        storage.get(fresh).modifiedAt = Instant.now().minus(59, ChronoUnit.MINUTES);
        mockMvc.perform(patchMe(me, "{\"profileImageUrl\":\"" + fresh + "\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("서버 업로드 한도(2MiB)를 넘어 컨트롤러 전에 발생한 예외도 400 FILE_TOO_LARGE로 변환한다")
    void serverLimitExceeded() {
        var response = exceptionHandler.handle(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(2L * 1024 * 1024));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getError().getDetails().get(0).getReason())
                .isEqualTo("FILE_TOO_LARGE");
        assertThat(response.getBody().getError().getDetails().get(0).getField()).isEqualTo("file");
    }
}
