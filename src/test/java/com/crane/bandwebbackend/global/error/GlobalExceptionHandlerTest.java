package com.crane.bandwebbackend.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.global.config.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MDC.put(RequestIdFilter.MDC_KEY, "req-test00000001");
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("0.2 BusinessException은 오류 코드의 HTTP 상태와 코드명, 메시지로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/stub/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("팀을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.error.warnings").doesNotExist())
                .andExpect(jsonPath("$.error.requestId").value("req-test00000001"));
    }

    @Test
    @DisplayName("0.1 확인 필요 규칙은 409 CONFIRMATION_REQUIRED와 경고 목록을 돌려준다")
    void confirmationRequired() throws Exception {
        mockMvc.perform(get("/stub/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.warnings[0].type").value("LAST_ADMIN"))
                .andExpect(jsonPath("$.error.warnings[0].clubId").value("3"))
                .andExpect(jsonPath("$.error.warnings.length()").value(2));
    }

    @Test
    @DisplayName("0.2 요청 본문 검증 실패는 400 VALIDATION_ERROR이고 모든 필드 오류를 details에 담는다")
    void bodyValidation() throws Exception {
        mockMvc.perform(post("/stub/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(2))
                .andExpect(
                        jsonPath("$.error.details[?(@.field=='name')].reason").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.error.details[?(@.field=='age')].reason").value("INVALID_VALUE"));
    }

    @Test
    @DisplayName("0.2 경로·쿼리 파라미터 검증 실패(ConstraintViolationException)는 400 VALIDATION_ERROR")
    void constraintViolation() throws Exception {
        mockMvc.perform(get("/stub/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(
                        jsonPath("$.error.details[?(@.field=='name')].reason").value("INVALID_VALUE"));
    }

    @Test
    @DisplayName("0.2 깨진 JSON·타입 불일치·필수 파라미터 누락은 400 VALIDATION_ERROR")
    void unreadableRequests() throws Exception {
        mockMvc.perform(post("/stub/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/stub/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/stub/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("0.2 낙관적 락 충돌은 409 VERSION_CONFLICT")
    void optimisticLock() throws Exception {
        mockMvc.perform(get("/stub/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @ParameterizedTest(name = "MySQL {0} → {1} {2}")
    @CsvSource({
        "1062, 409, DUPLICATE",
        "3819, 422, RULE_VIOLATION",
        "1451, 409, STATE_CONFLICT",
        "1452, 409, STATE_CONFLICT",
        "0, 409, STATE_CONFLICT"
    })
    @DisplayName("0.2 DB 제약 위반은 DB 메시지(입력값 포함) 없이 규칙 코드로 변환한다")
    void dbConstraintViolation(int sqlErrorCode, int httpStatus, String code) throws Exception {
        mockMvc.perform(get("/stub/db/" + sqlErrorCode))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(content().string(not(containsString("secret@example.com"))));
    }

    @Test
    @DisplayName("0.2 지원하지 않는 Content-Type(415)은 400 VALIDATION_ERROR, reason UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/stub/valid").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.error.details[0].field").doesNotExist());
    }

    @Test
    @DisplayName("0.2 예상하지 못한 오류는 500, code·details 없이 message·requestId만 내리고 예외 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.requestId").value("req-test00000001"))
                .andExpect(content().string(not(containsString("secret@example.com"))));
    }

    @Test
    @DisplayName("0.2 허용되지 않은 메서드(405) 같은 프레임워크 요청 오류는 500으로 바꾸지 않는다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/stub/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").doesNotExist());
    }

    @Test
    @DisplayName("0.2 컨트롤러 파라미터 제약 위반(HandlerMethodValidationException)은 400 VALIDATION_ERROR")
    void handlerMethodValidation() throws Exception {
        mockMvc.perform(get("/stub/min").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("size"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_VALUE"));
    }

    static class Body {
        @NotBlank
        public String name;

        @NotNull
        public Integer age;
    }

    @RestController
    @RequestMapping("/stub")
    class StubController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다.");
        }

        @GetMapping("/confirm")
        void confirm() {
            throw BusinessException.confirmationRequired(
                    "확인이 필요합니다.",
                    List.of(Map.of("type", "LAST_ADMIN", "clubId", "3"), Map.of("type", "TEAM_LEADER_CHANGES")));
        }

        @PostMapping(value = "/valid", consumes = MediaType.APPLICATION_JSON_VALUE)
        void valid(@jakarta.validation.Valid @RequestBody Body body) {}

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new Body()));
        }

        @GetMapping("/number/{id}")
        void number(@PathVariable int id) {}

        @GetMapping("/required")
        void required(@RequestParam String name) {}

        @GetMapping("/min")
        void min(@RequestParam @Min(1) int size) {}

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret@example.com");
        }

        @GetMapping("/lock")
        void lock() {
            throw new OptimisticLockingFailureException("version");
        }

        @GetMapping("/db/{sqlErrorCode}")
        void db(@PathVariable int sqlErrorCode) {
            throw new DataIntegrityViolationException(
                    "Duplicate entry 'secret@example.com' for key 'uk_x'",
                    new SQLException("Duplicate entry 'secret@example.com'", "23000", sqlErrorCode));
        }
    }
}
