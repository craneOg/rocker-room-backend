package com.crane.bandwebbackend.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("0.2 BusinessException은 오류 코드의 HTTP 상태와 코드명, 메시지로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/stub/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("팀을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.warnings").doesNotExist());
    }

    @Test
    @DisplayName("0.1 확인 필요 규칙은 409 CONFIRMATION_REQUIRED와 경고 목록을 돌려준다")
    void confirmationRequired() throws Exception {
        mockMvc.perform(get("/stub/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.warnings[0].message").value("마지막 임원이라 후임에게 권한이 넘어갑니다."))
                .andExpect(jsonPath("$.error.warnings.length()").value(2));
    }

    @Test
    @DisplayName("0.2 요청 본문 검증 실패는 400 VALIDATION_ERROR이고 첫 필드 오류를 알려준다")
    void bodyValidation() throws Exception {
        mockMvc.perform(post("/stub/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("name")));
    }

    @Test
    @DisplayName("0.2 경로·쿼리 파라미터 검증 실패(ConstraintViolationException)는 400 VALIDATION_ERROR")
    void constraintViolation() throws Exception {
        mockMvc.perform(get("/stub/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("name")));
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

    static class Body {
        @NotBlank
        public String name;
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
                    "확인이 필요합니다.", List.of("마지막 임원이라 후임에게 권한이 넘어갑니다.", "진행 중 공연의 팀장이 바뀝니다."));
        }

        @PostMapping("/valid")
        void valid(@jakarta.validation.Valid @RequestBody Body body) {}

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new Body()));
        }

        @GetMapping("/number/{id}")
        void number(@PathVariable int id) {}

        @GetMapping("/required")
        void required(@RequestParam String name) {}

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
