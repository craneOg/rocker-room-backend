package com.crane.bandwebbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestIdFilterTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    @DisplayName("0.2 요청 ID는 req- + 영숫자 소문자 12자이고 MDC와 X-Request-Id 헤더가 같다")
    void generatesRequestId() throws Exception {
        var result =
                mockMvc.perform(get("/stub/mdc")).andExpect(status().isOk()).andReturn();

        String header = result.getResponse().getHeader("X-Request-Id");
        assertThat(header).matches("req-[a-z0-9]{12}");
        assertThat(result.getResponse().getContentAsString()).isEqualTo(header);
    }

    @Test
    @DisplayName("0.2 클라이언트가 보낸 X-Request-Id는 받지 않는다(U4)")
    void ignoresClientValue() throws Exception {
        var result = mockMvc.perform(get("/stub/mdc").header("X-Request-Id", "req-evil"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotEqualTo("req-evil");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("evil");
    }

    @Test
    @DisplayName("0.2 요청이 끝나면 MDC를 정리하고 요청마다 다른 ID를 만든다")
    void clearsMdc() throws Exception {
        String first =
                mockMvc.perform(get("/stub/mdc")).andReturn().getResponse().getHeader("X-Request-Id");
        String second =
                mockMvc.perform(get("/stub/mdc")).andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(first).isNotEqualTo(second);
    }

    @RestController
    static class StubController {

        @GetMapping("/stub/mdc")
        String mdc() {
            return MDC.get(RequestIdFilter.MDC_KEY);
        }
    }
}
