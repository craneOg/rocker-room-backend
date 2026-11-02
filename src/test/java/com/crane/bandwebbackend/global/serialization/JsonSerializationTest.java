package com.crane.bandwebbackend.global.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.global.config.JacksonConfig;
import com.crane.bandwebbackend.global.config.RequestIdFilter;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@JsonTest
@Import(JacksonConfig.class)
class JsonSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Getter
    static class Dto {
        @IdString
        private final Long id = 21L;

        @IdString
        private final List<Long> memberIds = List.of(1L, 22L);

        @IdString
        private final Long nothing = null;

        private final int count = 3;
        private final OffsetDateTime createdAt = KstTime.toKst(LocalDateTime.of(2026, 11, 2, 15, 30));
    }

    @Test
    @DisplayName("0.2 성공 응답은 data와 meta.requestId 봉투로 내린다")
    void apiResponse() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-abc123def456");

        JsonNode json = objectMapper.valueToTree(ApiResponse.ok(new Dto()));

        assertThat(json.get("meta").get("requestId").asText()).isEqualTo("req-abc123def456");
        assertThat(json.get("data").get("count").isNumber()).isTrue();
    }

    @Test
    @DisplayName("0.2 목록 응답은 items·page·size·totalElements·totalPages를 숫자로 내린다")
    void pageResponse() throws Exception {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        JsonNode json = objectMapper.valueToTree(ApiResponse.ok(PageResponse.from(page)));

        JsonNode data = json.get("data");
        assertThat(data.get("items")).hasSize(2);
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("size").asInt()).isEqualTo(2);
        assertThat(data.get("totalElements").isNumber()).isTrue();
        assertThat(data.get("totalElements").asLong()).isEqualTo(5);
        assertThat(data.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("0.2 @IdString은 Long과 List<Long>을 문자열로 내리고 null은 null, 다른 숫자는 그대로 둔다(U2)")
    void idString() throws Exception {
        JsonNode json = objectMapper.valueToTree(new Dto());

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").asText()).isEqualTo("21");
        assertThat(json.get("memberIds").get(0).isTextual()).isTrue();
        assertThat(json.get("memberIds").get(1).asText()).isEqualTo("22");
        assertThat(json.get("nothing").isNull()).isTrue();
        assertThat(json.get("count").isInt()).isTrue();
    }

    @Test
    @DisplayName("0.2 일시는 UTC로 바꾸지 않고 ISO-8601 +09:00 문자열로 내린다")
    void offsetDateTime() throws Exception {
        JsonNode json = objectMapper.valueToTree(new Dto());

        assertThat(json.get("createdAt").asText()).isEqualTo("2026-11-03T00:30:00+09:00");
    }

    @Test
    @DisplayName("0.2 알 수 없는 요청 필드는 무시한다(Boot 기본 유지)")
    void ignoresUnknownProperties() throws Exception {
        Req req = objectMapper.readValue("{\"name\":\"x\",\"extra\":1}", Req.class);

        assertThat(req.name).isEqualTo("x");
    }

    @Test
    @DisplayName("0.2 KstTime은 UTC 저장값을 +09:00으로, 다시 UTC로 변환한다(서머타임 없음)")
    void kstTime() {
        LocalDateTime utc = LocalDateTime.of(2026, 7, 1, 14, 59);

        OffsetDateTime kst = KstTime.toKst(utc);

        assertThat(kst.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(kst.toString()).isEqualTo("2026-07-01T23:59+09:00");
        assertThat(KstTime.toUtc(kst)).isEqualTo(utc);
        assertThat(KstTime.toKst(LocalDateTime.of(2026, 1, 1, 0, 0)).getOffset())
                .isEqualTo(kst.getOffset());
        assertThat(KstTime.toUtc(OffsetDateTime.parse("2026-11-02T09:00:00+09:00")))
                .isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        assertThat(KstTime.toKstDate(LocalDateTime.of(2026, 11, 2, 15, 0))).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(KstTime.toKst(null)).isNull();
    }

    static class Req {
        public String name;
    }
}
