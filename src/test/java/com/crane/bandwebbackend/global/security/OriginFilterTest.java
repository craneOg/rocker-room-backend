package com.crane.bandwebbackend.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OriginFilterTest {

    private static final String ALLOWED = "http://localhost:5173";

    @Autowired
    MockMvc mockMvc;

    private static MockHttpServletRequestBuilder exchange() {
        return post("/api/v1/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Requested-With", "XMLHttpRequest");
    }

    @Test
    @DisplayName("Q2 허용 출처가 아니면 exchange·refresh는 403 FORBIDDEN(ORIGIN_NOT_ALLOWED)")
    void rejectsOtherOrigin() throws Exception {
        for (String path : new String[] {"/api/v1/auth/exchange", "/api/v1/auth/refresh"}) {
            mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .header("Origin", "https://evil.example"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error.details[0].field").value("Origin"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("ORIGIN_NOT_ALLOWED"));
        }
    }

    @Test
    @DisplayName("Q2 허용 출처이거나 Origin 헤더가 없으면 필터를 통과한다")
    void allowsSameOriginAndNoOrigin() throws Exception {
        mockMvc.perform(exchange().header("Origin", ALLOWED)).andExpect(status().isUnauthorized());
        mockMvc.perform(exchange()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Q2 Origin 값이 문자열 null이어도 거부한다")
    void rejectsNullOrigin() throws Exception {
        mockMvc.perform(exchange().header("Origin", "null")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Q2 GET 요청은 Origin 필터의 대상이 아니다")
    void getRequestsUnaffected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("error", "access_denied")
                        .header("Origin", ALLOWED))
                .andExpect(status().isFound());
    }
}
