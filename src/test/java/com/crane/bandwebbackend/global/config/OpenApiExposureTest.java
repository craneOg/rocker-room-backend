package com.crane.bandwebbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiExposureTest {

    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("test")
    @SpringBootTest
    @AutoConfigureMockMvc
    @Nested
    class Default {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        Environment environment;

        @Test
        @DisplayName("U7 기본 설정에서 springdoc은 명시적으로 꺼져 있다(springdoc 기본값은 켜짐)")
        void apiDocsConfigurationIsDisabled() {
            assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .isFalse();
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                    .isFalse();
        }

        @Test
        @DisplayName("U7 기본 설정(운영과 동일)에서 /v3/api-docs는 인증 없이 열리지 않는다")
        void apiDocsHidden() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("U7 기본 설정(운영과 동일)에서 /swagger-ui/index.html은 인증 없이 열리지 않는다")
        void swaggerUiHidden() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        }
    }

    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles({"test", "local"})
    @SpringBootTest
    @AutoConfigureMockMvc
    @Nested
    class Local {

        @Autowired
        MockMvc mockMvc;

        @Test
        @DisplayName("U7 local 프로필에서는 /v3/api-docs가 200이고 쿠키 보안 스킴을 포함한다")
        void apiDocsExposed() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("rocker-room API"))
                    .andExpect(jsonPath("$.components.securitySchemes.accessCookie.in")
                            .value("cookie"))
                    .andExpect(jsonPath("$.components.securitySchemes.accessCookie.name")
                            .value("rr_at"));
        }

        @Test
        @DisplayName("U7 local 프로필에서는 /swagger-ui/index.html이 인증 없이 200이다")
        void swaggerUiExposed() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}
